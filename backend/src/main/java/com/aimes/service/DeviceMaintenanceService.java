package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.DeviceMaintenancePlanSaveRequest;
import com.aimes.dto.Requests.DeviceMaintenanceSubmitRequest;
import com.aimes.entity.DevDevice;
import com.aimes.entity.DevDeviceHistory;
import com.aimes.entity.DevMaintenancePlan;
import com.aimes.entity.DevMaintenanceRecord;
import com.aimes.entity.SysUser;
import com.aimes.mapper.DevDeviceHistoryMapper;
import com.aimes.mapper.DevDeviceMapper;
import com.aimes.mapper.DevMaintenancePlanMapper;
import com.aimes.mapper.DevMaintenanceRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeviceMaintenanceService {

    private static final Set<String> VALID_CYCLES = Set.of("daily", "weekly", "monthly");
    private static final Map<String, String> CYCLE_LABELS = Map.of(
            "daily", "每日",
            "weekly", "每周",
            "monthly", "每月"
    );

    private final DevMaintenancePlanMapper devMaintenancePlanMapper;
    private final DevMaintenanceRecordMapper devMaintenanceRecordMapper;
    private final DevDeviceMapper devDeviceMapper;
    private final DevDeviceHistoryMapper devDeviceHistoryMapper;
    private final DeviceCategoryService deviceCategoryService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> listPlans(Long deviceId, Long categoryId, String keyword) {
        LambdaQueryWrapper<DevMaintenancePlan> wrapper = new LambdaQueryWrapper<>();
        if (deviceId != null) {
            wrapper.eq(DevMaintenancePlan::getDeviceId, deviceId);
        }
        if (categoryId != null) {
            wrapper.eq(DevMaintenancePlan::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(DevMaintenancePlan::getPlanCode, keyword)
                    .or().like(DevMaintenancePlan::getPlanName, keyword));
        }
        wrapper.orderByAsc(DevMaintenancePlan::getPlanCode);
        return devMaintenancePlanMapper.selectList(wrapper).stream().map(this::planToView).toList();
    }

    public List<Map<String, Object>> listPlansForDevice(Long deviceId) {
        DevDevice device = getDevice(deviceId);
        Set<Long> planIds = new LinkedHashSet<>();
        List<DevMaintenancePlan> plans = new ArrayList<>();

        devMaintenancePlanMapper.selectList(new LambdaQueryWrapper<DevMaintenancePlan>()
                        .eq(DevMaintenancePlan::getDeviceId, deviceId)
                        .eq(DevMaintenancePlan::getEnabled, 1)
                        .orderByAsc(DevMaintenancePlan::getPlanCode))
                .forEach(plan -> {
                    if (planIds.add(plan.getId())) {
                        plans.add(plan);
                    }
                });

        if (device.getCategoryId() != null) {
            devMaintenancePlanMapper.selectList(new LambdaQueryWrapper<DevMaintenancePlan>()
                            .isNull(DevMaintenancePlan::getDeviceId)
                            .eq(DevMaintenancePlan::getCategoryId, device.getCategoryId())
                            .eq(DevMaintenancePlan::getEnabled, 1)
                            .orderByAsc(DevMaintenancePlan::getPlanCode))
                    .forEach(plan -> {
                        if (planIds.add(plan.getId())) {
                            plans.add(plan);
                        }
                    });
        }

        return plans.stream().map(this::planToView).toList();
    }

    public List<Map<String, Object>> listDuePlans(Long deviceId) {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<DevMaintenancePlan> wrapper = new LambdaQueryWrapper<DevMaintenancePlan>()
                .eq(DevMaintenancePlan::getEnabled, 1)
                .isNotNull(DevMaintenancePlan::getNextDueDate)
                .le(DevMaintenancePlan::getNextDueDate, today)
                .orderByAsc(DevMaintenancePlan::getNextDueDate);
        if (deviceId != null) {
            DevDevice device = getDevice(deviceId);
            wrapper.and(w -> w.eq(DevMaintenancePlan::getDeviceId, deviceId)
                    .or(inner -> inner.isNull(DevMaintenancePlan::getDeviceId)
                            .eq(DevMaintenancePlan::getCategoryId, device.getCategoryId())));
        }
        return devMaintenancePlanMapper.selectList(wrapper).stream().map(this::planToView).toList();
    }

    public Map<Long, Long> countOverdueByDevice() {
        LocalDate today = LocalDate.now();
        Map<Long, Long> counts = new HashMap<>();
        List<DevMaintenancePlan> overduePlans = devMaintenancePlanMapper.selectList(new LambdaQueryWrapper<DevMaintenancePlan>()
                .eq(DevMaintenancePlan::getEnabled, 1)
                .isNotNull(DevMaintenancePlan::getNextDueDate)
                .le(DevMaintenancePlan::getNextDueDate, today));
        for (DevMaintenancePlan plan : overduePlans) {
            if (plan.getDeviceId() != null) {
                counts.merge(plan.getDeviceId(), 1L, Long::sum);
            } else if (plan.getCategoryId() != null) {
                devDeviceMapper.selectList(new LambdaQueryWrapper<DevDevice>()
                                .eq(DevDevice::getCategoryId, plan.getCategoryId()))
                        .forEach(device -> counts.merge(device.getId(), 1L, Long::sum));
            }
        }
        return counts;
    }

    public Map<String, Object> getPlan(Long id) {
        return planToView(getPlanEntity(id));
    }

    @Transactional
    public Map<String, Object> createPlan(DeviceMaintenancePlanSaveRequest request) {
        DevMaintenancePlan plan = new DevMaintenancePlan();
        applyPlanRequest(plan, request);
        if (!StringUtils.hasText(plan.getPlanCode())) {
            plan.setPlanCode(generatePlanCode());
        } else {
            ensureUniquePlanCode(plan.getPlanCode(), null);
        }
        if (plan.getNextDueDate() == null) {
            plan.setNextDueDate(LocalDate.now());
        }
        plan.setCreatedTime(LocalDateTime.now());
        plan.setUpdatedTime(LocalDateTime.now());
        devMaintenancePlanMapper.insert(plan);
        return planToView(plan);
    }

    @Transactional
    public Map<String, Object> updatePlan(Long id, DeviceMaintenancePlanSaveRequest request) {
        DevMaintenancePlan plan = getPlanEntity(id);
        if (StringUtils.hasText(request.getPlanCode()) && !request.getPlanCode().equals(plan.getPlanCode())) {
            ensureUniquePlanCode(request.getPlanCode(), id);
        }
        applyPlanRequest(plan, request);
        plan.setUpdatedTime(LocalDateTime.now());
        devMaintenancePlanMapper.updateById(plan);
        return planToView(plan);
    }

    @Transactional
    public void deletePlan(Long id) {
        getPlanEntity(id);
        long related = devMaintenanceRecordMapper.selectCount(new LambdaQueryWrapper<DevMaintenanceRecord>()
                .eq(DevMaintenanceRecord::getPlanId, id));
        if (related > 0) {
            throw new BusinessException("保养计划已有关联记录，不能删除");
        }
        devMaintenancePlanMapper.deleteById(id);
    }

    public List<Map<String, Object>> listRecords(Long deviceId) {
        getDevice(deviceId);
        return devMaintenanceRecordMapper.selectList(new LambdaQueryWrapper<DevMaintenanceRecord>()
                        .eq(DevMaintenanceRecord::getDeviceId, deviceId)
                        .orderByDesc(DevMaintenanceRecord::getMaintenanceTime)
                        .orderByDesc(DevMaintenanceRecord::getId))
                .stream()
                .map(this::recordToView)
                .toList();
    }

    @Transactional
    public void deleteRecord(Long id) {
        DevMaintenanceRecord record = devMaintenanceRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("保养记录不存在");
        }
        getDevice(record.getDeviceId());
        Long planId = record.getPlanId();
        devMaintenanceRecordMapper.deleteById(id);

        if (planId != null) {
            DevMaintenancePlan plan = devMaintenancePlanMapper.selectById(planId);
            if (plan != null) {
                DevMaintenanceRecord latest = devMaintenanceRecordMapper.selectOne(new LambdaQueryWrapper<DevMaintenanceRecord>()
                        .eq(DevMaintenanceRecord::getPlanId, planId)
                        .orderByDesc(DevMaintenanceRecord::getMaintenanceTime)
                        .orderByDesc(DevMaintenanceRecord::getId)
                        .last("limit 1"));
                if (latest != null && latest.getMaintenanceTime() != null) {
                    LocalDate lastDate = latest.getMaintenanceTime().toLocalDate();
                    plan.setLastMaintenanceDate(lastDate);
                    plan.setNextDueDate(calcNextDueDate(plan.getCycleType(), lastDate));
                } else {
                    plan.setLastMaintenanceDate(null);
                }
                plan.setUpdatedTime(LocalDateTime.now());
                devMaintenancePlanMapper.updateById(plan);
            }
        }
    }

    @Transactional
    public Map<String, Object> submitMaintenance(DeviceMaintenanceSubmitRequest request) {
        DevDevice device = getDevice(request.getDeviceId());
        SysUser user = authService.currentUser();

        DevMaintenancePlan plan = null;
        if (request.getPlanId() != null) {
            plan = getPlanEntity(request.getPlanId());
            validatePlanForDevice(plan, device);
        }

        boolean isCompleted = request.getItems().stream().allMatch(item -> Boolean.TRUE.equals(item.getDone()));
        List<Map<String, Object>> itemViews = request.getItems().stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemName", item.getItemName());
            row.put("done", Boolean.TRUE.equals(item.getDone()));
            row.put("remark", item.getRemark());
            return row;
        }).toList();

        DevMaintenanceRecord record = new DevMaintenanceRecord();
        record.setRecordNo(generateRecordNo());
        record.setDeviceId(device.getId());
        record.setPlanId(plan == null ? null : plan.getId());
        record.setPlanName(plan == null ? null : plan.getPlanName());
        record.setMaintainerId(user.getId());
        record.setMaintainerName(user.getRealName());
        record.setMaintenanceTime(LocalDateTime.now());
        record.setMaintenanceItems(writeJson(itemViews));
        record.setIsCompleted(isCompleted ? 1 : 0);
        record.setRemark(request.getRemark());
        record.setCreatedTime(LocalDateTime.now());
        devMaintenanceRecordMapper.insert(record);

        if (plan != null) {
            plan.setLastMaintenanceDate(LocalDate.now());
            plan.setNextDueDate(calcNextDueDate(plan.getCycleType(), LocalDate.now()));
            plan.setUpdatedTime(LocalDateTime.now());
            devMaintenancePlanMapper.updateById(plan);
        }

        String historyDesc = plan == null
                ? "设备保养：已完成"
                : "设备保养：" + plan.getPlanName();
        recordHistory(device.getId(), "maintenance", historyDesc, user.getId(), user.getRealName(),
                isCompleted ? "completed" : "partial");

        if ("maintenance".equals(device.getStatus()) && isCompleted) {
            device.setStatus("idle");
            device.setUpdatedTime(LocalDateTime.now());
            devDeviceMapper.updateById(device);
        }

        return recordToView(record);
    }

    private void applyPlanRequest(DevMaintenancePlan plan, DeviceMaintenancePlanSaveRequest request) {
        if (StringUtils.hasText(request.getPlanCode())) {
            plan.setPlanCode(request.getPlanCode().trim());
        }
        plan.setPlanName(request.getPlanName().trim());
        plan.setDeviceId(request.getDeviceId());
        plan.setCategoryId(request.getCategoryId());
        String cycleType = StringUtils.hasText(request.getCycleType()) ? request.getCycleType() : "monthly";
        if (!VALID_CYCLES.contains(cycleType)) {
            throw new BusinessException("无效的保养周期");
        }
        plan.setCycleType(cycleType);
        plan.setMaintenanceItems(writeJson(request.getMaintenanceItems()));
        if (request.getNextDueDate() != null) {
            plan.setNextDueDate(request.getNextDueDate());
        }
        plan.setEnabled(request.getEnabled() == null || request.getEnabled() ? 1 : 0);
        plan.setRemark(request.getRemark());
    }

    private void validatePlanForDevice(DevMaintenancePlan plan, DevDevice device) {
        if (plan.getDeviceId() != null && !plan.getDeviceId().equals(device.getId())) {
            throw new BusinessException("保养计划与设备不匹配");
        }
        if (plan.getCategoryId() != null && device.getCategoryId() != null
                && !plan.getCategoryId().equals(device.getCategoryId()) && plan.getDeviceId() == null) {
            throw new BusinessException("保养计划与设备分类不匹配");
        }
    }

    private Map<String, Object> planToView(DevMaintenancePlan plan) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", plan.getId());
        view.put("planCode", plan.getPlanCode());
        view.put("planName", plan.getPlanName());
        view.put("deviceId", plan.getDeviceId());
        view.put("categoryId", plan.getCategoryId());
        view.put("categoryName", deviceCategoryService.resolveCategoryName(plan.getCategoryId()));
        view.put("cycleType", plan.getCycleType());
        view.put("cycleTypeLabel", CYCLE_LABELS.getOrDefault(plan.getCycleType(), plan.getCycleType()));
        view.put("maintenanceItems", readStringList(plan.getMaintenanceItems()));
        view.put("nextDueDate", plan.getNextDueDate());
        view.put("lastMaintenanceDate", plan.getLastMaintenanceDate());
        view.put("enabled", plan.getEnabled() != null && plan.getEnabled() == 1);
        view.put("overdue", isOverdue(plan));
        view.put("overdueDays", overdueDays(plan));
        view.put("remark", plan.getRemark());
        view.put("createdTime", plan.getCreatedTime());
        view.put("updatedTime", plan.getUpdatedTime());
        return view;
    }

    private Map<String, Object> recordToView(DevMaintenanceRecord record) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", record.getId());
        view.put("recordNo", record.getRecordNo());
        view.put("deviceId", record.getDeviceId());
        view.put("planId", record.getPlanId());
        view.put("planName", record.getPlanName());
        view.put("maintainerId", record.getMaintainerId());
        view.put("maintainerName", record.getMaintainerName());
        view.put("maintenanceTime", record.getMaintenanceTime());
        view.put("maintenanceItems", readItemResults(record.getMaintenanceItems()));
        view.put("isCompleted", record.getIsCompleted() != null && record.getIsCompleted() == 1);
        view.put("isCompletedLabel", record.getIsCompleted() != null && record.getIsCompleted() == 1 ? "已完成" : "未完成");
        view.put("remark", record.getRemark());
        view.put("createdTime", record.getCreatedTime());
        return view;
    }

    private boolean isOverdue(DevMaintenancePlan plan) {
        return plan.getNextDueDate() != null
                && plan.getEnabled() != null
                && plan.getEnabled() == 1
                && !plan.getNextDueDate().isAfter(LocalDate.now());
    }

    private long overdueDays(DevMaintenancePlan plan) {
        if (!isOverdue(plan) || plan.getNextDueDate() == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(plan.getNextDueDate(), LocalDate.now());
    }

    private LocalDate calcNextDueDate(String cycleType, LocalDate baseDate) {
        return switch (cycleType) {
            case "daily" -> baseDate.plusDays(1);
            case "weekly" -> baseDate.plusWeeks(1);
            default -> baseDate.plusMonths(1);
        };
    }

    private DevDevice getDevice(Long deviceId) {
        DevDevice device = devDeviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        return device;
    }

    private DevMaintenancePlan getPlanEntity(Long id) {
        DevMaintenancePlan plan = devMaintenancePlanMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("保养计划不存在");
        }
        return plan;
    }

    private void ensureUniquePlanCode(String planCode, Long excludeId) {
        long count = devMaintenancePlanMapper.selectCount(new LambdaQueryWrapper<DevMaintenancePlan>()
                .eq(DevMaintenancePlan::getPlanCode, planCode)
                .ne(excludeId != null, DevMaintenancePlan::getId, excludeId));
        if (count > 0) {
            throw new BusinessException("保养计划编号已存在");
        }
    }

    private String generatePlanCode() {
        long count = devMaintenancePlanMapper.selectCount(null) + 1;
        return "MAINT-P-" + String.format("%03d", count);
    }

    private String generateRecordNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "MAINT-" + datePart + "-";
        long count = devMaintenanceRecordMapper.selectCount(new LambdaQueryWrapper<DevMaintenanceRecord>()
                .likeRight(DevMaintenanceRecord::getRecordNo, prefix)) + 1;
        return prefix + String.format("%03d", count);
    }

    private void recordHistory(Long deviceId, String actionType, String actionDesc,
                               Long operatorId, String operatorName, String afterValue) {
        DevDeviceHistory history = new DevDeviceHistory();
        history.setDeviceId(deviceId);
        history.setActionType(actionType);
        history.setActionDesc(actionDesc);
        history.setOperatorId(operatorId);
        history.setOperatorName(operatorName);
        history.setAfterValue(afterValue);
        history.setCreateTime(LocalDateTime.now());
        devDeviceHistoryMapper.insert(history);
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> readItemResults(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("保养数据序列化失败");
        }
    }
}
