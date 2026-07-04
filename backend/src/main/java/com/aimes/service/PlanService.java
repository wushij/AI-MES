package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.common.OperationLog;
import com.aimes.common.OperationLogRunner;
import com.aimes.dto.Requests.PlanSaveRequest;
import com.aimes.config.AimesProperties;
import com.aimes.entity.ProdPlan;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.entity.SysUser;
import com.aimes.mapper.ProdPlanMapper;
import com.aimes.mapper.ProdWorkOrderMapper;
import com.aimes.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.aimes.entity.ProdTeam;
import com.aimes.mapper.ProdTeamMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final ProdPlanMapper prodPlanMapper;
    private final ProdWorkOrderMapper prodWorkOrderMapper;
    private final ProdTeamMapper prodTeamMapper;
    private final SysUserMapper sysUserMapper;
    private final AuthService authService;
    private final SysNotificationService sysNotificationService;
    private final WorkOrderNoService workOrderNoService;
    private final ReferentialIntegrityService referentialIntegrityService;
    private final ProcessRouteService processRouteService;
    private final OperationLogRunner operationLogRunner;
    private final ProductService productService;
    private final AimesProperties aimesProperties;

    public Map<String, Object> list(long current, long size, String keyword, String status) {
        LambdaQueryWrapper<ProdPlan> wrapper = new LambdaQueryWrapper<ProdPlan>()
                .and(StringUtils.hasText(keyword), w -> w.like(ProdPlan::getPlanNo, keyword)
                        .or()
                        .like(ProdPlan::getProductName, keyword))
                .eq(StringUtils.hasText(status), ProdPlan::getStatus, status)
                .orderByDesc(ProdPlan::getPlanDate)
                .orderByDesc(ProdPlan::getId);
        Page<ProdPlan> page = prodPlanMapper.selectPage(new Page<>(current, size), wrapper);

        List<Map<String, Object>> records = page.getRecords().stream().map(this::toView).toList();
        return pageResult(page.getTotal(), records);
    }

    public Map<String, Object> detail(Long id) {
        ProdPlan plan = getPlan(id);
        Map<String, Object> view = toView(plan);
        List<ProdWorkOrder> orders = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                .eq(ProdWorkOrder::getPlanId, id)
                .orderByDesc(ProdWorkOrder::getId));
        view.put("workOrders", orders.stream().map(this::toWorkOrderSummary).toList());
        view.put("completionProgress", computeCompletionProgress(orders));
        view.put("executionStatus", resolveExecutionStatus(plan, orders));
        if (plan.getProductId() != null) {
            view.put("product", productService.getProductSummary(plan.getProductId()));
            view.put("hasBom", productService.hasActiveBom(plan.getProductId()));
        }
        return view;
    }

    @Transactional
    public Map<String, Object> create(PlanSaveRequest request) {
        ProdPlan plan = new ProdPlan();
        plan.setPlanNo(StringUtils.hasText(request.getPlanNo()) ? request.getPlanNo() : nextPlanNo());
        applyProduct(plan, request.getProductId(), request.getProductName());
        plan.setPlanQty(request.getPlanQty());
        plan.setPlanDate(request.getPlanDate());
        plan.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "draft");
        plan.setCreatedBy(authService.currentUser().getId());
        plan.setRemark(request.getRemark());
        prodPlanMapper.insert(plan);
        return toView(plan);
    }

    @Transactional
    public Map<String, Object> update(Long id, PlanSaveRequest request) {
        ProdPlan plan = getPlan(id);
        if ("completed".equals(plan.getStatus())) {
            throw new BusinessException("已完成计划不允许修改");
        }
        applyProduct(plan, request.getProductId(), request.getProductName());
        plan.setPlanQty(request.getPlanQty());
        plan.setPlanDate(request.getPlanDate());
        if (StringUtils.hasText(request.getStatus())) {
            plan.setStatus(request.getStatus());
        }
        plan.setRemark(request.getRemark());
        prodPlanMapper.updateById(plan);
        return toView(plan);
    }

    @Transactional
    @OperationLog(module = "生产计划", action = "删除")
    public void delete(Long id) {
        operationLogRunner.runVoid("生产计划", "删除", "delete", new Object[]{id}, () -> deleteInternal(id));
    }

    private void deleteInternal(Long id) {
        referentialIntegrityService.ensurePlanDeletable(id);
        prodPlanMapper.deleteById(id);
    }

    @Transactional
    @OperationLog(module = "生产计划", action = "下发")
    public Map<String, Object> release(Long id) {
        return operationLogRunner.runUnchecked("生产计划", "下发", "release", new Object[]{id}, () -> releaseInternal(id));
    }

    public Map<String, Object> previewRelease(Long id) {
        ProdPlan plan = getPlan(id);
        if (!"draft".equals(plan.getStatus())) {
            throw new BusinessException("只有草稿计划可以预览下发");
        }
        List<Map<String, Object>> workOrders = buildSplitPreview(plan);
        boolean hasBom = plan.getProductId() != null && productService.hasActiveBom(plan.getProductId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plan", toView(plan));
        result.put("splitQty", aimesProperties.getPlanSplitQty());
        result.put("workOrderCount", workOrders.size());
        result.put("workOrders", workOrders);
        result.put("hasBom", hasBom);
        if (!hasBom) {
            result.put("bomWarning", "产品未配置 BOM，下发后无法预览理论用料");
        }
        return result;
    }

    private Map<String, Object> releaseInternal(Long id) {
        ProdPlan plan = getPlan(id);
        if (!"draft".equals(plan.getStatus())) {
            throw new BusinessException("只有草稿计划可以下发");
        }

        plan.setStatus("released");
        plan.setReleaseTime(LocalDateTime.now());
        prodPlanMapper.updateById(plan);

        List<SysUser> usersToNotify = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getRole, java.util.List.of("admin", "supervisor")));
        for (SysUser recipient : usersToNotify) {
            sysNotificationService.createNotification(
                    recipient.getId(),
                    "计划发布",
                    "生产计划 “" + plan.getPlanNo() + "” 状态已更新为已下发。",
                    "info",
                    "/plans"
            );
        }

        List<Integer> splitQtys = computeSplitQuantities(plan.getPlanQty());
        List<ProdWorkOrder> generated = new ArrayList<>();
        int index = 1;
        for (Integer qty : splitQtys) {
            ProdWorkOrder order = new ProdWorkOrder();
            order.setOrderNo(workOrderNoService.nextOrderNo());
            order.setPlanId(plan.getId());
            order.setProductId(plan.getProductId());
            order.setProductName(plan.getProductName());
            order.setOrderQty(qty);
            processRouteService.applyRoutingToWorkOrder(order, plan.getProductId(), plan.getProductName());
            order.setProgress(0);
            order.setStatus("pending");
            order.setPriority(2);
            order.setDeadline(plan.getPlanDate().atTime(18, 0));
            String splitHint = splitQtys.size() > 1 ? "（批次 " + index + "/" + splitQtys.size() + "，数量 " + qty + "）" : "";
            order.setRemark("由计划 " + plan.getPlanNo() + " 自动生成" + splitHint);
            prodWorkOrderMapper.insert(order);
            processRouteService.initProcessRecords(order.getId(), plan.getProductId(), plan.getProductName());
            generated.add(order);
            index++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plan", toView(plan));
        result.put("generatedWorkOrders", generated.stream().map(this::toWorkOrderSummary).toList());
        result.put("generatedWorkOrder", generated.isEmpty() ? null : generated.get(0));
        result.put("hasBom", plan.getProductId() != null && productService.hasActiveBom(plan.getProductId()));
        return result;
    }

    private List<Map<String, Object>> buildSplitPreview(ProdPlan plan) {
        List<Integer> splitQtys = computeSplitQuantities(plan.getPlanQty());
        List<Map<String, Object>> rows = new ArrayList<>();
        int index = 1;
        for (Integer qty : splitQtys) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("batchNo", index);
            row.put("quantity", qty);
            row.put("productName", plan.getProductName());
            row.put("deadline", plan.getPlanDate().atTime(18, 0));
            rows.add(row);
            index++;
        }
        return rows;
    }

    private List<Integer> computeSplitQuantities(int planQty) {
        int splitSize = Math.max(1, aimesProperties.getPlanSplitQty());
        List<Integer> quantities = new ArrayList<>();
        int remaining = planQty;
        while (remaining > 0) {
            int batch = Math.min(remaining, splitSize);
            quantities.add(batch);
            remaining -= batch;
        }
        return quantities;
    }

    private void applyProduct(ProdPlan plan, Long productId, String productName) {
        if (productId != null) {
            var summary = productService.getProductSummary(productId);
            plan.setProductId(productId);
            plan.setProductName((String) summary.get("productName"));
            return;
        }
        plan.setProductName(productName);
        var product = productService.findByName(productName);
        if (product != null) {
            plan.setProductId(product.getId());
        } else {
            plan.setProductId(null);
        }
    }

    private ProdPlan getPlan(Long id) {
        ProdPlan plan = prodPlanMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("生产计划不存在");
        }
        return plan;
    }

    private Map<String, Object> toView(ProdPlan plan) {
        SysUser creator = plan.getCreatedBy() == null ? null : sysUserMapper.selectById(plan.getCreatedBy());
        long workOrderCount = prodWorkOrderMapper.selectCount(new LambdaQueryWrapper<ProdWorkOrder>()
                .eq(ProdWorkOrder::getPlanId, plan.getId()));
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", plan.getId());
        view.put("planNo", plan.getPlanNo());
        view.put("productId", plan.getProductId());
        view.put("productName", plan.getProductName());
        view.put("planQty", plan.getPlanQty());
        view.put("planDate", plan.getPlanDate());
        view.put("status", plan.getStatus());
        view.put("remark", plan.getRemark());
        view.put("createdBy", plan.getCreatedBy());
        view.put("createdByName", creator == null ? null : creator.getRealName());
        view.put("releaseTime", plan.getReleaseTime());
        view.put("createdTime", plan.getCreatedTime());
        view.put("updatedTime", plan.getUpdatedTime());
        view.put("workOrderCount", workOrderCount);
        return view;
    }

    private String nextPlanNo() {
        long count = prodPlanMapper.selectCount(null) + 1;
        return "PLAN-" + LocalDateTime.now().getYear() + "-" + String.format("%03d", count);
    }

    private Map<String, Object> pageResult(long total, List<?> records) {
        return Map.of("total", total, "records", records);
    }

    private Map<String, Object> toWorkOrderSummary(ProdWorkOrder order) {
        ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("orderNo", order.getOrderNo());
        row.put("productId", order.getProductId());
        row.put("productName", order.getProductName());
        row.put("orderQty", order.getOrderQty());
        row.put("processName", order.getProcessName());
        row.put("progress", order.getProgress());
        row.put("status", order.getStatus());
        row.put("teamName", team == null ? null : team.getTeamName());
        row.put("deadline", order.getDeadline());
        row.put("scheduledStartTime", order.getScheduledStartTime());
        row.put("estimatedHours", order.getEstimatedHours());
        row.put("schedulingRank", order.getSchedulingRank());
        return row;
    }

    private int computeCompletionProgress(List<ProdWorkOrder> orders) {
        if (orders.isEmpty()) {
            return 0;
        }
        return (int) Math.round(orders.stream()
                .mapToInt(o -> o.getProgress() == null ? 0 : o.getProgress())
                .average()
                .orElse(0));
    }

    private String resolveExecutionStatus(ProdPlan plan, List<ProdWorkOrder> orders) {
        if ("draft".equals(plan.getStatus())) {
            return "draft";
        }
        if ("done".equals(plan.getStatus())) {
            return "done";
        }
        if (orders.isEmpty()) {
            return "released";
        }
        boolean allDone = orders.stream().allMatch(o -> "done".equals(o.getStatus()));
        if (allDone) {
            return "done";
        }
        boolean anyStarted = orders.stream().anyMatch(o ->
                ("done".equals(o.getStatus()) || "producing".equals(o.getStatus()) || "exception".equals(o.getStatus()))
                        || (o.getProgress() != null && o.getProgress() > 0));
        return anyStarted ? "in_progress" : "released";
    }
}
