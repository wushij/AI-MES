package com.aimes.service;

import com.aimes.entity.DevDevice;
import com.aimes.entity.DevDeviceHistory;
import com.aimes.entity.DevInspectionRecord;
import com.aimes.entity.DevMaintenancePlan;
import com.aimes.entity.DevRepairOrder;
import com.aimes.entity.ExcEvent;
import com.aimes.entity.ProdProcessRecord;
import com.aimes.mapper.DevDeviceMapper;
import com.aimes.mapper.DevDeviceHistoryMapper;
import com.aimes.mapper.DevInspectionRecordMapper;
import com.aimes.mapper.DevMaintenancePlanMapper;
import com.aimes.mapper.DevRepairOrderMapper;
import com.aimes.mapper.ExcEventMapper;
import com.aimes.mapper.ProdProcessRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeviceRuntimeService {

    private static final Set<String> RUN_STATUSES = Set.of("running", "done");
    private static final Set<String> PAUSE_STATUSES = Set.of("paused");
    private static final Set<String> STOP_DEVICE_STATUSES = Set.of("fault", "repairing", "maintenance", "stopped", "paused");

    private final ProdProcessRecordMapper prodProcessRecordMapper;
    private final ExcEventMapper excEventMapper;
    private final DevRepairOrderMapper devRepairOrderMapper;
    private final DevInspectionRecordMapper devInspectionRecordMapper;
    private final DevMaintenancePlanMapper devMaintenancePlanMapper;
    private final DevDeviceMapper devDeviceMapper;
    private final DevDeviceHistoryMapper devDeviceHistoryMapper;

    public Map<String, Object> calcTodayStats(DevDevice device) {
        return calcTodayStats(device.getId(), device.getStatus());
    }

    public Map<String, Object> calcTodayStats(Long deviceId, String currentStatus) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayEnd = todayStart.plusDays(1);

        long runMinutes = 0;
        long pauseMinutes = 0;

        List<ProdProcessRecord> records = prodProcessRecordMapper.selectList(new LambdaQueryWrapper<ProdProcessRecord>()
                .eq(ProdProcessRecord::getDeviceId, deviceId)
                .isNotNull(ProdProcessRecord::getStartTime)
                .lt(ProdProcessRecord::getStartTime, todayEnd)
                .and(wrapper -> wrapper.isNull(ProdProcessRecord::getEndTime)
                        .or()
                        .gt(ProdProcessRecord::getEndTime, todayStart)));

        for (ProdProcessRecord record : records) {
            long minutes = overlapMinutes(record.getStartTime(), resolveEndTime(record, now, todayEnd), todayStart, todayEnd);
            if (minutes <= 0) {
                continue;
            }
            if (RUN_STATUSES.contains(record.getStatus())) {
                runMinutes += minutes;
            } else if (PAUSE_STATUSES.contains(record.getStatus())) {
                pauseMinutes += minutes;
            }
        }

        long repairStopMinutes = calcRepairStopMinutes(deviceId, todayStart, now, todayEnd);
        long statusStopMinutes = calcStatusStopMinutes(deviceId, currentStatus, todayStart, now);

        long stopMinutes = pauseMinutes + repairStopMinutes + statusStopMinutes;
        long totalActiveMinutes = runMinutes + stopMinutes;
        int utilizationRate = totalActiveMinutes > 0
                ? (int) Math.round(runMinutes * 100.0 / totalActiveMinutes)
                : 0;

        List<Map<String, Object>> todayAlerts = listTodayAlerts(deviceId, todayStart, todayEnd);
        todayAlerts.sort(Comparator.comparing(
                (Map<String, Object> alert) -> String.valueOf(alert.get("occurTime")),
                Comparator.nullsLast(Comparator.reverseOrder())));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("todayRunMinutes", runMinutes);
        stats.put("todayRunLabel", formatMinutes(runMinutes));
        stats.put("todayStopMinutes", stopMinutes);
        stats.put("todayStopLabel", formatMinutes(stopMinutes));
        stats.put("todayPauseMinutes", pauseMinutes);
        stats.put("todayRepairStopMinutes", repairStopMinutes);
        stats.put("todayStatusStopMinutes", statusStopMinutes);
        stats.put("utilizationRate", utilizationRate);
        stats.put("todayAlertCount", todayAlerts.size());
        stats.put("todayAlerts", todayAlerts);
        stats.put("dataSource", "prod_process_record");
        return stats;
    }

    public Map<Long, Map<String, Object>> calcTodayStatsBatch(Collection<DevDevice> devices) {
        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (DevDevice device : devices) {
            result.put(device.getId(), calcTodayStats(device));
        }
        return result;
    }

    public List<Map<String, Object>> listTodayAlerts(Long deviceId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return listTodayAlerts(deviceId, todayStart, todayStart.plusDays(1));
    }

    private List<Map<String, Object>> listTodayAlerts(Long deviceId, LocalDateTime todayStart, LocalDateTime todayEnd) {
        List<Map<String, Object>> alerts = new ArrayList<>();

        excEventMapper.selectList(new LambdaQueryWrapper<ExcEvent>()
                        .eq(ExcEvent::getDeviceId, deviceId)
                        .eq(ExcEvent::getEventType, "device")
                        .ge(ExcEvent::getOccurTime, todayStart)
                        .lt(ExcEvent::getOccurTime, todayEnd)
                        .orderByDesc(ExcEvent::getOccurTime))
                .forEach(event -> alerts.add(toAlertView("exception", event)));

        devRepairOrderMapper.selectList(new LambdaQueryWrapper<DevRepairOrder>()
                        .eq(DevRepairOrder::getDeviceId, deviceId)
                        .in(DevRepairOrder::getStatus, List.of("open", "processing"))
                        .orderByDesc(DevRepairOrder::getReportTime))
                .forEach(order -> alerts.add(toRepairAlertView(order)));

        devInspectionRecordMapper.selectList(new LambdaQueryWrapper<DevInspectionRecord>()
                        .eq(DevInspectionRecord::getDeviceId, deviceId)
                        .eq(DevInspectionRecord::getIsNormal, 0)
                        .ge(DevInspectionRecord::getInspectTime, todayStart)
                        .lt(DevInspectionRecord::getInspectTime, todayEnd)
                        .orderByDesc(DevInspectionRecord::getInspectTime))
                .forEach(record -> alerts.add(toInspectionAlertView(record)));

        LocalDate today = todayStart.toLocalDate();
        DevDevice device = devDeviceMapper.selectById(deviceId);
        LambdaQueryWrapper<DevMaintenancePlan> dueWrapper = new LambdaQueryWrapper<DevMaintenancePlan>()
                .eq(DevMaintenancePlan::getEnabled, 1)
                .isNotNull(DevMaintenancePlan::getNextDueDate)
                .le(DevMaintenancePlan::getNextDueDate, today)
                .and(wrapper -> {
                    wrapper.eq(DevMaintenancePlan::getDeviceId, deviceId);
                    if (device != null && device.getCategoryId() != null) {
                        wrapper.or(inner -> inner.isNull(DevMaintenancePlan::getDeviceId)
                                .eq(DevMaintenancePlan::getCategoryId, device.getCategoryId()));
                    }
                });
        devMaintenancePlanMapper.selectList(dueWrapper.orderByAsc(DevMaintenancePlan::getNextDueDate))
                .forEach(plan -> alerts.add(toMaintenanceDueAlertView(plan, deviceId)));

        return alerts;
    }

    private Map<String, Object> toMaintenanceDueAlertView(DevMaintenancePlan plan, Long deviceId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", plan.getId());
        row.put("alertType", "maintenance_due");
        row.put("alertTypeLabel", "保养到期");
        row.put("refNo", plan.getPlanCode());
        row.put("title", plan.getPlanName() + " 已到期，请安排保养");
        row.put("status", "due");
        row.put("statusLabel", "已到期");
        row.put("occurTime", plan.getNextDueDate() == null ? null : plan.getNextDueDate().atStartOfDay());
        row.put("source", "dev_maintenance_plan");
        row.put("sourceId", plan.getId());
        row.put("deviceId", deviceId);
        row.put("targetUrl", "/devices");
        return row;
    }

    private long calcRepairStopMinutes(Long deviceId, LocalDateTime todayStart, LocalDateTime now, LocalDateTime todayEnd) {
        long minutes = 0;
        List<DevRepairOrder> repairs = devRepairOrderMapper.selectList(new LambdaQueryWrapper<DevRepairOrder>()
                .eq(DevRepairOrder::getDeviceId, deviceId)
                .lt(DevRepairOrder::getReportTime, todayEnd)
                .and(wrapper -> wrapper.in(DevRepairOrder::getStatus, List.of("open", "processing"))
                        .or(inner -> inner.eq(DevRepairOrder::getStatus, "completed")
                                .isNotNull(DevRepairOrder::getEndTime)
                                .gt(DevRepairOrder::getEndTime, todayStart))));

        for (DevRepairOrder repair : repairs) {
            LocalDateTime start = repair.getReportTime().isBefore(todayStart) ? todayStart : repair.getReportTime();
            LocalDateTime end;
            if ("completed".equals(repair.getStatus()) && repair.getEndTime() != null) {
                end = repair.getEndTime().isAfter(todayEnd) ? todayEnd : repair.getEndTime();
            } else if ("open".equals(repair.getStatus()) || "processing".equals(repair.getStatus())) {
                end = now.isBefore(todayEnd) ? now : todayEnd;
            } else {
                continue;
            }
            minutes += overlapMinutes(start, end, todayStart, todayEnd);
        }
        return minutes;
    }

    private long calcStatusStopMinutes(Long deviceId, String currentStatus, LocalDateTime todayStart, LocalDateTime now) {
        if (!STOP_DEVICE_STATUSES.contains(currentStatus)) {
            return 0;
        }
        DevDeviceHistory lastStatusChange = devDeviceHistoryMapper.selectOne(new LambdaQueryWrapper<DevDeviceHistory>()
                .eq(DevDeviceHistory::getDeviceId, deviceId)
                .in(DevDeviceHistory::getActionType, List.of("status", "exception", "handle", "repair"))
                .ge(DevDeviceHistory::getCreateTime, todayStart)
                .orderByDesc(DevDeviceHistory::getCreateTime)
                .last("limit 1"));
        if (lastStatusChange == null) {
            return overlapMinutes(todayStart, now, todayStart, todayStart.plusDays(1));
        }
        return overlapMinutes(lastStatusChange.getCreateTime(), now, todayStart, todayStart.plusDays(1));
    }

    private LocalDateTime resolveEndTime(ProdProcessRecord record, LocalDateTime now, LocalDateTime todayEnd) {
        if (record.getEndTime() != null) {
            return record.getEndTime();
        }
        if ("running".equals(record.getStatus())) {
            return now.isBefore(todayEnd) ? now : todayEnd;
        }
        return null;
    }

    private long overlapMinutes(LocalDateTime start, LocalDateTime end, LocalDateTime windowStart, LocalDateTime windowEnd) {
        if (start == null || end == null || !end.isAfter(start)) {
            return 0;
        }
        LocalDateTime effectiveStart = start.isBefore(windowStart) ? windowStart : start;
        LocalDateTime effectiveEnd = end.isAfter(windowEnd) ? windowEnd : end;
        if (!effectiveEnd.isAfter(effectiveStart)) {
            return 0;
        }
        return Duration.between(effectiveStart, effectiveEnd).toMinutes();
    }

    private Map<String, Object> toAlertView(String alertType, ExcEvent event) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", event.getId());
        row.put("alertType", alertType);
        row.put("alertTypeLabel", "设备异常");
        row.put("refNo", event.getEventNo());
        row.put("title", event.getDescription());
        row.put("status", event.getStatus());
        row.put("statusLabel", exceptionStatusLabel(event.getStatus()));
        row.put("occurTime", event.getOccurTime());
        row.put("source", "exc_event");
        row.put("sourceId", event.getId());
        row.put("targetUrl", "/exceptions");
        return row;
    }

    private Map<String, Object> toRepairAlertView(DevRepairOrder order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("alertType", "repair");
        row.put("alertTypeLabel", "维修待处理");
        row.put("refNo", order.getRepairNo());
        row.put("title", order.getFaultReason());
        row.put("status", order.getStatus());
        row.put("statusLabel", repairStatusLabel(order.getStatus()));
        row.put("occurTime", order.getReportTime());
        row.put("source", "dev_repair_order");
        row.put("sourceId", order.getId());
        row.put("targetUrl", "/devices");
        return row;
    }

    private Map<String, Object> toInspectionAlertView(DevInspectionRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", record.getId());
        row.put("alertType", "inspection");
        row.put("alertTypeLabel", "点检异常");
        row.put("refNo", record.getRecordNo());
        row.put("title", record.getPlanName() != null ? record.getPlanName() + " 点检异常" : "点检发现异常");
        row.put("status", "abnormal");
        row.put("statusLabel", "异常");
        row.put("occurTime", record.getInspectTime());
        row.put("source", "dev_inspection_record");
        row.put("sourceId", record.getId());
        row.put("targetUrl", "/devices");
        return row;
    }

    private String exceptionStatusLabel(String status) {
        return switch (status) {
            case "open" -> "待处理";
            case "processing" -> "处理中";
            case "closed" -> "已关闭";
            default -> status;
        };
    }

    private String repairStatusLabel(String status) {
        return switch (status) {
            case "open" -> "待维修";
            case "processing" -> "维修中";
            case "completed" -> "已完成";
            default -> status;
        };
    }

    public static String formatMinutes(long minutes) {
        if (minutes <= 0) {
            return "0分";
        }
        long hours = minutes / 60;
        long remain = minutes % 60;
        if (hours > 0 && remain > 0) {
            return hours + "小时" + remain + "分";
        }
        if (hours > 0) {
            return hours + "小时";
        }
        return remain + "分";
    }
}
