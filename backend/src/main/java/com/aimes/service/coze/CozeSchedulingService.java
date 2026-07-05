package com.aimes.service.coze;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.CozeSchedulingRequest;
import com.aimes.entity.ExcEvent;
import com.aimes.entity.MatMaterial;
import com.aimes.entity.ProdTeam;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.mapper.ExcEventMapper;
import com.aimes.mapper.MatMaterialMapper;
import com.aimes.mapper.ProdTeamMapper;
import com.aimes.mapper.ProdWorkOrderMapper;
import com.aimes.service.CozeConfigService;
import com.aimes.service.DeviceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CozeSchedulingService {

    private final ProdWorkOrderMapper prodWorkOrderMapper;
    private final ProdTeamMapper prodTeamMapper;
    private final MatMaterialMapper matMaterialMapper;
    private final ExcEventMapper excEventMapper;
    private final CozeConfigService cozeConfigService;
    private final DeviceService deviceService;
    private final CozeApiClient cozeApiClient;
    private final ObjectMapper objectMapper;

    public Map<String, Object> scheduling(CozeSchedulingRequest request) {
        List<ProdWorkOrder> workOrders = request.getWorkOrderIds().stream()
                .map(prodWorkOrderMapper::selectById)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<MatMaterial> warningMaterials = matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                .eq(MatMaterial::getAlertStatus, "warning"));
        Map<String, Boolean> constraints = resolveSchedulingConstraints(request);
        LocalDate planDate = request.getPlanDate() != null ? request.getPlanDate() : LocalDate.now();

        try {
            if (cozeConfigService.isConfigured()) {
                String workflowId = cozeConfigService.getEffectiveWorkflowId();
                if (!StringUtils.hasText(workflowId)) {
                    return mockSchedulingResult(workOrders, warningMaterials, constraints, planDate,
                            "未配置排产工作流 ID，请在 Coze 配置中填写");
                }
                Map<String, Object> parsed = finalizeSchedulingResult(
                        runSchedulingWorkflow(workOrders, warningMaterials, planDate, constraints),
                        workOrders,
                        constraints,
                        planDate);
                return Map.of(
                        "mode", "live",
                        "result", parsed,
                        "constraints", constraints
                );
            }
        } catch (Exception ex) {
            return mockSchedulingResult(workOrders, warningMaterials, constraints, planDate,
                    "Coze 工作流调用失败：" + ex.getMessage()
                            + "。请确认工作流「开始节点」入参名为 plan_date、work_orders_json、material_status、constraints_json、teams_json、devices_json、current_time");
        }
        return mockSchedulingResult(workOrders, warningMaterials, constraints, planDate,
                "Coze 未启用或未配置，当前展示演示排产结果");
    }

    private Map<String, Boolean> resolveSchedulingConstraints(CozeSchedulingRequest request) {
        Map<String, Boolean> constraints = new LinkedHashMap<>();
        constraints.put("materialAvailability", request.getMaterialConstraint() == null || request.getMaterialConstraint());
        constraints.put("deviceLoad", request.getDeviceConstraint() == null || request.getDeviceConstraint());
        constraints.put("teamHours", request.getTeamConstraint() == null || request.getTeamConstraint());
        return constraints;
    }

    private Map<String, Object> mockSchedulingResult(
            List<ProdWorkOrder> workOrders,
            List<MatMaterial> warningMaterials,
            Map<String, Boolean> constraints,
            LocalDate planDate,
            String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "mock");
        result.put("message", message);
        result.put("constraints", constraints);
        result.put("result", finalizeSchedulingResult(
                buildMockScheduling(workOrders, warningMaterials, constraints, planDate),
                workOrders,
                constraints,
                planDate));
        return result;
    }

    private Map<String, Object> parseWorkflowSchedulingResult(JsonNode root) throws IOException {
        JsonNode dataNode = root.path("data");
        if (dataNode.isTextual()) {
            JsonNode parsedData = objectMapper.readTree(dataNode.asText());
            Map<String, Object> fromData = extractSchedulingPayload(parsedData);
            if (fromData != null) {
                return fromData;
            }
        } else if (dataNode.isObject()) {
            Map<String, Object> fromData = extractSchedulingPayload(dataNode);
            if (fromData != null) {
                return fromData;
            }
        }
        Map<String, Object> fromRoot = extractSchedulingPayload(root);
        return fromRoot;
    }

    private Map<String, Object> extractSchedulingPayload(JsonNode node) throws IOException {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String text = cozeApiClient.stripMarkdownJson(node.asText());
            if (!StringUtils.hasText(text)) {
                return null;
            }
            return extractSchedulingPayload(objectMapper.readTree(text));
        }
        if (node.isObject()) {
            if (hasSchedulingKeys(node)) {
                return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
            }
            for (String key : List.of("Output", "output", "result", "data", "content")) {
                Map<String, Object> nested = extractSchedulingPayload(node.path(key));
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private boolean hasSchedulingKeys(JsonNode node) {
        return node.has("priorities") || node.has("bottlenecks")
                || node.has("dispatchSuggestions") || node.has("dispatches");
    }

    public Map<String, Object> testWorkflowHealth() {
        Map<String, Object> workflow = new LinkedHashMap<>();
        String workflowId = cozeConfigService.getEffectiveWorkflowId();
        workflow.put("workflowId", StringUtils.hasText(workflowId) ? workflowId : null);
        if (!StringUtils.hasText(workflowId)) {
            workflow.put("status", "skipped");
            workflow.put("message", "未配置排产工作流 ID，跳过工作流测试");
            return workflow;
        }

        try {
            List<ProdWorkOrder> workOrders = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                    .in(ProdWorkOrder::getStatus, List.of("pending", "assigned"))
                    .orderByAsc(ProdWorkOrder::getPriority)
                    .last("limit 1"));
            if (workOrders.isEmpty()) {
                workOrders = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                        .orderByDesc(ProdWorkOrder::getUpdatedTime)
                        .last("limit 1"));
            }
            if (workOrders.isEmpty()) {
                workOrders = List.of(buildSampleWorkOrder());
            }

            List<MatMaterial> warningMaterials = matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                    .eq(MatMaterial::getAlertStatus, "warning")
                    .last("limit 5"));
            ProdWorkOrder sampleOrder = workOrders.get(0);
            Map<String, Object> parsed = runSchedulingWorkflow(
                    workOrders, warningMaterials, LocalDate.now(), resolveSchedulingConstraints(new CozeSchedulingRequest()));
            Map<String, Object> summary = summarizeSchedulingResult(parsed);

            workflow.put("status", "ok");
            workflow.put("mode", "live");
            workflow.put("sampleWorkOrderNo", sampleOrder.getOrderNo());
            workflow.put("summary", summary);
            workflow.put("message", "排产工作流调用成功，样例工单 "
                    + sampleOrder.getOrderNo()
                    + "，返回 priorities=" + summary.get("priorities")
                    + "、bottlenecks=" + summary.get("bottlenecks")
                    + "、dispatches=" + summary.get("dispatches"));
        } catch (Exception ex) {
            workflow.put("status", "error");
            workflow.put("message", "排产工作流测试失败：" + ex.getMessage());
        }
        return workflow;
    }

    private ProdWorkOrder buildSampleWorkOrder() {
        ProdWorkOrder sample = new ProdWorkOrder();
        sample.setOrderNo("WO-TEST-001");
        sample.setProductName("连通性测试产品");
        sample.setProcessName("装配工序");
        sample.setPriority(2);
        sample.setStatus("pending");
        sample.setProgress(0);
        return sample;
    }

    private Map<String, Object> runSchedulingWorkflow(
            List<ProdWorkOrder> workOrders,
            List<MatMaterial> warningMaterials,
            LocalDate planDate,
            Map<String, Boolean> constraints) throws IOException, InterruptedException {
        String workflowId = cozeConfigService.getEffectiveWorkflowId();
        if (!StringUtils.hasText(workflowId)) {
            throw new IOException("未配置排产工作流 ID");
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("plan_date", String.valueOf(planDate));
        parameters.put("work_orders_json", objectMapper.writeValueAsString(buildSchedulingWorkOrderViews(workOrders)));
        if (Boolean.TRUE.equals(constraints.get("materialAvailability"))) {
            parameters.put("material_status", objectMapper.writeValueAsString(warningMaterials));
        } else {
            parameters.put("material_status", "[]");
        }
        parameters.put("constraints_json", objectMapper.writeValueAsString(constraints));
        if (Boolean.TRUE.equals(constraints.get("teamHours"))) {
            parameters.put("teams_json", objectMapper.writeValueAsString(buildSchedulingTeamLoads(workOrders)));
        } else {
            parameters.put("teams_json", "[]");
        }
        if (Boolean.TRUE.equals(constraints.get("deviceLoad"))) {
            parameters.put("devices_json", objectMapper.writeValueAsString(deviceService.schedulingLoads()));
        } else {
            parameters.put("devices_json", "[]");
        }
        parameters.put("current_time", LocalDateTime.now().format(CozeConstants.SCHEDULING_TIME_FORMAT));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflow_id", workflowId.trim());
        payload.put("bot_id", cozeApiClient.botId());
        payload.put("parameters", parameters);
        JsonNode root = cozeApiClient.invokeJson(cozeApiClient.workflowApiUrl() + "/workflow/run", payload);
        Map<String, Object> parsed = parseWorkflowSchedulingResult(root);
        if (parsed == null || parsed.isEmpty()) {
            throw new IOException("工作流返回结果无法解析，请检查 Coze 结束节点是否输出 JSON（priorities / bottlenecks / dispatchSuggestions）");
        }
        return parsed;
    }

    private List<Map<String, Object>> buildSchedulingWorkOrderViews(List<ProdWorkOrder> workOrders) {
        List<Map<String, Object>> views = new ArrayList<>();
        for (ProdWorkOrder order : workOrders) {
            Map<String, Object> view = objectMapper.convertValue(order, new TypeReference<Map<String, Object>>() {});
            ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
            view.put("workOrderCode", order.getOrderNo());
            view.put("teamName", team == null ? null : team.getTeamName());
            views.add(view);
        }
        return views;
    }

    private Map<String, String> resolveAssignedTeamNames(List<ProdWorkOrder> workOrders) {
        Map<String, String> assignedTeams = new LinkedHashMap<>();
        for (ProdWorkOrder order : workOrders) {
            if (order.getTeamId() == null || !StringUtils.hasText(order.getOrderNo())) {
                continue;
            }
            ProdTeam team = prodTeamMapper.selectById(order.getTeamId());
            if (team != null && StringUtils.hasText(team.getTeamName())) {
                assignedTeams.put(order.getOrderNo(), team.getTeamName());
            }
        }
        return assignedTeams;
    }

    private Map<String, Object> finalizeSchedulingResult(
            Map<String, Object> parsed,
            List<ProdWorkOrder> workOrders,
            Map<String, Boolean> constraints,
            LocalDate planDate) {
        if (parsed == null || parsed.isEmpty()) {
            return parsed;
        }
        Map<String, Object> result = applySchedulingConstraintsToResult(parsed, constraints, planDate);
        optimizeDispatchTeamsByLoad(result, workOrders, constraints);
        normalizeDispatchStartTimes(result, planDate, Boolean.TRUE.equals(constraints.get("teamHours")));
        result.put("summary", buildSchedulingSummary(result, workOrders, constraints, planDate));
        return result;
    }

    public Map<String, Object> schedulingContext(List<Long> workOrderIds) {
        List<ProdWorkOrder> workOrders = workOrderIds.stream()
                .map(prodWorkOrderMapper::selectById)
                .filter(java.util.Objects::nonNull)
                .toList();

        List<Map<String, Object>> workOrderViews = workOrders.stream()
                .map(this::toSchedulingContextWorkOrder)
                .toList();

        List<Map<String, Object>> materialAlerts = matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                        .eq(MatMaterial::getAlertStatus, "warning")
                        .orderByAsc(MatMaterial::getStockQty))
                .stream()
                .map(this::toSchedulingMaterialAlert)
                .toList();

        List<Long> ids = workOrders.stream().map(ProdWorkOrder::getId).toList();
        List<Map<String, Object>> exceptions = ids.isEmpty()
                ? List.of()
                : excEventMapper.selectList(new LambdaQueryWrapper<ExcEvent>()
                        .in(ExcEvent::getWorkOrderId, ids)
                        .in(ExcEvent::getStatus, List.of("open", "processing"))
                        .orderByDesc(ExcEvent::getCreateTime))
                .stream()
                .map(this::toSchedulingExceptionView)
                .toList();

        List<Map<String, Object>> teams = buildSchedulingTeamLoads(workOrders);
        List<Map<String, Object>> devices = deviceService.schedulingLoads();

        long overdueCount = workOrderViews.stream()
                .filter(row -> Boolean.TRUE.equals(row.get("overdue")))
                .count();

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("selectedCount", workOrders.size());
        kpi.put("overdueCount", overdueCount);
        kpi.put("exceptionCount", exceptions.size());
        kpi.put("materialWarningCount", materialAlerts.size());
        kpi.put("deviceFaultCount", devices.stream().filter(row -> "fault".equals(row.get("status")) || "repairing".equals(row.get("status"))).count());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("workOrders", workOrderViews);
        context.put("materialAlerts", materialAlerts);
        context.put("exceptions", exceptions);
        context.put("teams", teams);
        context.put("devices", devices);
        context.put("kpi", kpi);
        return context;
    }

    private Map<String, Object> toSchedulingContextWorkOrder(ProdWorkOrder order) {
        ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
        long exceptionCount = excEventMapper.selectCount(new LambdaQueryWrapper<ExcEvent>()
                .eq(ExcEvent::getWorkOrderId, order.getId())
                .in(ExcEvent::getStatus, List.of("open", "processing")));

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", order.getId());
        view.put("orderNo", order.getOrderNo());
        view.put("productName", order.getProductName());
        view.put("processName", order.getProcessName());
        view.put("status", order.getStatus());
        view.put("progress", order.getProgress() == null ? 0 : order.getProgress());
        view.put("priority", order.getPriority());
        view.put("priorityLabel", schedulingPriorityLabel(order.getPriority()));
        view.put("teamId", order.getTeamId());
        view.put("teamName", team == null ? null : team.getTeamName());
        view.put("deadline", order.getDeadline());
        view.put("exceptionCount", exceptionCount);

        LocalDate deadlineDate = order.getDeadline() == null ? null : order.getDeadline().toLocalDate();
        LocalDate today = LocalDate.now();
        boolean overdue = deadlineDate != null && deadlineDate.isBefore(today);
        Long daysToDeadline = deadlineDate == null ? null : java.time.temporal.ChronoUnit.DAYS.between(today, deadlineDate);
        view.put("overdue", overdue);
        view.put("daysToDeadline", daysToDeadline);
        view.put("deadlineLabel", buildDeadlineLabel(deadlineDate, overdue, daysToDeadline));
        view.put("materialRisk", materialAlertsContainProduct(order.getProductName()) ? "warning" : "normal");
        return view;
    }

    private boolean materialAlertsContainProduct(String productName) {
        if (!StringUtils.hasText(productName)) {
            return false;
        }
        long count = matMaterialMapper.selectCount(new LambdaQueryWrapper<MatMaterial>()
                .eq(MatMaterial::getAlertStatus, "warning")
                .like(MatMaterial::getMaterialName, productName.trim()));
        return count > 0;
    }

    private String buildDeadlineLabel(LocalDate deadlineDate, boolean overdue, Long daysToDeadline) {
        if (deadlineDate == null) {
            return "未设交期";
        }
        if (overdue) {
            return "已逾期 " + Math.abs(daysToDeadline) + " 天";
        }
        if (daysToDeadline != null && daysToDeadline == 0) {
            return "今日交期";
        }
        if (daysToDeadline != null && daysToDeadline <= 3) {
            return "距交期 " + daysToDeadline + " 天";
        }
        return deadlineDate.toString();
    }

    private Map<String, Object> toSchedulingMaterialAlert(MatMaterial material) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", material.getId());
        row.put("materialCode", material.getMaterialCode());
        row.put("materialName", material.getMaterialName());
        row.put("stockQty", material.getStockQty());
        row.put("safetyStock", material.getSafetyStock());
        row.put("unit", material.getUnit());
        row.put("alertStatus", material.getAlertStatus());
        return row;
    }

    private Map<String, Object> toSchedulingExceptionView(ExcEvent event) {
        ProdWorkOrder order = event.getWorkOrderId() == null
                ? null
                : prodWorkOrderMapper.selectById(event.getWorkOrderId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", event.getId());
        row.put("eventNo", event.getEventNo());
        row.put("eventType", event.getEventType());
        row.put("workOrderId", event.getWorkOrderId());
        row.put("workOrderNo", order == null ? null : order.getOrderNo());
        row.put("deviceId", event.getDeviceId());
        if (event.getDeviceId() != null) {
            try {
                Map<String, Object> device = deviceService.detail(event.getDeviceId());
                row.put("deviceCode", device.get("deviceCode"));
                row.put("deviceName", device.get("deviceName"));
                row.put("deviceStatus", device.get("status"));
            } catch (BusinessException ignored) {
                // device may have been removed
            }
        }
        row.put("description", event.getDescription());
        row.put("status", event.getStatus());
        row.put("occurTime", event.getOccurTime());
        return row;
    }

    private List<Map<String, Object>> buildSchedulingTeamLoads(List<ProdWorkOrder> selectedOrders) {
        List<ProdTeam> teams = prodTeamMapper.selectList(new LambdaQueryWrapper<ProdTeam>().orderByAsc(ProdTeam::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProdTeam team : teams) {
            List<ProdWorkOrder> teamOrders = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                    .eq(ProdWorkOrder::getTeamId, team.getId())
                    .in(ProdWorkOrder::getStatus, List.of("assigned", "producing", "exception")));
            long pending = teamOrders.stream().filter(o -> "assigned".equals(o.getStatus())).count();
            long producing = teamOrders.stream().filter(o -> "producing".equals(o.getStatus())).count();
            long selectedHere = selectedOrders.stream()
                    .filter(o -> team.getId().equals(o.getTeamId()))
                    .count();
            int activeTasks = teamOrders.size();
            int loadRate = Math.min(100, activeTasks * 20 + (int) producing * 15);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", team.getId());
            row.put("teamName", team.getTeamName());
            row.put("memberCount", team.getMemberCount());
            row.put("pendingCount", pending);
            row.put("producingCount", producing);
            row.put("activeTaskCount", activeTasks);
            row.put("selectedCount", selectedHere);
            row.put("proposedHours", 0);
            row.put("loadRate", loadRate);
            result.add(row);
        }
        return result;
    }

    private String buildSchedulingSummary(
            Map<String, Object> result,
            List<ProdWorkOrder> workOrders,
            Map<String, Boolean> constraints,
            LocalDate planDate) {
        List<Map<String, Object>> priorities = copyMapList(firstPresent(result, "priorities", "prioritySuggestions"));
        List<Map<String, Object>> bottlenecks = copyMapList(firstPresent(result, "bottlenecks", "bottleneckWarnings"));
        List<Map<String, Object>> dispatches = copyMapList(firstPresent(result, "dispatches", "dispatchSuggestions"));

        StringBuilder summary = new StringBuilder();
        summary.append("计划日期 ").append(planDate).append("，共 ").append(workOrders.size()).append(" 个待排产工单。");

        if (!priorities.isEmpty()) {
            Map<String, Object> top = priorities.get(0);
            summary.append(" 建议优先排产 ")
                    .append(top.getOrDefault("workOrderCode", "--"))
                    .append("（").append(top.getOrDefault("priorityLabel", "--")).append("）");
            Object reason = top.get("reason");
            if (reason != null && StringUtils.hasText(String.valueOf(reason))) {
                summary.append("，理由：").append(String.valueOf(reason));
            }
            summary.append("。");
        }

        if (Boolean.TRUE.equals(constraints.get("deviceLoad")) && !bottlenecks.isEmpty()) {
            Map<String, Object> bottleneck = bottlenecks.stream()
                    .filter(row -> {
                        Object rate = row.get("loadRate");
                        return rate instanceof Number number && number.intValue() > 0;
                    })
                    .findFirst()
                    .orElse(bottlenecks.get(0));
            summary.append(" 瓶颈工序 ")
                    .append(bottleneck.getOrDefault("processName", "--"))
                    .append("，预计负荷 ")
                    .append(bottleneck.getOrDefault("loadRate", 0))
                    .append("%。");
        }

        if (!dispatches.isEmpty()) {
            Map<String, Object> last = dispatches.get(dispatches.size() - 1);
            summary.append(" 末单预计 ")
                    .append(last.getOrDefault("startTime", "--"))
                    .append(" 启动，耗时 ")
                    .append(last.getOrDefault("hours", "--"))
                    .append(" 小时。");
        }

        if (!Boolean.TRUE.equals(constraints.get("materialAvailability"))) {
            summary.append(" 本次未纳入物料可用性约束。");
        }
        if (!Boolean.TRUE.equals(constraints.get("teamHours"))) {
            summary.append(" 本次未纳入班组工时约束。");
        }
        return summary.toString();
    }

    private void optimizeDispatchTeamsByLoad(
            Map<String, Object> result,
            List<ProdWorkOrder> workOrders,
            Map<String, Boolean> constraints) {
        if (!Boolean.TRUE.equals(constraints.get("teamHours"))) {
            annotateCurrentTeams(result, workOrders);
            return;
        }
        List<Map<String, Object>> teamLoads = buildSchedulingTeamLoads(workOrders);
        if (teamLoads.isEmpty()) {
            return;
        }
        Map<String, Integer> proposedHoursByTeam = new LinkedHashMap<>();
        for (Map<String, Object> team : teamLoads) {
            proposedHoursByTeam.put(String.valueOf(team.get("teamName")), 0);
        }
        Map<String, String> assignedTeams = resolveAssignedTeamNames(workOrders);

        for (String dispatchKey : List.of("dispatches", "dispatchSuggestions")) {
            if (!result.containsKey(dispatchKey)) {
                continue;
            }
            List<Map<String, Object>> dispatches = copyMapList(result.get(dispatchKey));
            for (Map<String, Object> row : dispatches) {
                String workOrderCode = readDispatchWorkOrderCode(row);
                if (StringUtils.hasText(workOrderCode) && assignedTeams.containsKey(workOrderCode)) {
                    row.put("currentTeam", assignedTeams.get(workOrderCode));
                }
                int hours = parseDispatchHours(row.get("hours"));
                String teamName = pickLeastLoadedTeamName(teamLoads, proposedHoursByTeam);
                row.put("teamName", teamName);
                row.put("team", teamName);
                row.put("suggestedTeam", teamName);
                proposedHoursByTeam.merge(teamName, hours, Integer::sum);
            }
            result.put(dispatchKey, dispatches);
        }
    }

    private void annotateCurrentTeams(Map<String, Object> result, List<ProdWorkOrder> workOrders) {
        Map<String, String> assignedTeams = resolveAssignedTeamNames(workOrders);
        if (assignedTeams.isEmpty()) {
            return;
        }
        for (String dispatchKey : List.of("dispatches", "dispatchSuggestions")) {
            if (!result.containsKey(dispatchKey)) {
                continue;
            }
            List<Map<String, Object>> dispatches = copyMapList(result.get(dispatchKey));
            for (Map<String, Object> row : dispatches) {
                String workOrderCode = readDispatchWorkOrderCode(row);
                String assignedTeam = assignedTeams.get(workOrderCode);
                if (StringUtils.hasText(assignedTeam)) {
                    row.put("currentTeam", assignedTeam);
                }
            }
            result.put(dispatchKey, dispatches);
        }
    }

    private String pickLeastLoadedTeamName(
            List<Map<String, Object>> teamLoads,
            Map<String, Integer> proposedHoursByTeam) {
        Map<String, Object> best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Map<String, Object> team : teamLoads) {
            String name = String.valueOf(team.get("teamName"));
            int loadRate = team.get("loadRate") instanceof Number number ? number.intValue() : 0;
            int activeTasks = team.get("activeTaskCount") instanceof Number number ? number.intValue() : 0;
            int proposed = proposedHoursByTeam.getOrDefault(name, 0);
            int score = loadRate * 100 + proposed * 10 + activeTasks;
            if (score < bestScore) {
                bestScore = score;
                best = team;
            }
        }
        return best == null ? "待定" : String.valueOf(best.get("teamName"));
    }

    private String readDispatchWorkOrderCode(Map<String, Object> row) {
        for (String key : List.of("workOrderCode", "workOrderNo", "orderNo", "code")) {
            Object value = row.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (StringUtils.hasText(text) && !"null".equals(text) && !"--".equals(text)) {
                return text;
            }
        }
        return "";
    }

    private Map<String, Object> applySchedulingConstraintsToResult(
            Map<String, Object> parsed,
            Map<String, Boolean> constraints,
            LocalDate planDate) {
        if (parsed == null || parsed.isEmpty()) {
            return parsed;
        }
        boolean materialEnabled = Boolean.TRUE.equals(constraints.get("materialAvailability"));
        boolean deviceEnabled = Boolean.TRUE.equals(constraints.get("deviceLoad"));
        boolean teamEnabled = Boolean.TRUE.equals(constraints.get("teamHours"));

        Map<String, Object> result = new LinkedHashMap<>(parsed);
        List<Map<String, Object>> priorities = reshapePriorities(
                firstPresent(result, "priorities", "prioritySuggestions"),
                materialEnabled,
                teamEnabled);
        putIfPresent(result, "priorities", priorities);
        putIfPresent(result, "prioritySuggestions", priorities);

        List<Map<String, Object>> bottlenecks = reshapeBottlenecks(
                firstPresent(result, "bottlenecks", "bottleneckWarnings"),
                materialEnabled,
                deviceEnabled,
                teamEnabled);
        putIfPresent(result, "bottlenecks", bottlenecks);
        putIfPresent(result, "bottleneckWarnings", bottlenecks);

        for (String dispatchKey : List.of("dispatches", "dispatchSuggestions")) {
            if (result.containsKey(dispatchKey)) {
                result.put(dispatchKey, reshapeDispatches(result.get(dispatchKey), teamEnabled, planDate));
            }
        }
        return result;
    }

    private Object firstPresent(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key) && source.get(key) != null) {
                return source.get(key);
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (target.containsKey(key)) {
            target.put(key, value);
        }
    }

    private List<Map<String, Object>> copyMapList(Object source) {
        if (!(source instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> copied = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    row.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                copied.add(row);
            }
        }
        return copied;
    }

    private List<Map<String, Object>> reshapePriorities(
            Object source,
            boolean materialEnabled,
            boolean teamEnabled) {
        List<Map<String, Object>> priorities = copyMapList(source);
        for (Map<String, Object> row : priorities) {
            for (String key : List.of("reason", "comment", "rationale")) {
                if (!row.containsKey(key)) {
                    continue;
                }
                String text = String.valueOf(row.get(key));
                if (!materialEnabled) {
                    text = stripMaterialReferences(text);
                }
                if (!teamEnabled) {
                    text = appendTeamConstraintNote(text);
                }
                row.put(key, text);
            }
        }
        return priorities;
    }

    private List<Map<String, Object>> reshapeBottlenecks(
            Object source,
            boolean materialEnabled,
            boolean deviceEnabled,
            boolean teamEnabled) {
        List<Map<String, Object>> bottlenecks = new ArrayList<>();
        if (!deviceEnabled) {
            bottlenecks.add(constraintNoticeBottleneck("—", "未启用设备负荷约束，已跳过瓶颈分析"));
        } else {
            for (Map<String, Object> row : copyMapList(source)) {
                if (!materialEnabled && isMaterialBottleneck(row)) {
                    continue;
                }
                if (!materialEnabled) {
                    row.put("suggestion", stripMaterialReferences(String.valueOf(
                            row.getOrDefault("suggestion", row.getOrDefault("advice", row.get("reason"))))));
                    row.put("advice", row.get("suggestion"));
                }
                bottlenecks.add(row);
            }
        }
        if (!materialEnabled) {
            bottlenecks.add(constraintNoticeBottleneck("物料约束", "未启用物料可用性约束，排产未纳入缺料风险"));
        }
        if (!teamEnabled) {
            bottlenecks.add(constraintNoticeBottleneck("班组约束", "未启用班组工时约束，派工时间仅按默认班次估算"));
        }
        return bottlenecks;
    }

    private List<Map<String, Object>> reshapeDispatches(
            Object source,
            boolean teamEnabled,
            LocalDate planDate) {
        List<Map<String, Object>> dispatches = copyMapList(source);
        if (teamEnabled || dispatches.isEmpty()) {
            return dispatches;
        }
        String baselineStart = formatStartTime(computeEarliestDispatchStart(planDate, 0));
        for (Map<String, Object> row : dispatches) {
            row.put("hours", "待定");
            row.put("estimatedHours", "待定");
            row.put("startTime", baselineStart);
            row.put("suggestedStart", baselineStart);
            row.put("suggestedStartTime", baselineStart);
        }
        return dispatches;
    }

    private void normalizeDispatchStartTimes(
            Map<String, Object> result,
            LocalDate planDate,
            boolean teamEnabled) {
        LocalDate effectivePlanDate = planDate != null ? planDate : LocalDate.now();
        for (String dispatchKey : List.of("dispatches", "dispatchSuggestions")) {
            if (!result.containsKey(dispatchKey)) {
                continue;
            }
            List<Map<String, Object>> dispatches = copyMapList(result.get(dispatchKey));
            if (dispatches.isEmpty()) {
                continue;
            }
            LocalDateTime cursor = computeEarliestDispatchStart(effectivePlanDate, 0);
            for (Map<String, Object> row : dispatches) {
                String formatted = formatStartTime(cursor);
                row.put("startTime", formatted);
                row.put("suggestedStart", formatted);
                row.put("suggestedStartTime", formatted);
                if (teamEnabled) {
                    cursor = cursor.plusHours(parseDispatchHours(row.get("hours")));
                }
            }
            result.put(dispatchKey, dispatches);
        }
    }

    private int parseDispatchHours(Object raw) {
        if (raw == null) {
            return CozeConstants.DEFAULT_DISPATCH_HOURS;
        }
        String text = String.valueOf(raw).trim();
        if (!StringUtils.hasText(text) || "待定".equals(text) || "null".equals(text)) {
            return CozeConstants.DEFAULT_DISPATCH_HOURS;
        }
        String digits = text.replaceAll("[^0-9]", "");
        if (!StringUtils.hasText(digits)) {
            return CozeConstants.DEFAULT_DISPATCH_HOURS;
        }
        try {
            return Math.max(1, Integer.parseInt(digits));
        } catch (NumberFormatException ex) {
            return CozeConstants.DEFAULT_DISPATCH_HOURS;
        }
    }

    private LocalDateTime computeEarliestDispatchStart(LocalDate planDate, int staggerIndex) {
        LocalDate effectiveDate = planDate != null ? planDate : LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayShiftStart = effectiveDate.atTime(8, 0).plusHours(staggerIndex * (long) CozeConstants.DEFAULT_DISPATCH_HOURS);

        if (effectiveDate.isAfter(now.toLocalDate())) {
            return dayShiftStart;
        }
        if (effectiveDate.isBefore(now.toLocalDate())) {
            return roundUpToNextHalfHour(now).plusHours(staggerIndex * (long) CozeConstants.DEFAULT_DISPATCH_HOURS);
        }
        LocalDateTime earliestToday = roundUpToNextHalfHour(now);
        if (staggerIndex == 0) {
            return earliestToday.isAfter(dayShiftStart) ? earliestToday : dayShiftStart;
        }
        return earliestToday.plusHours(staggerIndex * (long) CozeConstants.DEFAULT_DISPATCH_HOURS);
    }

    private LocalDateTime roundUpToNextHalfHour(LocalDateTime time) {
        int minute = time.getMinute();
        if (minute % 30 == 0 && time.getSecond() == 0 && time.getNano() == 0) {
            return time;
        }
        int minutesToAdd = minute % 30 == 0 ? 30 : (30 - minute % 30);
        return time.plusMinutes(minutesToAdd).withSecond(0).withNano(0);
    }

    private String formatStartTime(LocalDateTime time) {
        return time.format(CozeConstants.SCHEDULING_TIME_FORMAT);
    }

    private Map<String, Object> constraintNoticeBottleneck(String processName, String suggestion) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("processName", processName);
        row.put("loadRate", 0);
        row.put("suggestion", suggestion);
        row.put("advice", suggestion);
        return row;
    }

    private boolean isMaterialBottleneck(Map<String, Object> row) {
        String text = String.join(" ",
                String.valueOf(row.getOrDefault("processName", "")),
                String.valueOf(row.getOrDefault("process", "")),
                String.valueOf(row.getOrDefault("name", "")),
                String.valueOf(row.getOrDefault("suggestion", "")),
                String.valueOf(row.getOrDefault("advice", "")),
                String.valueOf(row.getOrDefault("reason", "")));
        return containsMaterialReference(text);
    }

    private boolean containsMaterialReference(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("物料")
                || text.contains("缺料")
                || text.contains("库存")
                || text.contains("预警")
                || text.contains("安全库存")
                || text.contains("备料");
    }

    private String stripMaterialReferences(String text) {
        if (!StringUtils.hasText(text) || "null".equals(text)) {
            return "按交期与优先级排序（未纳入物料可用性约束）";
        }
        String[] segments = text.split("[。；;\\n]");
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            String trimmed = segment.trim();
            if (!StringUtils.hasText(trimmed) || containsMaterialReference(trimmed)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('。');
            }
            builder.append(trimmed);
        }
        if (builder.length() == 0) {
            return "按交期与优先级排序（未纳入物料可用性约束）";
        }
        return builder.toString();
    }

    private String appendTeamConstraintNote(String text) {
        if (!StringUtils.hasText(text) || "null".equals(text)) {
            return "按交期与优先级排序（未考虑班组工时）";
        }
        if (text.contains("班组工时") || text.contains("未考虑班组")) {
            return text;
        }
        return text + "（未考虑班组工时）";
    }

    private Map<String, Object> summarizeSchedulingResult(Map<String, Object> parsed) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("priorities", listSize(parsed.get("priorities")));
        summary.put("bottlenecks", listSize(parsed.get("bottlenecks")));
        Object dispatches = parsed.containsKey("dispatchSuggestions")
                ? parsed.get("dispatchSuggestions")
                : parsed.get("dispatches");
        summary.put("dispatches", listSize(dispatches));
        return summary;
    }

    private int listSize(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    private Map<String, Object> buildMockScheduling(
            List<ProdWorkOrder> workOrders,
            List<MatMaterial> warningMaterials,
            Map<String, Boolean> constraints,
            LocalDate planDate) {
        boolean materialEnabled = Boolean.TRUE.equals(constraints.get("materialAvailability"));
        boolean deviceEnabled = Boolean.TRUE.equals(constraints.get("deviceLoad"));
        boolean teamEnabled = Boolean.TRUE.equals(constraints.get("teamHours"));

        List<Map<String, Object>> priorities = new ArrayList<>();
        List<Map<String, Object>> dispatch = new ArrayList<>();
        for (int i = 0; i < workOrders.size(); i++) {
            ProdWorkOrder order = workOrders.get(i);
            Map<String, Object> priorityRow = new LinkedHashMap<>();
            priorityRow.put("workOrderCode", order.getOrderNo());
            priorityRow.put("rank", i + 1);
            priorityRow.put("priorityLabel", schedulingPriorityLabel(order.getPriority()));
            priorityRow.put("reason", buildMockPriorityReason(i, materialEnabled, teamEnabled, warningMaterials));
            priorities.add(priorityRow);

            Map<String, Object> dispatchRow = new LinkedHashMap<>();
            dispatchRow.put("workOrderCode", order.getOrderNo());
            dispatchRow.put("teamName", "待定");
            dispatchRow.put("startTime", formatStartTime(computeEarliestDispatchStart(planDate, i)));
            dispatchRow.put("hours", teamEnabled ? String.valueOf(4 + i) : "待定");
            dispatch.add(dispatchRow);
        }

        List<Map<String, Object>> bottlenecks = new ArrayList<>();
        if (deviceEnabled) {
            bottlenecks.add(Map.of(
                    "processName", "装配工序",
                    "loadRate", materialEnabled && !warningMaterials.isEmpty() ? 95 : 78,
                    "suggestion", materialEnabled && !warningMaterials.isEmpty()
                            ? "请优先处理缺料后再集中排装配任务"
                            : "当前负载可接受"
            ));
        } else {
            bottlenecks.add(Map.of(
                    "processName", "—",
                    "loadRate", 0,
                    "suggestion", "未启用设备负荷约束，已跳过瓶颈分析"
            ));
        }
        if (!materialEnabled) {
            bottlenecks.add(Map.of(
                    "processName", "物料约束",
                    "loadRate", 0,
                    "suggestion", "未启用物料可用性约束，排产未纳入缺料风险"
            ));
        }
        if (!teamEnabled) {
            bottlenecks.add(Map.of(
                    "processName", "班组约束",
                    "loadRate", 0,
                    "suggestion", "未启用班组工时约束，派工时间仅按默认班次估算"
            ));
        }

        return Map.of(
                "priorities", priorities,
                "bottlenecks", bottlenecks,
                "dispatches", dispatch
        );
    }

    private String buildMockPriorityReason(
            int index,
            boolean materialEnabled,
            boolean teamEnabled,
            List<MatMaterial> warningMaterials) {
        if (index == 0) {
            StringBuilder reason = new StringBuilder("交期较近且优先级更高");
            if (materialEnabled && !warningMaterials.isEmpty()) {
                reason.append("，并受物料预警影响");
            }
            if (!teamEnabled) {
                reason.append("（未考虑班组工时）");
            }
            return reason.toString();
        }
        if (!teamEnabled) {
            return "建议按当前队列顺序执行（未考虑班组工时）";
        }
        return "建议按当前队列顺序执行";
    }

    private String schedulingPriorityLabel(Integer priority) {
        if (priority == null || priority == 2) {
            return "中";
        }
        return priority == 1 ? "高" : "低";
    }


    public Map<String, Object> healthWorkflow() {
        if (!cozeConfigService.isConfigured()) {
            return Map.of("status", "skipped", "message", "未配置 API Token 或 Bot ID");
        }
        return testWorkflowHealth();
    }
}
