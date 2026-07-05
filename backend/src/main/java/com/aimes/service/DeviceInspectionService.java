package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.DeviceInspectionPlanSaveRequest;
import com.aimes.dto.Requests.DeviceInspectionSubmitRequest;
import com.aimes.entity.DevDevice;
import com.aimes.entity.DevDeviceHistory;
import com.aimes.entity.DevInspectionPlan;
import com.aimes.entity.DevInspectionRecord;
import com.aimes.entity.SysUser;
import com.aimes.mapper.DevDeviceHistoryMapper;
import com.aimes.mapper.DevDeviceMapper;
import com.aimes.mapper.DevInspectionPlanMapper;
import com.aimes.mapper.DevInspectionRecordMapper;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeviceInspectionService {

    private static final Set<String> VALID_CYCLES = Set.of("daily", "weekly", "monthly");
    private static final Map<String, String> CYCLE_LABELS = Map.of(
            "daily", "每日",
            "weekly", "每周",
            "monthly", "每月"
    );

    private final DevInspectionPlanMapper devInspectionPlanMapper;
    private final DevInspectionRecordMapper devInspectionRecordMapper;
    private final DevDeviceMapper devDeviceMapper;
    private final DevDeviceHistoryMapper devDeviceHistoryMapper;
    private final DeviceCategoryService deviceCategoryService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final DeviceAlertPushService deviceAlertPushService;

    public List<Map<String, Object>> listPlans(Long deviceId, Long categoryId, String keyword) {
        LambdaQueryWrapper<DevInspectionPlan> wrapper = new LambdaQueryWrapper<>();
        if (deviceId != null) {
            wrapper.eq(DevInspectionPlan::getDeviceId, deviceId);
        }
        if (categoryId != null) {
            wrapper.eq(DevInspectionPlan::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(DevInspectionPlan::getPlanCode, keyword)
                    .or().like(DevInspectionPlan::getPlanName, keyword));
        }
        wrapper.orderByAsc(DevInspectionPlan::getPlanCode);
        return devInspectionPlanMapper.selectList(wrapper).stream().map(this::planToView).toList();
    }

    public List<Map<String, Object>> listPlansForDevice(Long deviceId) {
        DevDevice device = getDevice(deviceId);
        Set<Long> planIds = new LinkedHashSet<>();
        List<DevInspectionPlan> plans = new ArrayList<>();

        devInspectionPlanMapper.selectList(new LambdaQueryWrapper<DevInspectionPlan>()
                        .eq(DevInspectionPlan::getDeviceId, deviceId)
                        .eq(DevInspectionPlan::getEnabled, 1)
                        .orderByAsc(DevInspectionPlan::getPlanCode))
                .forEach(plan -> {
                    if (planIds.add(plan.getId())) {
                        plans.add(plan);
                    }
                });

        if (device.getCategoryId() != null) {
            devInspectionPlanMapper.selectList(new LambdaQueryWrapper<DevInspectionPlan>()
                            .isNull(DevInspectionPlan::getDeviceId)
                            .eq(DevInspectionPlan::getCategoryId, device.getCategoryId())
                            .eq(DevInspectionPlan::getEnabled, 1)
                            .orderByAsc(DevInspectionPlan::getPlanCode))
                    .forEach(plan -> {
                        if (planIds.add(plan.getId())) {
                            plans.add(plan);
                        }
                    });
        }

        return plans.stream().map(this::planToView).toList();
    }

    public Map<String, Object> getPlan(Long id) {
        return planToView(getPlanEntity(id));
    }

    @Transactional
    public Map<String, Object> createPlan(DeviceInspectionPlanSaveRequest request) {
        DevInspectionPlan plan = new DevInspectionPlan();
        applyPlanRequest(plan, request);
        if (!StringUtils.hasText(plan.getPlanCode())) {
            plan.setPlanCode(generatePlanCode());
        } else {
            ensureUniquePlanCode(plan.getPlanCode(), null);
        }
        plan.setCreatedTime(LocalDateTime.now());
        plan.setUpdatedTime(LocalDateTime.now());
        devInspectionPlanMapper.insert(plan);
        return planToView(plan);
    }

    @Transactional
    public Map<String, Object> updatePlan(Long id, DeviceInspectionPlanSaveRequest request) {
        DevInspectionPlan plan = getPlanEntity(id);
        if (StringUtils.hasText(request.getPlanCode()) && !request.getPlanCode().equals(plan.getPlanCode())) {
            ensureUniquePlanCode(request.getPlanCode(), id);
        }
        applyPlanRequest(plan, request);
        plan.setUpdatedTime(LocalDateTime.now());
        devInspectionPlanMapper.updateById(plan);
        return planToView(plan);
    }

    @Transactional
    public void deletePlan(Long id) {
        getPlanEntity(id);
        long related = devInspectionRecordMapper.selectCount(new LambdaQueryWrapper<DevInspectionRecord>()
                .eq(DevInspectionRecord::getPlanId, id));
        if (related > 0) {
            throw new BusinessException("点检计划已有关联记录，不能删除");
        }
        devInspectionPlanMapper.deleteById(id);
    }

    public List<Map<String, Object>> listRecords(Long deviceId) {
        getDevice(deviceId);
        return devInspectionRecordMapper.selectList(new LambdaQueryWrapper<DevInspectionRecord>()
                        .eq(DevInspectionRecord::getDeviceId, deviceId)
                        .orderByDesc(DevInspectionRecord::getInspectTime)
                        .orderByDesc(DevInspectionRecord::getId))
                .stream()
                .map(this::recordToView)
                .toList();
    }

    @Transactional
    public void deleteRecord(Long id) {
        DevInspectionRecord record = devInspectionRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("点检记录不存在");
        }
        getDevice(record.getDeviceId());
        devInspectionRecordMapper.deleteById(id);
    }

    @Transactional
    public Map<String, Object> submitInspection(DeviceInspectionSubmitRequest request) {
        DevDevice device = getDevice(request.getDeviceId());
        SysUser user = authService.currentUser();

        DevInspectionPlan plan = null;
        if (request.getPlanId() != null) {
            plan = getPlanEntity(request.getPlanId());
            if (plan.getDeviceId() != null && !plan.getDeviceId().equals(device.getId())) {
                throw new BusinessException("点检计划与设备不匹配");
            }
            if (plan.getCategoryId() != null && device.getCategoryId() != null
                    && !plan.getCategoryId().equals(device.getCategoryId()) && plan.getDeviceId() == null) {
                throw new BusinessException("点检计划与设备分类不匹配");
            }
        }

        boolean isNormal = request.getItems().stream().allMatch(item -> Boolean.TRUE.equals(item.getIsNormal()));
        List<Map<String, Object>> itemViews = request.getItems().stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemName", item.getItemName());
            row.put("isNormal", Boolean.TRUE.equals(item.getIsNormal()));
            row.put("remark", item.getRemark());
            return row;
        }).toList();

        DevInspectionRecord record = new DevInspectionRecord();
        record.setRecordNo(generateRecordNo());
        record.setDeviceId(device.getId());
        record.setPlanId(plan == null ? null : plan.getId());
        record.setPlanName(plan == null ? null : plan.getPlanName());
        record.setInspectorId(user.getId());
        record.setInspectorName(user.getRealName());
        record.setInspectTime(LocalDateTime.now());
        record.setCheckItems(writeJson(itemViews));
        record.setIsNormal(isNormal ? 1 : 0);
        record.setRemark(request.getRemark());
        record.setCreatedTime(LocalDateTime.now());
        devInspectionRecordMapper.insert(record);

        String historyDesc = isNormal
                ? "设备点检：全部正常"
                : "设备点检：发现异常";
        recordHistory(device.getId(), "inspection", historyDesc, user.getId(), user.getRealName(),
                isNormal ? "normal" : "abnormal");

        if (!isNormal) {
            deviceAlertPushService.pushInspectionAlert(record);
        }

        return recordToView(record);
    }

    private void applyPlanRequest(DevInspectionPlan plan, DeviceInspectionPlanSaveRequest request) {
        if (StringUtils.hasText(request.getPlanCode())) {
            plan.setPlanCode(request.getPlanCode().trim());
        }
        plan.setPlanName(request.getPlanName().trim());
        plan.setDeviceId(request.getDeviceId());
        plan.setCategoryId(request.getCategoryId());
        String cycleType = StringUtils.hasText(request.getCycleType()) ? request.getCycleType() : "daily";
        if (!VALID_CYCLES.contains(cycleType)) {
            throw new BusinessException("无效的点检周期");
        }
        plan.setCycleType(cycleType);
        plan.setCheckItems(writeJson(request.getCheckItems()));
        plan.setEnabled(request.getEnabled() == null || request.getEnabled() ? 1 : 0);
        plan.setRemark(request.getRemark());
    }

    private Map<String, Object> planToView(DevInspectionPlan plan) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", plan.getId());
        view.put("planCode", plan.getPlanCode());
        view.put("planName", plan.getPlanName());
        view.put("deviceId", plan.getDeviceId());
        view.put("categoryId", plan.getCategoryId());
        view.put("categoryName", deviceCategoryService.resolveCategoryName(plan.getCategoryId()));
        view.put("cycleType", plan.getCycleType());
        view.put("cycleTypeLabel", CYCLE_LABELS.getOrDefault(plan.getCycleType(), plan.getCycleType()));
        view.put("checkItems", readStringList(plan.getCheckItems()));
        view.put("enabled", plan.getEnabled() != null && plan.getEnabled() == 1);
        view.put("remark", plan.getRemark());
        view.put("createdTime", plan.getCreatedTime());
        view.put("updatedTime", plan.getUpdatedTime());
        return view;
    }

    private Map<String, Object> recordToView(DevInspectionRecord record) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", record.getId());
        view.put("recordNo", record.getRecordNo());
        view.put("deviceId", record.getDeviceId());
        view.put("planId", record.getPlanId());
        view.put("planName", record.getPlanName());
        view.put("inspectorId", record.getInspectorId());
        view.put("inspectorName", record.getInspectorName());
        view.put("inspectTime", record.getInspectTime());
        view.put("checkItems", readItemResults(record.getCheckItems()));
        view.put("isNormal", record.getIsNormal() != null && record.getIsNormal() == 1);
        view.put("isNormalLabel", record.getIsNormal() != null && record.getIsNormal() == 1 ? "正常" : "异常");
        view.put("remark", record.getRemark());
        view.put("createdTime", record.getCreatedTime());
        return view;
    }

    private DevDevice getDevice(Long deviceId) {
        DevDevice device = devDeviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        return device;
    }

    private DevInspectionPlan getPlanEntity(Long id) {
        DevInspectionPlan plan = devInspectionPlanMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException("点检计划不存在");
        }
        return plan;
    }

    private void ensureUniquePlanCode(String planCode, Long excludeId) {
        long count = devInspectionPlanMapper.selectCount(new LambdaQueryWrapper<DevInspectionPlan>()
                .eq(DevInspectionPlan::getPlanCode, planCode)
                .ne(excludeId != null, DevInspectionPlan::getId, excludeId));
        if (count > 0) {
            throw new BusinessException("点检计划编号已存在");
        }
    }

    private String generatePlanCode() {
        long count = devInspectionPlanMapper.selectCount(null) + 1;
        return "INSP-P-" + String.format("%03d", count);
    }

    private String generateRecordNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "INSP-" + datePart + "-";
        long count = devInspectionRecordMapper.selectCount(new LambdaQueryWrapper<DevInspectionRecord>()
                .likeRight(DevInspectionRecord::getRecordNo, prefix)) + 1;
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
            throw new BusinessException("点检数据序列化失败");
        }
    }
}
