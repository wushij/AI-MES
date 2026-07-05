package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.common.OperationLog;
import com.aimes.common.OperationLogRunner;
import com.aimes.dto.Requests.SchedulingApplyRequest;
import com.aimes.dto.Requests.SchedulingBottleneckItem;
import com.aimes.dto.Requests.SchedulingDispatchItem;
import com.aimes.dto.Requests.SchedulingPriorityItem;
import com.aimes.dto.Requests.WorkOrderAssignRequest;
import com.aimes.dto.Requests.WorkOrderCreateRequest;
import com.aimes.dto.Requests.WorkOrderProgressRequest;
import com.aimes.dto.Requests.WorkOrderUpdateRequest;
import com.aimes.config.AimesProperties;
import com.aimes.entity.ExcEvent;
import com.aimes.entity.ProdPlan;
import com.aimes.entity.ProdProcessRecord;
import com.aimes.entity.ProdTeam;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.entity.SysUser;
import com.aimes.mapper.ExcEventMapper;
import com.aimes.mapper.ProdPlanMapper;
import com.aimes.mapper.ProdProcessRecordMapper;
import com.aimes.mapper.ProdTeamMapper;
import com.aimes.mapper.ProdWorkOrderMapper;
import com.aimes.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class WorkOrderService {

    private final ProdWorkOrderMapper prodWorkOrderMapper;
    private final ProdPlanMapper prodPlanMapper;
    private final ProdTeamMapper prodTeamMapper;
    private final ProdProcessRecordMapper prodProcessRecordMapper;
    private final ExcEventMapper excEventMapper;
    private final AuthService authService;
    private final WorkOrderNoService workOrderNoService;
    private final SysNotificationService sysNotificationService;
    private final SysUserMapper sysUserMapper;
    private final ProcessRouteService processRouteService;
    private final OperationLogRunner operationLogRunner;
    private final ProductService productService;
    private final MaterialService materialService;
    private final AimesProperties aimesProperties;

    public WorkOrderService(ProdWorkOrderMapper prodWorkOrderMapper,
                            ProdPlanMapper prodPlanMapper,
                            ProdTeamMapper prodTeamMapper,
                            ProdProcessRecordMapper prodProcessRecordMapper,
                            ExcEventMapper excEventMapper,
                            AuthService authService,
                            WorkOrderNoService workOrderNoService,
                            SysNotificationService sysNotificationService,
                            SysUserMapper sysUserMapper,
                            ProcessRouteService processRouteService,
                            OperationLogRunner operationLogRunner,
                            ProductService productService,
                            MaterialService materialService,
                            AimesProperties aimesProperties) {
        this.prodWorkOrderMapper = prodWorkOrderMapper;
        this.prodPlanMapper = prodPlanMapper;
        this.prodTeamMapper = prodTeamMapper;
        this.prodProcessRecordMapper = prodProcessRecordMapper;
        this.excEventMapper = excEventMapper;
        this.authService = authService;
        this.workOrderNoService = workOrderNoService;
        this.sysNotificationService = sysNotificationService;
        this.sysUserMapper = sysUserMapper;
        this.processRouteService = processRouteService;
        this.operationLogRunner = operationLogRunner;
        this.productService = productService;
        this.materialService = materialService;
        this.aimesProperties = aimesProperties;
    }

    public Map<String, Object> list(long current, long size, String keyword, String status, Long teamId) {
        SysUser user = authService.currentUser();
        Long queryTeamId = "worker".equals(user.getRole()) ? user.getTeamId() : teamId;
        LambdaQueryWrapper<ProdWorkOrder> wrapper = new LambdaQueryWrapper<ProdWorkOrder>()
                .and(StringUtils.hasText(keyword), w -> w.like(ProdWorkOrder::getOrderNo, keyword)
                        .or()
                        .like(ProdWorkOrder::getProductName, keyword))
                .eq(StringUtils.hasText(status), ProdWorkOrder::getStatus, status)
                .eq(queryTeamId != null, ProdWorkOrder::getTeamId, queryTeamId)
                .orderByAsc(ProdWorkOrder::getPriority)
                .orderByAsc(ProdWorkOrder::getDeadline)
                .orderByDesc(ProdWorkOrder::getId);

        Page<ProdWorkOrder> page = prodWorkOrderMapper.selectPage(new Page<>(current, size), wrapper);
        return Map.of("total", page.getTotal(), "records", page.getRecords().stream().map(this::toView).toList());
    }

    public Map<String, Object> detail(Long id) {
        ProdWorkOrder order = getOrder(id);
        Map<String, Object> detail = new LinkedHashMap<>(toView(order));
        detail.put("processRecords", prodProcessRecordMapper.selectList(new LambdaQueryWrapper<ProdProcessRecord>()
                .eq(ProdProcessRecord::getWorkOrderId, id)
                .orderByAsc(ProdProcessRecord::getSeqNo)));
        if (order.getRoutingId() != null) {
            detail.put("routingContext", processRouteService.getExecutionContext(id));
        }
        detail.put("exceptions", excEventMapper.selectList(new LambdaQueryWrapper<ExcEvent>()
                .eq(ExcEvent::getWorkOrderId, id)
                .orderByDesc(ExcEvent::getOccurTime)));
        if (order.getProductId() != null) {
            detail.put("product", productService.getProductSummary(order.getProductId()));
            int qty = order.getOrderQty() != null ? order.getOrderQty() : 1;
            detail.put("bomPreview", productService.computeBomDemand(order.getProductId(), qty));
            detail.put("hasBom", productService.hasActiveBom(order.getProductId()));
        }
        return detail;
    }

    @Transactional
    public Map<String, Object> create(WorkOrderCreateRequest request) {
        ProdWorkOrder order = new ProdWorkOrder();
        order.setOrderNo(StringUtils.hasText(request.getOrderNo()) ? request.getOrderNo() : workOrderNoService.nextOrderNo());
        order.setPlanId(request.getPlanId());
        applyProduct(order, request.getProductId(), request.getProductName());
        order.setOrderQty(request.getOrderQty() != null ? request.getOrderQty() : 1);
        order.setTeamId(request.getTeamId());
        order.setProcessName(defaultFirstProcess(order.getProductId(), order.getProductName()));
        processRouteService.applyRoutingToWorkOrder(order, order.getProductId(), order.getProductName());
        order.setProgress(request.getProgress() == null ? 0 : request.getProgress());
        order.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : (request.getTeamId() == null ? "pending" : "assigned"));
        order.setPriority(request.getPriority() == null ? 2 : request.getPriority());
        order.setDeadline(request.getDeadline());
        order.setRemark(request.getRemark());
        prodWorkOrderMapper.insert(order);
        processRouteService.initProcessRecords(order.getId(), order.getProductId(), order.getProductName());
        return detail(order.getId());
    }

    @Transactional
    public Map<String, Object> update(Long id, WorkOrderUpdateRequest request) {
        ProdWorkOrder order = getOrder(id);
        if (request.getPlanId() != null) {
            order.setPlanId(request.getPlanId());
        }
        if (request.getProductId() != null || StringUtils.hasText(request.getProductName())) {
            applyProduct(order, request.getProductId(), request.getProductName());
        }
        if (request.getOrderQty() != null) {
            order.setOrderQty(request.getOrderQty());
        }
        if (request.getTeamId() != null) {
            order.setTeamId(request.getTeamId());
        }
        if (StringUtils.hasText(request.getProcessName())) {
            order.setProcessName(request.getProcessName());
        }
        if (request.getProgress() != null) {
            order.setProgress(request.getProgress());
        }
        if (StringUtils.hasText(request.getStatus())) {
            order.setStatus(request.getStatus());
        }
        if (request.getPriority() != null) {
            order.setPriority(request.getPriority());
        }
        if (request.getDeadline() != null) {
            order.setDeadline(request.getDeadline());
        }
        if (request.getRemark() != null) {
            order.setRemark(request.getRemark());
        }
        prodWorkOrderMapper.updateById(order);
        return detail(id);
    }

    @Transactional
    @OperationLog(module = "工单管理", action = "删除")
    public void delete(Long id) {
        operationLogRunner.runVoid("工单管理", "删除", "delete", new Object[]{id}, () -> deleteInternal(id));
    }

    private void deleteInternal(Long id) {
        ProdWorkOrder order = getOrder(id);
        Long workOrderId = order.getId();
        String orderNo = order.getOrderNo();

        prodProcessRecordMapper.delete(new LambdaQueryWrapper<ProdProcessRecord>()
                .eq(ProdProcessRecord::getWorkOrderId, workOrderId));
        excEventMapper.delete(new LambdaQueryWrapper<ExcEvent>()
                .eq(ExcEvent::getWorkOrderId, workOrderId));
        sysNotificationService.deleteByWorkOrderNo(orderNo);
        prodWorkOrderMapper.deleteById(workOrderId);
    }

    @Transactional
    @OperationLog(module = "工单管理", action = "派工")
    public Map<String, Object> assign(Long id, WorkOrderAssignRequest request) {
        return operationLogRunner.runUnchecked("工单管理", "派工", "assign", new Object[]{id, request},
                () -> assignInternal(id, request));
    }

    private Map<String, Object> assignInternal(Long id, WorkOrderAssignRequest request) {
        ProdWorkOrder order = getOrder(id);
        order.setTeamId(request.getTeamId());
        order.setPriority(request.getPriority());
        order.setStatus("assigned");
        order.setRemark(request.getRemark());
        prodWorkOrderMapper.updateById(order);

        // Notify all users in the assigned team
        if (request.getTeamId() != null) {
            List<SysUser> teamUsers = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getTeamId, request.getTeamId()));
            for (SysUser user : teamUsers) {
                sysNotificationService.createNotification(
                        user.getId(),
                        "新工单指派",
                        "您有新的工单任务：工单 " + order.getOrderNo() + "（产品：" + order.getProductName() + "）已下发至您的班组，请及时开工。",
                        "info",
                        "/work-orders"
                );
            }
        }

        return detail(id);
    }

    @Transactional
    public Map<String, Object> claim(Long id) {
        SysUser user = authService.currentUser();
        ProdWorkOrder order = getOrder(id);
        if (!"assigned".equals(order.getStatus())) {
            throw new BusinessException("当前工单不可领取");
        }
        if ("worker".equals(user.getRole()) && (user.getTeamId() == null || !user.getTeamId().equals(order.getTeamId()))) {
            throw new BusinessException("只能领取本班组工单");
        }

        order.setStatus("producing");
        order.setClaimUserId(user.getId());
        prodWorkOrderMapper.updateById(order);

        ProdProcessRecord first = prodProcessRecordMapper.selectOne(new LambdaQueryWrapper<ProdProcessRecord>()
                .eq(ProdProcessRecord::getWorkOrderId, id)
                .orderByAsc(ProdProcessRecord::getSeqNo)
                .last("limit 1"));
        if (first != null && !"done".equals(first.getStatus())) {
            first.setStatus("running");
            first.setStartTime(LocalDateTime.now());
            prodProcessRecordMapper.updateById(first);
            order.setProcessName(first.getProcessName());
            prodWorkOrderMapper.updateById(order);
        }
        return detail(id);
    }

    @Transactional
    public Map<String, Object> updateProgress(Long id, WorkOrderProgressRequest request) {
        SysUser user = authService.currentUser();
        ProdWorkOrder order = getOrder(id);
        if ("worker".equals(user.getRole()) && (user.getTeamId() == null || !user.getTeamId().equals(order.getTeamId()))) {
            throw new BusinessException("只能更新本班组工单");
        }

        order.setProgress(request.getProgress());
        order.setProcessName(request.getProcessName());
        if (request.getProgress() < 100) {
            order.setStatus("producing");
        }
        if (request.getRemark() != null) {
            order.setRemark(request.getRemark());
        }
        prodWorkOrderMapper.updateById(order);

        ProdProcessRecord currentRecord = prodProcessRecordMapper.selectOne(new LambdaQueryWrapper<ProdProcessRecord>()
                .eq(ProdProcessRecord::getWorkOrderId, id)
                .eq(ProdProcessRecord::getProcessName, request.getProcessName())
                .last("limit 1"));
        if (currentRecord != null) {
            if (request.getDeviceId() != null) {
                processRouteService.validateDeviceForOperation(currentRecord.getOperationId(), request.getDeviceId());
                currentRecord.setDeviceId(request.getDeviceId());
            }
            if (currentRecord.getStartTime() == null) {
                currentRecord.setStartTime(LocalDateTime.now());
            }
            currentRecord.setStatus(Boolean.TRUE.equals(request.getCompleteCurrentProcess()) ? "done" : "running");
            if (Boolean.TRUE.equals(request.getCompleteCurrentProcess())) {
                currentRecord.setEndTime(LocalDateTime.now());
                ProdProcessRecord next = prodProcessRecordMapper.selectOne(new LambdaQueryWrapper<ProdProcessRecord>()
                        .eq(ProdProcessRecord::getWorkOrderId, id)
                        .gt(ProdProcessRecord::getSeqNo, currentRecord.getSeqNo())
                        .orderByAsc(ProdProcessRecord::getSeqNo)
                        .last("limit 1"));
                if (next != null) {
                    next.setStatus("running");
                    if (next.getStartTime() == null) {
                        next.setStartTime(LocalDateTime.now());
                    }
                    prodProcessRecordMapper.updateById(next);
                    order.setProcessName(next.getProcessName());
                    prodWorkOrderMapper.updateById(order);
                }
            }
            prodProcessRecordMapper.updateById(currentRecord);
        }

        if (request.getProgress() >= 100) {
            return complete(id);
        }
        order.setUpdatedTime(LocalDateTime.now());
        prodWorkOrderMapper.updateById(order);
        return detail(id);
    }

    @Transactional
    public Map<String, Object> complete(Long id) {
        ProdWorkOrder order = getOrder(id);
        if ("done".equals(order.getStatus())) {
            return detail(id);
        }

        LocalDateTime completedAt = LocalDateTime.now();
        order.setProgress(100);
        order.setStatus("done");
        order.setUpdatedTime(completedAt);
        prodWorkOrderMapper.updateById(order);

        List<ProdProcessRecord> records = prodProcessRecordMapper.selectList(new LambdaQueryWrapper<ProdProcessRecord>()
                .eq(ProdProcessRecord::getWorkOrderId, id));
        for (ProdProcessRecord record : records) {
            record.setStatus("done");
            if (record.getStartTime() == null) {
                record.setStartTime(LocalDateTime.now());
            }
            if (record.getEndTime() == null) {
                record.setEndTime(LocalDateTime.now());
            }
            prodProcessRecordMapper.updateById(record);
        }

        ProdPlan plan = order.getPlanId() == null ? null : prodPlanMapper.selectById(order.getPlanId());
        if (plan != null) {
            long unfinished = prodWorkOrderMapper.selectCount(new LambdaQueryWrapper<ProdWorkOrder>()
                    .eq(ProdWorkOrder::getPlanId, plan.getId())
                    .ne(ProdWorkOrder::getStatus, "done"));
            if (unfinished == 0) {
                plan.setStatus("completed");
                prodPlanMapper.updateById(plan);
            }
        }
        if (aimesProperties.isBomPickOnComplete() && order.getProductId() != null
                && productService.hasActiveBom(order.getProductId())) {
            int qty = order.getOrderQty() != null ? order.getOrderQty() : 1;
            var demand = productService.computeBomDemand(order.getProductId(), qty);
            materialService.pickForWorkOrder(order.getId(), order.getProductId(), qty, demand);
        }
        return detail(id);
    }

    @Transactional
    public Map<String, Object> applySchedulingSuggestions(SchedulingApplyRequest request) {
        List<Map<String, Object>> applied = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        Map<String, SchedulingPriorityItem> priorityMap = buildSchedulingPriorityMap(request);

        for (SchedulingDispatchItem item : request.getDispatches()) {
            String orderNo = resolveWorkOrderNo(item);
            if (!StringUtils.hasText(orderNo)) {
                skipped.add(Map.of("reason", "缺少工单号"));
                continue;
            }

            ProdWorkOrder order = prodWorkOrderMapper.selectOne(new LambdaQueryWrapper<ProdWorkOrder>()
                    .eq(ProdWorkOrder::getOrderNo, orderNo.trim())
                    .last("limit 1"));
            if (order == null) {
                skipped.add(Map.of("workOrderCode", orderNo, "reason", "工单不存在"));
                continue;
            }

            ProdTeam team = resolveTeamByName(item.getTeamName());
            if (team == null) {
                skipped.add(Map.of("workOrderCode", orderNo, "reason", "未识别班组：" + item.getTeamName()));
                continue;
            }

            SchedulingPriorityItem priorityItem = priorityMap.get(orderNo.trim());
            Integer suggestedPriority = resolveSchedulingPriority(priorityItem);
            Integer previousPriority = order.getPriority();
            Long previousTeamId = order.getTeamId();
            ProdTeam previousTeam = previousTeamId == null ? null : prodTeamMapper.selectById(previousTeamId);
            String previousTeamName = previousTeam == null ? "未分配" : previousTeam.getTeamName();

            order.setTeamId(team.getId());
            order.setStatus("assigned");
            if (suggestedPriority != null) {
                order.setPriority(suggestedPriority);
            }
            order.setScheduledStartTime(parseSchedulingStartTime(item.getStartTime(), request.getPlanDate()));
            order.setEstimatedHours(parseEstimatedHours(item.getHours()));
            order.setSchedulingReason(buildSchedulingReason(
                    request.getPlanDate(),
                    team.getTeamName(),
                    previousTeamName,
                    priorityItem,
                    request.getSummary(),
                    request.getBottlenecks()));
            order.setSchedulingRank(null);
            order.setRemark(stripLegacyAiSchedulingRemark(order.getRemark()));
            prodWorkOrderMapper.updateById(order);

            Map<String, Object> appliedView = new LinkedHashMap<>(toView(order));
            appliedView.put("teamChanged", !Objects.equals(previousTeamId, team.getId()));
            appliedView.put("priorityChanged", suggestedPriority != null && !Objects.equals(previousPriority, suggestedPriority));
            appliedView.put("suggestedStartTime", item.getStartTime());
            appliedView.put("suggestedHours", item.getHours());
            if (priorityItem != null) {
                appliedView.put("priorityReason", priorityItem.getReason());
            }
            applied.add(appliedView);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applied", applied);
        result.put("skipped", skipped);
        result.put("appliedCount", applied.size());
        result.put("skippedCount", skipped.size());
        result.put("planDate", request.getPlanDate());
        result.put("summary", request.getSummary());
        return result;
    }

    public Map<String, Object> processBoard() {
        SysUser user = authService.currentUser();
        Long teamId = "worker".equals(user.getRole()) ? user.getTeamId() : null;

        List<Map<String, Object>> pending = listByStatuses(teamId, List.of("assigned"));
        List<Map<String, Object>> producing = listByStatuses(teamId, List.of("producing", "exception"));
        List<Map<String, Object>> doneToday = listDoneToday(teamId);

        return Map.of("pending", pending, "producing", producing, "doneToday", doneToday);
    }

    private List<Map<String, Object>> listDoneToday(Long teamId) {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LambdaQueryWrapper<ProdWorkOrder> wrapper = new LambdaQueryWrapper<ProdWorkOrder>()
                .eq(ProdWorkOrder::getStatus, "done")
                .ge(ProdWorkOrder::getUpdatedTime, todayStart)
                .lt(ProdWorkOrder::getUpdatedTime, tomorrowStart)
                .eq(teamId != null, ProdWorkOrder::getTeamId, teamId)
                .orderByDesc(ProdWorkOrder::getUpdatedTime);
        return prodWorkOrderMapper.selectList(wrapper).stream().map(this::toView).toList();
    }

    private List<Map<String, Object>> listByStatuses(Long teamId, List<String> statuses) {
        LambdaQueryWrapper<ProdWorkOrder> wrapper = new LambdaQueryWrapper<ProdWorkOrder>()
                .in(ProdWorkOrder::getStatus, statuses)
                .eq(teamId != null, ProdWorkOrder::getTeamId, teamId)
                .orderByAsc(ProdWorkOrder::getPriority);
        return prodWorkOrderMapper.selectList(wrapper).stream().map(this::toView).toList();
    }

    private ProdWorkOrder getOrder(Long id) {
        ProdWorkOrder order = prodWorkOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        return order;
    }

    private String resolveWorkOrderNo(SchedulingDispatchItem item) {
        if (StringUtils.hasText(item.getWorkOrderCode())) {
            return item.getWorkOrderCode().trim();
        }
        return StringUtils.hasText(item.getWorkOrderNo()) ? item.getWorkOrderNo().trim() : null;
    }

    private ProdTeam resolveTeamByName(String teamName) {
        if (!StringUtils.hasText(teamName)) {
            return null;
        }
        String normalized = teamName.trim();
        ProdTeam team = prodTeamMapper.selectOne(new LambdaQueryWrapper<ProdTeam>()
                .eq(ProdTeam::getTeamName, normalized)
                .last("limit 1"));
        if (team != null) {
            return team;
        }
        if (normalized.contains("甲")) {
            return selectTeamByNameLike("甲");
        }
        if (normalized.contains("乙")) {
            return selectTeamByNameLike("乙");
        }
        if (normalized.contains("丙")) {
            return selectTeamByNameLike("丙");
        }
        return null;
    }

    private ProdTeam selectTeamByNameLike(String keyword) {
        return prodTeamMapper.selectOne(new LambdaQueryWrapper<ProdTeam>()
                .like(ProdTeam::getTeamName, keyword)
                .last("limit 1"));
    }

    private String stripLegacyAiSchedulingRemark(String existingRemark) {
        if (!StringUtils.hasText(existingRemark)) {
            return existingRemark;
        }
        String base = existingRemark.trim();
        int aiIndex = base.indexOf("AI排产建议");
        if (aiIndex < 0) {
            return base;
        }
        base = base.substring(0, aiIndex).trim();
        while (base.endsWith("；") || base.endsWith(";")) {
            base = base.substring(0, base.length() - 1).trim();
        }
        return base;
    }

    private LocalDateTime parseSchedulingStartTime(String raw, LocalDate planDate) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        DateTimeFormatter full = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter minute = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        try {
            if (text.length() == 16) {
                return LocalDateTime.parse(text, minute);
            }
            if (text.length() >= 19) {
                return LocalDateTime.parse(text.substring(0, 19), full);
            }
            if (text.matches("\\d{2}:\\d{2}")) {
                LocalDate date = planDate != null ? planDate : LocalDate.now();
                return LocalDateTime.parse(date + " " + text, minute);
            }
        } catch (DateTimeParseException ignored) {
            return null;
        }
        return null;
    }

    private BigDecimal parseEstimatedHours(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String digits = raw.replaceAll("[^\\d.]", "");
        if (!StringUtils.hasText(digits)) {
            return null;
        }
        try {
            return new BigDecimal(digits).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String buildSchedulingReason(
            LocalDate planDate,
            String teamName,
            String previousTeamName,
            SchedulingPriorityItem priorityItem,
            String summary,
            List<SchedulingBottleneckItem> bottlenecks) {
        if (StringUtils.hasText(summary)) {
            return trimSchedulingReason(summary.trim());
        }

        List<String> parts = new ArrayList<>();
        if (planDate != null) {
            parts.add("计划排产日 " + planDate);
        }
        if (StringUtils.hasText(teamName)) {
            if (StringUtils.hasText(previousTeamName) && !teamName.equals(previousTeamName)) {
                parts.add("建议班组 " + teamName + "（原 " + previousTeamName + "）");
            } else {
                parts.add("建议班组 " + teamName);
            }
        }
        if (priorityItem != null && StringUtils.hasText(priorityItem.getReason())) {
            parts.add(priorityItem.getReason().trim());
        }
        if (bottlenecks != null) {
            for (SchedulingBottleneckItem bottleneck : bottlenecks) {
                String line = formatSchedulingBottleneck(bottleneck);
                if (StringUtils.hasText(line)) {
                    parts.add(line);
                }
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return trimSchedulingReason(String.join("；", parts));
    }

    private String trimSchedulingReason(String text) {
        return text.length() > 500 ? text.substring(0, 497) + "..." : text;
    }

    private String formatSchedulingBottleneck(SchedulingBottleneckItem bottleneck) {
        if (bottleneck == null) {
            return null;
        }
        StringBuilder line = new StringBuilder();
        if (StringUtils.hasText(bottleneck.getProcessName())) {
            line.append(bottleneck.getProcessName().trim());
            if (bottleneck.getLoadRate() != null && bottleneck.getLoadRate() > 0) {
                line.append("负荷").append(bottleneck.getLoadRate()).append("%");
            }
        }
        if (StringUtils.hasText(bottleneck.getSuggestion())) {
            if (line.length() > 0) {
                line.append("：");
            }
            line.append(bottleneck.getSuggestion().trim());
        }
        return line.length() > 0 ? line.toString() : null;
    }

    private Map<String, SchedulingPriorityItem> buildSchedulingPriorityMap(SchedulingApplyRequest request) {
        Map<String, SchedulingPriorityItem> map = new LinkedHashMap<>();
        if (request.getPriorities() == null) {
            return map;
        }
        for (SchedulingPriorityItem item : request.getPriorities()) {
            String orderNo = resolvePriorityWorkOrderNo(item);
            if (StringUtils.hasText(orderNo)) {
                map.put(orderNo.trim(), item);
            }
        }
        return map;
    }

    private String resolvePriorityWorkOrderNo(SchedulingPriorityItem item) {
        if (StringUtils.hasText(item.getWorkOrderCode())) {
            return item.getWorkOrderCode().trim();
        }
        return StringUtils.hasText(item.getWorkOrderNo()) ? item.getWorkOrderNo().trim() : null;
    }

    private Integer resolveSchedulingPriority(SchedulingPriorityItem item) {
        if (item == null) {
            return null;
        }
        if (item.getPriority() != null && item.getPriority() >= 1 && item.getPriority() <= 3) {
            return item.getPriority();
        }
        if (!StringUtils.hasText(item.getPriorityLabel())) {
            return null;
        }
        String label = item.getPriorityLabel().trim();
        if (label.contains("高")) {
            return 1;
        }
        if (label.contains("低")) {
            return 3;
        }
        return 2;
    }

    private String defaultFirstProcess(Long productId, String productName) {
        var operations = processRouteService.resolveRouting(productId, productName).operations();
        return operations.isEmpty() ? "备料" : operations.get(0).getOperationName();
    }

    private void applyProduct(ProdWorkOrder order, Long productId, String productName) {
        if (productId != null) {
            var summary = productService.getProductSummary(productId);
            order.setProductId(productId);
            order.setProductName((String) summary.get("productName"));
            return;
        }
        order.setProductName(productName);
        var product = productService.findByName(productName);
        if (product != null) {
            order.setProductId(product.getId());
        } else {
            order.setProductId(null);
        }
    }

    private Map<String, Object> toView(ProdWorkOrder order) {
        ProdPlan plan = order.getPlanId() == null ? null : prodPlanMapper.selectById(order.getPlanId());
        ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
        long exceptionCount = excEventMapper.selectCount(new LambdaQueryWrapper<ExcEvent>()
                .eq(ExcEvent::getWorkOrderId, order.getId())
                .in(ExcEvent::getStatus, List.of("open", "processing")));

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", order.getId());
        view.put("orderNo", order.getOrderNo());
        view.put("planId", order.getPlanId());
        view.put("planNo", plan == null ? null : plan.getPlanNo());
        view.put("productId", order.getProductId());
        view.put("productName", order.getProductName());
        view.put("orderQty", order.getOrderQty());
        view.put("routingId", order.getRoutingId());
        view.put("routeVersion", order.getRouteVersion());
        view.put("teamId", order.getTeamId());
        view.put("teamName", team == null ? null : team.getTeamName());
        view.put("processName", order.getProcessName());
        view.put("progress", order.getProgress());
        view.put("status", order.getStatus());
        view.put("priority", order.getPriority());
        view.put("deadline", order.getDeadline());
        view.put("scheduledStartTime", order.getScheduledStartTime());
        view.put("estimatedHours", order.getEstimatedHours());
        view.put("schedulingRank", order.getSchedulingRank());
        view.put("schedulingReason", order.getSchedulingReason());
        view.put("claimUserId", order.getClaimUserId());
        view.put("remark", order.getRemark());
        view.put("exceptionCount", exceptionCount);
        view.put("createdTime", order.getCreatedTime());
        view.put("updatedTime", order.getUpdatedTime());
        if ("done".equals(order.getStatus())) {
            view.put("completedAt", order.getUpdatedTime());
        }
        return view;
    }
}
