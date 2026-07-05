package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.DeviceRepairCreateRequest;
import com.aimes.dto.Requests.DeviceRepairHandleRequest;
import com.aimes.entity.DevDevice;
import com.aimes.entity.DevDeviceHistory;
import com.aimes.entity.DevRepairOrder;
import com.aimes.entity.ExcEvent;
import com.aimes.entity.SysUser;
import com.aimes.mapper.DevDeviceHistoryMapper;
import com.aimes.mapper.DevDeviceMapper;
import com.aimes.mapper.DevRepairOrderMapper;
import com.aimes.mapper.ExcEventMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceRepairService {

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "open", "待维修",
            "processing", "维修中",
            "completed", "已完成",
            "cancelled", "已取消"
    );

    private final DevRepairOrderMapper devRepairOrderMapper;
    private final DevDeviceMapper devDeviceMapper;
    private final DevDeviceHistoryMapper devDeviceHistoryMapper;
    private final ExcEventMapper excEventMapper;
    private final AuthService authService;
    private final DeviceAlertPushService deviceAlertPushService;

    public List<Map<String, Object>> list(Long deviceId, String status) {
        LambdaQueryWrapper<DevRepairOrder> wrapper = new LambdaQueryWrapper<>();
        if (deviceId != null) {
            wrapper.eq(DevRepairOrder::getDeviceId, deviceId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(DevRepairOrder::getStatus, status);
        }
        wrapper.orderByDesc(DevRepairOrder::getReportTime).orderByDesc(DevRepairOrder::getId);
        return devRepairOrderMapper.selectList(wrapper).stream().map(this::toView).toList();
    }

    public Map<String, Object> detail(Long id) {
        return toView(getOrder(id));
    }

    @Transactional
    public Map<String, Object> create(DeviceRepairCreateRequest request) {
        DevDevice device = getDevice(request.getDeviceId());
        if ("scrapped".equals(device.getStatus())) {
            throw new BusinessException("已报废设备不能报修");
        }
        SysUser user = authService.currentUser();

        DevRepairOrder order = new DevRepairOrder();
        order.setRepairNo(generateRepairNo());
        order.setDeviceId(device.getId());
        order.setEventId(request.getEventId());
        order.setFaultReason(request.getFaultReason().trim());
        order.setFaultCode(StringUtils.hasText(request.getFaultCode()) ? request.getFaultCode().trim() : null);
        order.setDescription(request.getDescription());
        order.setStatus("open");
        order.setReporterId(user.getId());
        order.setReporterName(user.getRealName());
        order.setReportTime(LocalDateTime.now());
        order.setRemark(request.getRemark());
        order.setCreatedTime(LocalDateTime.now());
        order.setUpdatedTime(LocalDateTime.now());
        devRepairOrderMapper.insert(order);

        String oldStatus = device.getStatus();
        if (!"fault".equals(device.getStatus()) && !"repairing".equals(device.getStatus())) {
            device.setStatus("fault");
            device.setUpdatedTime(LocalDateTime.now());
            devDeviceMapper.updateById(device);
        }

        recordHistory(device.getId(), "repair", "设备报修：" + order.getFaultReason(),
                user.getId(), user.getRealName(), oldStatus, "open");

        deviceAlertPushService.pushRepairAlert(order);

        return toView(order);
    }

    @Transactional
    public Map<String, Object> start(Long id) {
        DevRepairOrder order = getOrder(id);
        if (!"open".equals(order.getStatus())) {
            throw new BusinessException("仅待维修状态可开始维修");
        }
        SysUser user = authService.currentUser();
        DevDevice device = getDevice(order.getDeviceId());

        order.setStatus("processing");
        order.setRepairerId(user.getId());
        order.setRepairerName(user.getRealName());
        order.setStartTime(LocalDateTime.now());
        order.setUpdatedTime(LocalDateTime.now());
        devRepairOrderMapper.updateById(order);

        String oldStatus = device.getStatus();
        device.setStatus("repairing");
        device.setUpdatedTime(LocalDateTime.now());
        devDeviceMapper.updateById(device);

        recordHistory(device.getId(), "repair", "开始维修：" + order.getFaultReason(),
                user.getId(), user.getRealName(), oldStatus, "processing");
        return toView(order);
    }

    @Transactional
    public Map<String, Object> complete(Long id, DeviceRepairHandleRequest request) {
        DevRepairOrder order = getOrder(id);
        if ("completed".equals(order.getStatus()) || "cancelled".equals(order.getStatus())) {
            throw new BusinessException("维修单已结束，不能重复处理");
        }
        SysUser user = authService.currentUser();
        DevDevice device = getDevice(order.getDeviceId());

        LocalDateTime endTime = LocalDateTime.now();
        if (order.getStartTime() == null) {
            order.setStartTime(endTime);
            order.setRepairerId(user.getId());
            order.setRepairerName(user.getRealName());
        }
        order.setStatus("completed");
        order.setEndTime(endTime);
        order.setRepairMinutes((int) Duration.between(order.getStartTime(), endTime).toMinutes());
        order.setRepairAction(request.getRepairAction());
        order.setRepairResult(request.getRepairResult());
        if (StringUtils.hasText(request.getRemark())) {
            order.setRemark(request.getRemark());
        }
        order.setUpdatedTime(endTime);
        devRepairOrderMapper.updateById(order);

        String oldStatus = device.getStatus();
        device.setStatus("idle");
        device.setUpdatedTime(endTime);
        devDeviceMapper.updateById(device);

        recordHistory(device.getId(), "repair", "维修完成：" + request.getRepairResult(),
                user.getId(), user.getRealName(), oldStatus, "completed");
        return toView(order);
    }

    @Transactional
    public void delete(Long id) {
        DevRepairOrder order = getOrder(id);
        Long deviceId = order.getDeviceId();
        boolean wasActive = "open".equals(order.getStatus()) || "processing".equals(order.getStatus());
        devRepairOrderMapper.deleteById(id);
        if (wasActive) {
            restoreDeviceStatusIfNeeded(deviceId);
        }
    }

    private void restoreDeviceStatusIfNeeded(Long deviceId) {
        DevDevice device = getDevice(deviceId);
        if (!"fault".equals(device.getStatus()) && !"repairing".equals(device.getStatus())) {
            return;
        }
        long openRepairs = devRepairOrderMapper.selectCount(new LambdaQueryWrapper<DevRepairOrder>()
                .eq(DevRepairOrder::getDeviceId, deviceId)
                .in(DevRepairOrder::getStatus, List.of("open", "processing")));
        if (openRepairs > 0) {
            return;
        }
        long openExceptions = excEventMapper.selectCount(new LambdaQueryWrapper<ExcEvent>()
                .eq(ExcEvent::getDeviceId, deviceId)
                .eq(ExcEvent::getEventType, "device")
                .in(ExcEvent::getStatus, List.of("open", "processing")));
        if (openExceptions > 0) {
            return;
        }
        device.setStatus("idle");
        device.setUpdatedTime(LocalDateTime.now());
        devDeviceMapper.updateById(device);
    }

    private Map<String, Object> toView(DevRepairOrder order) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", order.getId());
        view.put("repairNo", order.getRepairNo());
        view.put("deviceId", order.getDeviceId());
        view.put("eventId", order.getEventId());
        view.put("faultReason", order.getFaultReason());
        view.put("faultCode", order.getFaultCode());
        view.put("description", order.getDescription());
        view.put("status", order.getStatus());
        view.put("statusLabel", STATUS_LABELS.getOrDefault(order.getStatus(), order.getStatus()));
        view.put("reporterId", order.getReporterId());
        view.put("reporterName", order.getReporterName());
        view.put("repairerId", order.getRepairerId());
        view.put("repairerName", order.getRepairerName());
        view.put("reportTime", order.getReportTime());
        view.put("startTime", order.getStartTime());
        view.put("endTime", order.getEndTime());
        view.put("repairMinutes", order.getRepairMinutes());
        view.put("repairAction", order.getRepairAction());
        view.put("repairResult", order.getRepairResult());
        view.put("remark", order.getRemark());
        view.put("createdTime", order.getCreatedTime());
        view.put("updatedTime", order.getUpdatedTime());
        return view;
    }

    private DevRepairOrder getOrder(Long id) {
        DevRepairOrder order = devRepairOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("维修单不存在");
        }
        return order;
    }

    private DevDevice getDevice(Long deviceId) {
        DevDevice device = devDeviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        return device;
    }

    private String generateRepairNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "REP-" + datePart + "-";
        long count = devRepairOrderMapper.selectCount(new LambdaQueryWrapper<DevRepairOrder>()
                .likeRight(DevRepairOrder::getRepairNo, prefix)) + 1;
        return prefix + String.format("%03d", count);
    }

    private void recordHistory(Long deviceId, String actionType, String actionDesc,
                               Long operatorId, String operatorName, String beforeValue, String afterValue) {
        DevDeviceHistory history = new DevDeviceHistory();
        history.setDeviceId(deviceId);
        history.setActionType(actionType);
        history.setActionDesc(actionDesc);
        history.setOperatorId(operatorId);
        history.setOperatorName(operatorName);
        history.setBeforeValue(beforeValue);
        history.setAfterValue(afterValue);
        history.setCreateTime(LocalDateTime.now());
        devDeviceHistoryMapper.insert(history);
    }
}
