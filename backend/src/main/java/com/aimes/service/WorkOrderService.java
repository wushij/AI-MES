package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.SchedulingApplyRequest;
import com.aimes.dto.Requests.SchedulingDispatchItem;
import com.aimes.dto.Requests.WorkOrderAssignRequest;
import com.aimes.dto.Requests.WorkOrderCreateRequest;
import com.aimes.dto.Requests.WorkOrderProgressRequest;
import com.aimes.dto.Requests.WorkOrderUpdateRequest;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkOrderService {

    private static final List<String> DEFAULT_PROCESSES = List.of("备料", "装配", "检测", "包装");

    private final ProdWorkOrderMapper prodWorkOrderMapper;
    private final ProdPlanMapper prodPlanMapper;
    private final ProdTeamMapper prodTeamMapper;
    private final ProdProcessRecordMapper prodProcessRecordMapper;
    private final ExcEventMapper excEventMapper;
    private final AuthService authService;
    private final WorkOrderNoService workOrderNoService;
    private final SysNotificationService sysNotificationService;
    private final SysUserMapper sysUserMapper;

    public WorkOrderService(ProdWorkOrderMapper prodWorkOrderMapper,
                            ProdPlanMapper prodPlanMapper,
                            ProdTeamMapper prodTeamMapper,
                            ProdProcessRecordMapper prodProcessRecordMapper,
                            ExcEventMapper excEventMapper,
                            AuthService authService,
                            WorkOrderNoService workOrderNoService,
                            SysNotificationService sysNotificationService,
                            SysUserMapper sysUserMapper) {
        this.prodWorkOrderMapper = prodWorkOrderMapper;
        this.prodPlanMapper = prodPlanMapper;
        this.prodTeamMapper = prodTeamMapper;
        this.prodProcessRecordMapper = prodProcessRecordMapper;
        this.excEventMapper = excEventMapper;
        this.authService = authService;
        this.workOrderNoService = workOrderNoService;
        this.sysNotificationService = sysNotificationService;
        this.sysUserMapper = sysUserMapper;
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
        detail.put("exceptions", excEventMapper.selectList(new LambdaQueryWrapper<ExcEvent>()
                .eq(ExcEvent::getWorkOrderId, id)
                .orderByDesc(ExcEvent::getOccurTime)));
        return detail;
    }

    @Transactional
    public Map<String, Object> create(WorkOrderCreateRequest request) {
        ProdWorkOrder order = new ProdWorkOrder();
        order.setOrderNo(StringUtils.hasText(request.getOrderNo()) ? request.getOrderNo() : workOrderNoService.nextOrderNo());
        order.setPlanId(request.getPlanId());
        order.setProductName(request.getProductName());
        order.setTeamId(request.getTeamId());
        order.setProcessName(StringUtils.hasText(request.getProcessName()) ? request.getProcessName() : DEFAULT_PROCESSES.get(0));
        order.setProgress(request.getProgress() == null ? 0 : request.getProgress());
        order.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : (request.getTeamId() == null ? "pending" : "assigned"));
        order.setPriority(request.getPriority() == null ? 2 : request.getPriority());
        order.setDeadline(request.getDeadline());
        order.setRemark(request.getRemark());
        prodWorkOrderMapper.insert(order);
        initProcessRecords(order.getId());
        return detail(order.getId());
    }

    @Transactional
    public Map<String, Object> update(Long id, WorkOrderUpdateRequest request) {
        ProdWorkOrder order = getOrder(id);
        if (request.getPlanId() != null) {
            order.setPlanId(request.getPlanId());
        }
        if (StringUtils.hasText(request.getProductName())) {
            order.setProductName(request.getProductName());
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
    public void delete(Long id) {
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
    public Map<String, Object> assign(Long id, WorkOrderAssignRequest request) {
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
        order.setStatus(request.getProgress() >= 100 ? "done" : "producing");
        if (request.getRemark() != null) {
            order.setRemark(request.getRemark());
        }
        prodWorkOrderMapper.updateById(order);

        ProdProcessRecord currentRecord = prodProcessRecordMapper.selectOne(new LambdaQueryWrapper<ProdProcessRecord>()
                .eq(ProdProcessRecord::getWorkOrderId, id)
                .eq(ProdProcessRecord::getProcessName, request.getProcessName())
                .last("limit 1"));
        if (currentRecord != null) {
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
        return detail(id);
    }

    @Transactional
    public Map<String, Object> complete(Long id) {
        ProdWorkOrder order = getOrder(id);
        order.setProgress(100);
        order.setStatus("done");
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
        return detail(id);
    }

    @Transactional
    public Map<String, Object> applySchedulingSuggestions(SchedulingApplyRequest request) {
        List<Map<String, Object>> applied = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();

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

            order.setTeamId(team.getId());
            order.setStatus("assigned");
            order.setRemark(buildSchedulingRemark(order.getRemark(), request, item));
            prodWorkOrderMapper.updateById(order);
            applied.add(toView(order));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applied", applied);
        result.put("skipped", skipped);
        result.put("appliedCount", applied.size());
        result.put("skippedCount", skipped.size());
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
                .ge(ProdWorkOrder::getDeadline, todayStart)
                .lt(ProdWorkOrder::getDeadline, tomorrowStart)
                .eq(teamId != null, ProdWorkOrder::getTeamId, teamId)
                .orderByAsc(ProdWorkOrder::getPriority);
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

    private String buildSchedulingRemark(String existingRemark, SchedulingApplyRequest request, SchedulingDispatchItem item) {
        String base = StringUtils.hasText(existingRemark) ? existingRemark.trim() : "";
        int aiIndex = base.indexOf("AI排产建议");
        if (aiIndex >= 0) {
            base = base.substring(0, aiIndex).trim();
            while (base.endsWith("；") || base.endsWith(";")) {
                base = base.substring(0, base.length() - 1).trim();
            }
        }
        String aiNote = buildAiSchedulingNote(request, item);
        if (!StringUtils.hasText(base)) {
            return aiNote;
        }
        return base + "；" + aiNote;
    }

    private String buildAiSchedulingNote(SchedulingApplyRequest request, SchedulingDispatchItem item) {
        StringBuilder builder = new StringBuilder("AI排产建议");
        if (request.getPlanDate() != null) {
            builder.append("，计划日 ").append(request.getPlanDate());
        }
        if (StringUtils.hasText(item.getStartTime())) {
            builder.append("，建议开工 ").append(item.getStartTime().trim());
        }
        if (StringUtils.hasText(item.getHours())) {
            builder.append("，预计 ").append(item.getHours().trim()).append(" 小时");
        }
        if (StringUtils.hasText(item.getTeamName())) {
            builder.append("，班组 ").append(item.getTeamName().trim());
        }
        return builder.toString();
    }

    private void initProcessRecords(Long workOrderId) {
        for (int i = 0; i < DEFAULT_PROCESSES.size(); i++) {
            ProdProcessRecord record = new ProdProcessRecord();
            record.setWorkOrderId(workOrderId);
            record.setSeqNo(i + 1);
            record.setProcessName(DEFAULT_PROCESSES.get(i));
            record.setStatus("waiting");
            prodProcessRecordMapper.insert(record);
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
        view.put("productName", order.getProductName());
        view.put("teamId", order.getTeamId());
        view.put("teamName", team == null ? null : team.getTeamName());
        view.put("processName", order.getProcessName());
        view.put("progress", order.getProgress());
        view.put("status", order.getStatus());
        view.put("priority", order.getPriority());
        view.put("deadline", order.getDeadline());
        view.put("claimUserId", order.getClaimUserId());
        view.put("remark", order.getRemark());
        view.put("exceptionCount", exceptionCount);
        view.put("createdTime", order.getCreatedTime());
        view.put("updatedTime", order.getUpdatedTime());
        return view;
    }
}
