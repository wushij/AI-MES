package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.DeviceSaveRequest;
import com.aimes.dto.Requests.DeviceStatusRequest;
import com.aimes.entity.DevDevice;
import com.aimes.entity.DevDeviceHistory;
import com.aimes.entity.ExcEvent;
import com.aimes.entity.ProdTeam;
import com.aimes.entity.SysUser;
import com.aimes.mapper.DevDeviceHistoryMapper;
import com.aimes.mapper.DevDeviceMapper;
import com.aimes.mapper.ExcEventMapper;
import com.aimes.mapper.ProdTeamMapper;
import com.aimes.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final Set<String> VALID_STATUSES = Set.of(
            "idle", "running", "paused", "stopped", "maintenance", "repairing", "fault", "scrapped"
    );

    private final DevDeviceMapper devDeviceMapper;
    private final DevDeviceHistoryMapper devDeviceHistoryMapper;
    private final ProdTeamMapper prodTeamMapper;
    private final SysUserMapper sysUserMapper;
    private final ExcEventMapper excEventMapper;
    private final DeviceCategoryService deviceCategoryService;
    private final AuthService authService;

    public List<Map<String, Object>> list(String keyword, String status, Long categoryId) {
        LambdaQueryWrapper<DevDevice> wrapper = new LambdaQueryWrapper<DevDevice>()
                .and(StringUtils.hasText(keyword), w -> w.like(DevDevice::getDeviceCode, keyword)
                        .or()
                        .like(DevDevice::getDeviceName, keyword)
                        .or()
                        .like(DevDevice::getLineName, keyword)
                        .or()
                        .like(DevDevice::getWorkshop, keyword))
                .eq(StringUtils.hasText(status), DevDevice::getStatus, status)
                .eq(categoryId != null, DevDevice::getCategoryId, categoryId)
                .orderByAsc(DevDevice::getDeviceCode);
        return devDeviceMapper.selectList(wrapper).stream().map(this::toView).toList();
    }

    public List<Map<String, Object>> options() {
        return devDeviceMapper.selectList(new LambdaQueryWrapper<DevDevice>()
                        .ne(DevDevice::getStatus, "scrapped")
                        .orderByAsc(DevDevice::getDeviceCode))
                .stream()
                .map(device -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", device.getId());
                    row.put("deviceCode", device.getDeviceCode());
                    row.put("deviceName", device.getDeviceName());
                    row.put("lineName", device.getLineName() == null ? "" : device.getLineName());
                    row.put("status", device.getStatus() == null ? "idle" : device.getStatus());
                    row.put("statusLabel", statusLabel(device.getStatus()));
                    row.put("selectable", !"scrapped".equals(device.getStatus()));
                    return row;
                })
                .toList();
    }

    public Map<String, Object> formOptions() {
        List<Map<String, Object>> teams = prodTeamMapper.selectList(new LambdaQueryWrapper<ProdTeam>()
                        .orderByAsc(ProdTeam::getId))
                .stream()
                .map(team -> Map.<String, Object>of("id", team.getId(), "teamName", team.getTeamName()))
                .toList();
        List<Map<String, Object>> managers = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, 1)
                        .orderByAsc(SysUser::getId))
                .stream()
                .map(user -> Map.<String, Object>of("id", user.getId(), "realName", user.getRealName(), "role", user.getRole()))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", deviceCategoryService.listFlat());
        result.put("teams", teams);
        result.put("managers", managers);
        result.put("statuses", statusOptions());
        return result;
    }

    public Map<String, Object> detail(Long id) {
        return toView(getDevice(id));
    }

    public Map<String, Object> detailFull(Long id) {
        DevDevice device = getDevice(id);
        Map<String, Object> view = toView(device);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);

        long todayAlerts = excEventMapper.selectCount(new LambdaQueryWrapper<ExcEvent>()
                .eq(ExcEvent::getDeviceId, id)
                .eq(ExcEvent::getEventType, "device")
                .ge(ExcEvent::getOccurTime, todayStart)
                .lt(ExcEvent::getOccurTime, tomorrowStart));
        long openExceptions = excEventMapper.selectCount(new LambdaQueryWrapper<ExcEvent>()
                .eq(ExcEvent::getDeviceId, id)
                .in(ExcEvent::getStatus, List.of("open", "processing")));

        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("todayAlertCount", todayAlerts);
        runtime.put("openExceptionCount", openExceptions);
        runtime.put("statusLabel", statusLabel(device.getStatus()));
        view.put("runtime", runtime);

        List<Map<String, Object>> exceptions = excEventMapper.selectList(new LambdaQueryWrapper<ExcEvent>()
                        .eq(ExcEvent::getDeviceId, id)
                        .orderByDesc(ExcEvent::getOccurTime)
                        .last("limit 20"))
                .stream()
                .map(this::toExceptionBrief)
                .toList();
        view.put("exceptions", exceptions);

        List<Map<String, Object>> history = devDeviceHistoryMapper.selectList(new LambdaQueryWrapper<DevDeviceHistory>()
                        .eq(DevDeviceHistory::getDeviceId, id)
                        .orderByDesc(DevDeviceHistory::getCreateTime)
                        .last("limit 50"))
                .stream()
                .map(this::toHistoryView)
                .toList();
        view.put("history", history);
        return view;
    }

    public Map<String, Object> summary() {
        List<DevDevice> devices = devDeviceMapper.selectList(null);
        long total = devices.size();
        long running = devices.stream().filter(d -> "running".equals(d.getStatus())).count();
        long idle = devices.stream().filter(d -> "idle".equals(d.getStatus())).count();
        long fault = devices.stream().filter(d -> "fault".equals(d.getStatus()) || "repairing".equals(d.getStatus())).count();
        long maintenance = devices.stream().filter(d -> "maintenance".equals(d.getStatus())).count();
        long scrapped = devices.stream().filter(d -> "scrapped".equals(d.getStatus())).count();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayDeviceAlerts = excEventMapper.selectCount(new LambdaQueryWrapper<ExcEvent>()
                .eq(ExcEvent::getEventType, "device")
                .ge(ExcEvent::getOccurTime, todayStart));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCount", total);
        summary.put("runningCount", running);
        summary.put("idleCount", idle);
        summary.put("faultCount", fault);
        summary.put("maintenanceCount", maintenance);
        summary.put("scrappedCount", scrapped);
        summary.put("todayAlertCount", todayDeviceAlerts);
        summary.put("devices", devices.stream().map(this::toBriefView).toList());
        return summary;
    }

    public List<Map<String, Object>> schedulingLoads() {
        return devDeviceMapper.selectList(new LambdaQueryWrapper<DevDevice>()
                        .ne(DevDevice::getStatus, "scrapped")
                        .orderByAsc(DevDevice::getDeviceCode))
                .stream()
                .map(device -> {
                    long openExceptions = excEventMapper.selectCount(new LambdaQueryWrapper<ExcEvent>()
                            .eq(ExcEvent::getDeviceId, device.getId())
                            .in(ExcEvent::getStatus, List.of("open", "processing")));
                    int loadRate = switch (device.getStatus() == null ? "idle" : device.getStatus()) {
                        case "running" -> 85;
                        case "fault", "repairing" -> 100;
                        case "maintenance", "stopped", "paused" -> 70;
                        default -> openExceptions > 0 ? 60 : 20;
                    };
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", device.getId());
                    row.put("deviceCode", device.getDeviceCode());
                    row.put("deviceName", device.getDeviceName());
                    row.put("lineName", device.getLineName());
                    row.put("workshop", device.getWorkshop());
                    row.put("status", device.getStatus());
                    row.put("statusLabel", statusLabel(device.getStatus()));
                    row.put("openExceptionCount", openExceptions);
                    row.put("loadRate", Math.min(100, loadRate));
                    row.put("available", List.of("idle", "running").contains(device.getStatus()) && openExceptions == 0);
                    return row;
                })
                .toList();
    }

    @Transactional
    public Map<String, Object> create(DeviceSaveRequest request) {
        DevDevice device = new DevDevice();
        applyRequest(device, request);
        device.setDeviceCode(StringUtils.hasText(request.getDeviceCode()) ? request.getDeviceCode() : nextDeviceCode());
        ensureUniqueCode(device.getDeviceCode(), null);
        device.setCreatedTime(LocalDateTime.now());
        device.setUpdatedTime(LocalDateTime.now());
        devDeviceMapper.insert(device);
        recordHistory(device.getId(), "create", "新建设备台账", currentOperatorId(), currentOperatorName(), null, null, device.getStatus());
        return toView(device);
    }

    @Transactional
    public Map<String, Object> update(Long id, DeviceSaveRequest request) {
        DevDevice device = getDevice(id);
        String oldStatus = device.getStatus();
        String changeDesc = buildDeviceUpdateDesc(device, request);
        applyRequest(device, request);
        if (StringUtils.hasText(request.getDeviceCode())) {
            ensureUniqueCode(request.getDeviceCode(), id);
            device.setDeviceCode(request.getDeviceCode());
        }
        device.setUpdatedTime(LocalDateTime.now());
        devDeviceMapper.updateById(device);
        boolean statusChanged = request.getStatus() != null && !request.getStatus().equals(oldStatus);
        if (statusChanged) {
            String desc = StringUtils.hasText(changeDesc) ? changeDesc + "；编辑设备时更新状态" : "编辑设备时更新状态";
            recordHistory(device.getId(), "status", desc, currentOperatorId(), currentOperatorName(), null, oldStatus, device.getStatus());
        } else if (StringUtils.hasText(changeDesc)) {
            recordHistory(device.getId(), "update", changeDesc, currentOperatorId(), currentOperatorName(), null, null, null);
        } else {
            recordHistory(device.getId(), "update", "更新设备资料", currentOperatorId(), currentOperatorName(), null, null, null);
        }
        return toView(device);
    }

    @Transactional
    public Map<String, Object> updateStatus(Long id, DeviceStatusRequest request) {
        DevDevice device = getDevice(id);
        validateStatus(request.getStatus());
        if ("scrapped".equals(device.getStatus())) {
            throw new BusinessException("已报废设备不能变更状态");
        }
        String oldStatus = device.getStatus();
        device.setStatus(request.getStatus());
        if (StringUtils.hasText(request.getRemark())) {
            device.setRemark(request.getRemark());
        }
        device.setUpdatedTime(LocalDateTime.now());
        devDeviceMapper.updateById(device);
        String desc = "手动变更设备状态";
        if (StringUtils.hasText(request.getRemark())) {
            desc += "（" + request.getRemark().trim() + "）";
        }
        recordHistory(device.getId(), "status", desc, currentOperatorId(), currentOperatorName(), null, oldStatus, device.getStatus());
        return toView(device);
    }

    @Transactional
    public void delete(Long id) {
        getDevice(id);
        long related = excEventMapper.selectCount(new LambdaQueryWrapper<ExcEvent>()
                .eq(ExcEvent::getDeviceId, id));
        if (related > 0) {
            throw new BusinessException("设备已关联异常记录，不能删除");
        }
        devDeviceMapper.deleteById(id);
    }

    @Transactional
    public void onExceptionReported(Long deviceId, Long eventId, Long operatorId, String operatorName) {
        DevDevice device = getDevice(deviceId);
        if ("scrapped".equals(device.getStatus())) {
            throw new BusinessException("设备已报废，不能关联异常");
        }
        String oldStatus = device.getStatus();
        device.setStatus("fault");
        device.setUpdatedTime(LocalDateTime.now());
        devDeviceMapper.updateById(device);
        recordHistory(device.getId(), "exception", "设备异常上报", operatorId, operatorName, eventId, oldStatus, "fault");
    }

    @Transactional
    public void onExceptionHandled(Long deviceId, Long eventId, boolean recovered, Long operatorId, String operatorName) {
        if (deviceId == null) {
            return;
        }
        DevDevice device = devDeviceMapper.selectById(deviceId);
        if (device == null) {
            return;
        }
        long openCount = excEventMapper.selectCount(new LambdaQueryWrapper<ExcEvent>()
                .eq(ExcEvent::getDeviceId, deviceId)
                .in(ExcEvent::getStatus, List.of("open", "processing"))
                .ne(eventId != null, ExcEvent::getId, eventId));
        if (openCount > 0) {
            return;
        }
        String oldStatus = device.getStatus();
        String newStatus = recovered ? "idle" : "repairing";
        device.setStatus(newStatus);
        device.setUpdatedTime(LocalDateTime.now());
        devDeviceMapper.updateById(device);
        recordHistory(device.getId(), "handle", recovered ? "异常处理完成，设备恢复待命" : "异常处理中，设备进入维修", operatorId, operatorName, eventId, oldStatus, newStatus);
    }

    public void validateDeviceForException(Long deviceId, String eventType) {
        if ("device".equals(eventType)) {
            if (deviceId == null) {
                throw new BusinessException("设备停机异常必须选择关联设备");
            }
        }
        if (deviceId == null) {
            return;
        }
        DevDevice device = devDeviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("关联设备不存在");
        }
        if ("scrapped".equals(device.getStatus())) {
            throw new BusinessException("不能关联已报废设备");
        }
    }

    private void applyRequest(DevDevice device, DeviceSaveRequest request) {
        device.setDeviceName(request.getDeviceName());
        device.setCategoryId(request.getCategoryId());
        device.setDeviceType(request.getDeviceType());
        device.setBrand(request.getBrand());
        device.setModel(request.getModel());
        device.setSerialNumber(request.getSerialNumber());
        device.setWorkshop(request.getWorkshop());
        device.setLineName(request.getLineName());
        device.setStation(request.getStation());
        device.setManagerId(request.getManagerId());
        device.setPurchaseDate(request.getPurchaseDate());
        device.setInstallDate(request.getInstallDate());
        device.setEnableDate(request.getEnableDate());
        device.setWarrantyDate(request.getWarrantyDate());
        if (StringUtils.hasText(request.getStatus())) {
            validateStatus(request.getStatus());
            device.setStatus(request.getStatus());
        } else if (device.getStatus() == null) {
            device.setStatus("idle");
        }
        device.setTeamId(request.getTeamId());
        device.setRemark(request.getRemark());
        if (request.getCategoryId() != null) {
            device.setDeviceType(deviceCategoryService.resolveCategoryName(request.getCategoryId()));
        }
    }

    public DevDevice getDevice(Long id) {
        DevDevice device = devDeviceMapper.selectById(id);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        return device;
    }

    private void ensureUniqueCode(String code, Long excludeId) {
        DevDevice existing = devDeviceMapper.selectOne(new LambdaQueryWrapper<DevDevice>()
                .eq(DevDevice::getDeviceCode, code)
                .ne(excludeId != null, DevDevice::getId, excludeId)
                .last("limit 1"));
        if (existing != null) {
            throw new BusinessException("设备编号已存在");
        }
    }

    private String nextDeviceCode() {
        long count = devDeviceMapper.selectCount(null) + 1;
        return "DEV-" + String.format("%03d", count);
    }

    private void validateStatus(String status) {
        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException("无效的设备状态");
        }
    }

    private String buildDeviceUpdateDesc(DevDevice before, DeviceSaveRequest request) {
        List<String> changes = new ArrayList<>();
        if (StringUtils.hasText(request.getDeviceCode())) {
            appendTextChange(changes, "设备编号", before.getDeviceCode(), request.getDeviceCode());
        }
        appendTextChange(changes, "设备名称", before.getDeviceName(), request.getDeviceName());
        if (!Objects.equals(before.getCategoryId(), request.getCategoryId())) {
            changes.add("分类：" + displayCategory(before.getCategoryId()) + " → " + displayCategory(request.getCategoryId()));
        }
        appendTextChange(changes, "品牌", before.getBrand(), request.getBrand());
        appendTextChange(changes, "型号", before.getModel(), request.getModel());
        appendTextChange(changes, "序列号", before.getSerialNumber(), request.getSerialNumber());
        appendTextChange(changes, "车间", before.getWorkshop(), request.getWorkshop());
        appendTextChange(changes, "产线", before.getLineName(), request.getLineName());
        appendTextChange(changes, "工位", before.getStation(), request.getStation());
        if (!Objects.equals(before.getManagerId(), request.getManagerId())) {
            changes.add("负责人：" + displayUser(before.getManagerId()) + " → " + displayUser(request.getManagerId()));
        }
        if (!Objects.equals(before.getTeamId(), request.getTeamId())) {
            changes.add("责任班组：" + displayTeam(before.getTeamId()) + " → " + displayTeam(request.getTeamId()));
        }
        appendDateChange(changes, "启用日期", before.getEnableDate(), request.getEnableDate());
        appendDateChange(changes, "保修截止", before.getWarrantyDate(), request.getWarrantyDate());
        appendTextChange(changes, "备注", before.getRemark(), request.getRemark());
        return String.join("；", changes);
    }

    private void appendTextChange(List<String> changes, String label, String oldValue, String newValue) {
        if (Objects.equals(normalizeText(oldValue), normalizeText(newValue))) {
            return;
        }
        changes.add(label + "：" + displayText(oldValue) + " → " + displayText(newValue));
    }

    private void appendDateChange(List<String> changes, String label, LocalDate oldValue, LocalDate newValue) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        changes.add(label + "：" + displayDate(oldValue) + " → " + displayDate(newValue));
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String displayText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "空";
    }

    private String displayDate(LocalDate value) {
        return value == null ? "空" : value.toString();
    }

    private String displayCategory(Long categoryId) {
        if (categoryId == null) {
            return "空";
        }
        String name = deviceCategoryService.resolveCategoryName(categoryId);
        return StringUtils.hasText(name) ? name : String.valueOf(categoryId);
    }

    private String displayUser(Long userId) {
        if (userId == null) {
            return "空";
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getRealName())) {
            return String.valueOf(userId);
        }
        return user.getRealName();
    }

    private String displayTeam(Long teamId) {
        if (teamId == null) {
            return "空";
        }
        ProdTeam team = prodTeamMapper.selectById(teamId);
        if (team == null || !StringUtils.hasText(team.getTeamName())) {
            return String.valueOf(teamId);
        }
        return team.getTeamName();
    }

    private void recordHistory(Long deviceId, String actionType, String actionDesc, Long operatorId, String operatorName,
                               Long relatedEventId, String beforeValue, String afterValue) {
        DevDeviceHistory history = new DevDeviceHistory();
        history.setDeviceId(deviceId);
        history.setActionType(actionType);
        history.setActionDesc(actionDesc);
        history.setOperatorId(operatorId);
        history.setOperatorName(operatorName);
        history.setRelatedEventId(relatedEventId);
        history.setBeforeValue(beforeValue);
        history.setAfterValue(afterValue);
        history.setCreateTime(LocalDateTime.now());
        devDeviceHistoryMapper.insert(history);
    }

    private Long currentOperatorId() {
        try {
            return authService.currentUser().getId();
        } catch (Exception ex) {
            return null;
        }
    }

    private String currentOperatorName() {
        try {
            return authService.currentUser().getRealName();
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> toView(DevDevice device) {
        ProdTeam team = device.getTeamId() == null ? null : prodTeamMapper.selectById(device.getTeamId());
        SysUser manager = device.getManagerId() == null ? null : sysUserMapper.selectById(device.getManagerId());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", device.getId());
        view.put("deviceCode", device.getDeviceCode());
        view.put("deviceName", device.getDeviceName());
        view.put("categoryId", device.getCategoryId());
        view.put("categoryName", deviceCategoryService.resolveCategoryName(device.getCategoryId()));
        view.put("deviceType", device.getDeviceType());
        view.put("brand", device.getBrand());
        view.put("model", device.getModel());
        view.put("serialNumber", device.getSerialNumber());
        view.put("workshop", device.getWorkshop());
        view.put("lineName", device.getLineName());
        view.put("station", device.getStation());
        view.put("managerId", device.getManagerId());
        view.put("managerName", manager == null ? null : manager.getRealName());
        view.put("purchaseDate", device.getPurchaseDate());
        view.put("installDate", device.getInstallDate());
        view.put("enableDate", device.getEnableDate());
        view.put("warrantyDate", device.getWarrantyDate());
        view.put("status", device.getStatus());
        view.put("statusLabel", statusLabel(device.getStatus()));
        view.put("teamId", device.getTeamId());
        view.put("teamName", team == null ? null : team.getTeamName());
        view.put("remark", device.getRemark());
        view.put("createdTime", device.getCreatedTime());
        view.put("updatedTime", device.getUpdatedTime());
        return view;
    }

    private Map<String, Object> toBriefView(DevDevice device) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", device.getId());
        view.put("deviceCode", device.getDeviceCode());
        view.put("deviceName", device.getDeviceName());
        view.put("lineName", device.getLineName());
        view.put("workshop", device.getWorkshop());
        view.put("status", device.getStatus());
        view.put("statusLabel", statusLabel(device.getStatus()));
        return view;
    }

    private Map<String, Object> toExceptionBrief(ExcEvent event) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", event.getId());
        row.put("eventNo", event.getEventNo());
        row.put("eventType", event.getEventType());
        row.put("description", event.getDescription());
        row.put("status", event.getStatus());
        row.put("occurTime", event.getOccurTime());
        row.put("handleResult", event.getHandleResult());
        return row;
    }

    private Map<String, Object> toHistoryView(DevDeviceHistory history) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", history.getId());
        row.put("actionType", history.getActionType());
        row.put("actionDesc", history.getActionDesc());
        row.put("operatorName", history.getOperatorName());
        row.put("beforeValue", history.getBeforeValue());
        row.put("afterValue", history.getAfterValue());
        row.put("relatedEventId", history.getRelatedEventId());
        row.put("createTime", history.getCreateTime());
        row.put("beforeLabel", history.getBeforeValue() != null ? statusLabel(history.getBeforeValue()) : null);
        row.put("afterLabel", history.getAfterValue() != null ? statusLabel(history.getAfterValue()) : null);
        return row;
    }

    private List<Map<String, String>> statusOptions() {
        return List.of(
                Map.of("value", "idle", "label", "空闲"),
                Map.of("value", "running", "label", "运行中"),
                Map.of("value", "paused", "label", "暂停"),
                Map.of("value", "stopped", "label", "停机"),
                Map.of("value", "maintenance", "label", "保养中"),
                Map.of("value", "repairing", "label", "维修中"),
                Map.of("value", "fault", "label", "故障"),
                Map.of("value", "scrapped", "label", "报废")
        );
    }

    public static String statusLabel(String status) {
        if (status == null) {
            return "空闲";
        }
        return switch (status) {
            case "running" -> "运行中";
            case "paused" -> "暂停";
            case "stopped" -> "停机";
            case "maintenance" -> "保养中";
            case "repairing" -> "维修中";
            case "fault" -> "故障";
            case "scrapped" -> "报废";
            default -> "空闲";
        };
    }
}
