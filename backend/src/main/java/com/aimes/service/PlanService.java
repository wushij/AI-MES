package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.PlanSaveRequest;
import com.aimes.entity.ProdPlan;
import com.aimes.entity.ProdProcessRecord;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.entity.SysUser;
import com.aimes.mapper.ProdPlanMapper;
import com.aimes.mapper.ProdProcessRecordMapper;
import com.aimes.mapper.ProdWorkOrderMapper;
import com.aimes.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlanService {

    private static final List<String> DEFAULT_PROCESSES = List.of("备料", "装配", "检测", "包装");

    private final ProdPlanMapper prodPlanMapper;
    private final ProdWorkOrderMapper prodWorkOrderMapper;
    private final ProdProcessRecordMapper prodProcessRecordMapper;
    private final SysUserMapper sysUserMapper;
    private final AuthService authService;
    private final SysNotificationService sysNotificationService;
    private final WorkOrderNoService workOrderNoService;

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
        return toView(getPlan(id));
    }

    @Transactional
    public Map<String, Object> create(PlanSaveRequest request) {
        ProdPlan plan = new ProdPlan();
        plan.setPlanNo(StringUtils.hasText(request.getPlanNo()) ? request.getPlanNo() : nextPlanNo());
        plan.setProductName(request.getProductName());
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
        plan.setProductName(request.getProductName());
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
    public void delete(Long id) {
        long relatedOrders = prodWorkOrderMapper.selectCount(new LambdaQueryWrapper<ProdWorkOrder>()
                .eq(ProdWorkOrder::getPlanId, id));
        if (relatedOrders > 0) {
            throw new BusinessException("计划已关联工单，不能删除");
        }
        prodPlanMapper.deleteById(id);
    }

    @Transactional
    public Map<String, Object> release(Long id) {
        ProdPlan plan = getPlan(id);
        if (!"draft".equals(plan.getStatus())) {
            throw new BusinessException("只有草稿计划可以下发");
        }

        plan.setStatus("released");
        plan.setReleaseTime(LocalDateTime.now());
        prodPlanMapper.updateById(plan);

        // Notify admin and supervisor about the released plan
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

        ProdWorkOrder order = new ProdWorkOrder();
        order.setOrderNo(workOrderNoService.nextOrderNo());
        order.setPlanId(plan.getId());
        order.setProductName(plan.getProductName());
        order.setProcessName(DEFAULT_PROCESSES.get(0));
        order.setProgress(0);
        order.setStatus("pending");
        order.setPriority(2);
        order.setDeadline(plan.getPlanDate().atTime(18, 0));
        order.setRemark("由计划 " + plan.getPlanNo() + " 自动生成");
        prodWorkOrderMapper.insert(order);

        for (int i = 0; i < DEFAULT_PROCESSES.size(); i++) {
            ProdProcessRecord record = new ProdProcessRecord();
            record.setWorkOrderId(order.getId());
            record.setSeqNo(i + 1);
            record.setProcessName(DEFAULT_PROCESSES.get(i));
            record.setStatus("waiting");
            prodProcessRecordMapper.insert(record);
        }

        return Map.of("plan", toView(plan), "generatedWorkOrder", order);
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
}
