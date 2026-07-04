package com.aimes.service;

import com.aimes.entity.DevDevice;
import com.aimes.entity.ExcEvent;
import com.aimes.entity.MatMaterial;
import com.aimes.entity.ProdPlan;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.mapper.DevDeviceMapper;
import com.aimes.mapper.ExcEventMapper;
import com.aimes.mapper.MatMaterialMapper;
import com.aimes.mapper.ProdPlanMapper;
import com.aimes.mapper.ProdTeamMapper;
import com.aimes.mapper.ProdWorkOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProdPlanMapper prodPlanMapper;
    private final ProdWorkOrderMapper prodWorkOrderMapper;
    private final ExcEventMapper excEventMapper;
    private final MatMaterialMapper matMaterialMapper;
    private final ProdTeamMapper prodTeamMapper;
    private final DeviceService deviceService;
    private final DevDeviceMapper devDeviceMapper;

    public Map<String, Object> stats() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime yesterdayStart = yesterday.atStartOfDay();

        long todayPlans = prodPlanMapper.selectCount(new LambdaQueryWrapper<ProdPlan>()
                .eq(ProdPlan::getPlanDate, today));
        long yesterdayPlans = prodPlanMapper.selectCount(new LambdaQueryWrapper<ProdPlan>()
                .eq(ProdPlan::getPlanDate, yesterday));

        List<String> activeStatuses = List.of("assigned", "producing", "exception");
        long activeOrders = prodWorkOrderMapper.selectCount(new LambdaQueryWrapper<ProdWorkOrder>()
                .in(ProdWorkOrder::getStatus, activeStatuses));
        long todayNewInProgress = prodWorkOrderMapper.selectCount(new LambdaQueryWrapper<ProdWorkOrder>()
                .in(ProdWorkOrder::getStatus, activeStatuses)
                .ge(ProdWorkOrder::getCreatedTime, todayStart)
                .lt(ProdWorkOrder::getCreatedTime, tomorrowStart));
        long yesterdayNewInProgress = prodWorkOrderMapper.selectCount(new LambdaQueryWrapper<ProdWorkOrder>()
                .in(ProdWorkOrder::getStatus, activeStatuses)
                .ge(ProdWorkOrder::getCreatedTime, yesterdayStart)
                .lt(ProdWorkOrder::getCreatedTime, todayStart));

        long openExceptions = excEventMapper.selectCount(new LambdaQueryWrapper<ExcEvent>()
                .in(ExcEvent::getStatus, List.of("open", "processing")));
        long newExceptions = excEventMapper.selectCount(new LambdaQueryWrapper<ExcEvent>()
                .ge(ExcEvent::getOccurTime, todayStart)
                .lt(ExcEvent::getOccurTime, tomorrowStart));

        long materialAlerts = matMaterialMapper.selectCount(new LambdaQueryWrapper<MatMaterial>()
                .eq(MatMaterial::getAlertStatus, "warning"));
        long newMaterialAlerts = matMaterialMapper.selectCount(new LambdaQueryWrapper<MatMaterial>()
                .eq(MatMaterial::getAlertStatus, "warning")
                .ge(MatMaterial::getUpdatedTime, todayStart)
                .lt(MatMaterial::getUpdatedTime, tomorrowStart));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("planCount", todayPlans);
        stats.put("planTrend", formatTrend(todayPlans, yesterdayPlans));
        stats.put("inProgressWorkOrderCount", activeOrders);
        stats.put("inProgressTrend", formatTrend(todayNewInProgress, yesterdayNewInProgress));
        stats.put("openExceptionCount", openExceptions);
        stats.put("newExceptionCount", newExceptions);
        stats.put("materialAlertCount", materialAlerts);
        stats.put("newMaterialAlertCount", newMaterialAlerts);
        stats.put("outputTrend", getOutputTrend());

        Map<String, Object> deviceSummary = deviceService.summary();
        stats.put("deviceTotalCount", deviceSummary.get("totalCount"));
        stats.put("deviceRunningCount", deviceSummary.get("runningCount"));
        stats.put("deviceFaultCount", deviceSummary.get("faultCount"));
        stats.put("deviceTodayAlertCount", deviceSummary.get("todayAlertCount"));
        return stats;
    }

    public List<Map<String, Object>> progress() {
        return prodTeamMapper.selectList(null).stream().map(team -> {
            List<ProdWorkOrder> teamOrders = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                    .eq(ProdWorkOrder::getTeamId, team.getId()));
            int total = teamOrders.size();
            int completed = teamOrders.stream().mapToInt(order -> order.getProgress() == null ? 0 : order.getProgress()).sum();
            int avgProgress = total == 0 ? 0 : completed / total;
            long producing = teamOrders.stream().filter(order -> "producing".equals(order.getStatus())).count();
            long done = teamOrders.stream().filter(order -> "done".equals(order.getStatus())).count();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("teamId", team.getId());
            row.put("teamCode", team.getTeamCode());
            row.put("teamName", team.getTeamName());
            row.put("lineName", team.getLineName());
            row.put("avgProgress", avgProgress);
            row.put("producingCount", producing);
            row.put("doneCount", done);
            row.put("totalCount", total);
            return row;
        }).toList();
    }

    public Map<String, Object> deviceSummary() {
        return deviceService.summary();
    }

    public Map<String, Object> alerts() {
        List<Map<String, Object>> latestExceptions = excEventMapper.selectList(new LambdaQueryWrapper<ExcEvent>()
                        .last("ORDER BY FIELD(status, 'open', 'processing', 'closed'), occur_time DESC limit 5"))
                .stream()
                .map(event -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", event.getId());
                    row.put("eventNo", event.getEventNo());
                    row.put("eventType", event.getEventType());
                    row.put("workOrderId", event.getWorkOrderId());
                    row.put("status", event.getStatus());
                    row.put("occurTime", event.getOccurTime());
                    row.put("description", event.getDescription());
                    row.put("deviceId", event.getDeviceId());
                    if (event.getDeviceId() != null) {
                        DevDevice device = devDeviceMapper.selectById(event.getDeviceId());
                        if (device != null) {
                            row.put("deviceCode", device.getDeviceCode());
                            row.put("deviceName", device.getDeviceName());
                        }
                    }
                    return row;
                })
                .toList();

        List<Map<String, Object>> materialAlerts = matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                        .eq(MatMaterial::getAlertStatus, "warning")
                        .orderByAsc(MatMaterial::getStockQty)
                        .last("limit 5"))
                .stream()
                .map(material -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", material.getId());
                    row.put("materialCode", material.getMaterialCode());
                    row.put("materialName", material.getMaterialName());
                    row.put("stockQty", material.getStockQty());
                    row.put("safetyStock", material.getSafetyStock());
                    row.put("gap", material.getSafetyStock().subtract(material.getStockQty()).max(BigDecimal.ZERO));
                    row.put("unit", material.getUnit());
                    row.put("status", material.getAlertStatus());
                    return row;
                })
                .toList();

        return Map.of(
                "exceptions", latestExceptions,
                "materials", materialAlerts,
                "devices", deviceService.summary().get("devices")
        );
    }

    public Map<String, Object> workshopSummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        long total = prodWorkOrderMapper.selectCount(null);
        long done = prodWorkOrderMapper.selectCount(new LambdaQueryWrapper<ProdWorkOrder>()
                .eq(ProdWorkOrder::getStatus, "done"));
        // 今日实际完成的工单列表
        List<ProdWorkOrder> todayDoneOrders = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                .eq(ProdWorkOrder::getStatus, "done")
                .ge(ProdWorkOrder::getUpdatedTime, todayStart)
                .lt(ProdWorkOrder::getUpdatedTime, tomorrowStart));
        long todayDone = todayDoneOrders.size();
        int completionRate = total == 0 ? 0 : (int) (done * 100 / total);

        // 统计其中准时完成的工单数
        long onTimeDone = todayDoneOrders.stream()
                .filter(order -> order.getDeadline() == null || !order.getUpdatedTime().isAfter(order.getDeadline()))
                .count();
        int onTimeRate = todayDone == 0 ? 0 : (int) (onTimeDone * 100 / todayDone);
        List<String> runningStatuses = List.of("assigned", "producing", "exception");
        long activeLines = prodTeamMapper.selectList(null).stream()
                .filter(team -> prodWorkOrderMapper.selectCount(new LambdaQueryWrapper<ProdWorkOrder>()
                        .eq(ProdWorkOrder::getTeamId, team.getId())
                        .in(ProdWorkOrder::getStatus, runningStatuses)) > 0)
                .map(team -> team.getLineName() == null ? team.getTeamName() : team.getLineName())
                .distinct()
                .count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("completionRate", completionRate);
        summary.put("completedOrders", done);
        summary.put("totalOrders", total);
        summary.put("lineName", "装配产线");
        summary.put("shiftName", "今日班次");
        summary.put("todayOutput", todayDone);
        summary.put("onTimeRate", onTimeRate);
        summary.put("activeLines", activeLines);
        return summary;
    }

    private String formatTrend(long current, long previous) {
        if (previous == 0) {
            return current == 0 ? "0%" : "+100%";
        }
        long percent = Math.round((current - previous) * 100.0 / previous);
        if (percent > 0) {
            return "+" + percent + "%";
        }
        if (percent < 0) {
            return percent + "%";
        }
        return "0%";
    }

    private List<Map<String, Object>> getOutputTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            LocalDateTime start = d.atStartOfDay();
            LocalDateTime end = d.plusDays(1).atStartOfDay();
            long count = prodWorkOrderMapper.selectCount(new LambdaQueryWrapper<ProdWorkOrder>()
                    .eq(ProdWorkOrder::getStatus, "done")
                    .ge(ProdWorkOrder::getUpdatedTime, start)
                    .lt(ProdWorkOrder::getUpdatedTime, end));
            
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", d.getMonthValue() + "/" + d.getDayOfMonth());
            point.put("outputQty", count);
            trend.add(point);
        }
        return trend;
    }
}
