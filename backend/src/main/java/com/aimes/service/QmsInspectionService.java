package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.InspectionSubmitRequest;
import com.aimes.entity.ExcEvent;
import com.aimes.entity.MdmOperation;
import com.aimes.entity.ProdProcessRecord;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.entity.QmsInspectionPlan;
import com.aimes.entity.QmsInspectionRecord;
import com.aimes.entity.SysUser;
import com.aimes.mapper.ExcEventMapper;
import com.aimes.mapper.MdmOperationMapper;
import com.aimes.mapper.ProdProcessRecordMapper;
import com.aimes.mapper.ProdWorkOrderMapper;
import com.aimes.mapper.QmsInspectionPlanMapper;
import com.aimes.mapper.QmsInspectionRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QmsInspectionService {

    private final QmsInspectionPlanMapper qmsInspectionPlanMapper;
    private final QmsInspectionRecordMapper qmsInspectionRecordMapper;
    private final MdmOperationMapper mdmOperationMapper;
    private final ProdWorkOrderMapper prodWorkOrderMapper;
    private final ProdProcessRecordMapper prodProcessRecordMapper;
    private final ExcEventMapper excEventMapper;
    private final AuthService authService;

    public List<Map<String, Object>> listPlansByOperation(Long operationId) {
        return qmsInspectionPlanMapper.selectList(new LambdaQueryWrapper<QmsInspectionPlan>()
                        .eq(QmsInspectionPlan::getOperationId, operationId)
                        .orderByAsc(QmsInspectionPlan::getSortNo)
                        .orderByAsc(QmsInspectionPlan::getId))
                .stream()
                .map(this::planToView)
                .toList();
    }

    public List<Map<String, Object>> listPlansByProcessName(Long workOrderId, String processName) {
        ProdWorkOrder order = prodWorkOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        ProdProcessRecord record = prodProcessRecordMapper.selectOne(new LambdaQueryWrapper<ProdProcessRecord>()
                .eq(ProdProcessRecord::getWorkOrderId, workOrderId)
                .eq(ProdProcessRecord::getProcessName, processName)
                .last("limit 1"));
        if (record == null || record.getOperationId() == null) {
            return List.of();
        }
        MdmOperation operation = mdmOperationMapper.selectById(record.getOperationId());
        if (operation == null || operation.getNeedCheck() == null || operation.getNeedCheck() != 1) {
            return List.of();
        }
        return listPlansByOperation(record.getOperationId());
    }

    @Transactional
    public Map<String, Object> submitInspections(InspectionSubmitRequest request) {
        SysUser user = authService.currentUser();
        ProdWorkOrder order = prodWorkOrderMapper.selectById(request.getWorkOrderId());
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        ProdProcessRecord record = prodProcessRecordMapper.selectOne(new LambdaQueryWrapper<ProdProcessRecord>()
                .eq(ProdProcessRecord::getWorkOrderId, request.getWorkOrderId())
                .eq(ProdProcessRecord::getProcessName, request.getProcessName())
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException("工序记录不存在");
        }

        List<Map<String, Object>> saved = new ArrayList<>();
        boolean hasFailure = false;
        StringBuilder failDesc = new StringBuilder();

        for (InspectionSubmitRequest.InspectionItemRequest item : request.getItems()) {
            String result = evaluateResult(item);
            QmsInspectionRecord inspection = new QmsInspectionRecord();
            inspection.setWorkOrderId(request.getWorkOrderId());
            inspection.setProcessRecordId(record.getId());
            inspection.setOperationId(record.getOperationId());
            inspection.setPlanId(item.getPlanId());
            inspection.setItemName(item.getItemName());
            inspection.setMeasuredValue(item.getMeasuredValue());
            inspection.setResult(result);
            inspection.setInspectorId(user.getId());
            inspection.setRemark(item.getRemark());
            inspection.setCreatedTime(LocalDateTime.now());
            qmsInspectionRecordMapper.insert(inspection);

            if ("fail".equals(result)) {
                hasFailure = true;
                if (!failDesc.isEmpty()) {
                    failDesc.append("；");
                }
                failDesc.append(item.getItemName()).append("不合格");
            }
            saved.add(Map.of(
                    "id", inspection.getId(),
                    "itemName", inspection.getItemName(),
                    "measuredValue", inspection.getMeasuredValue() == null ? "" : inspection.getMeasuredValue(),
                    "result", inspection.getResult()
            ));
        }

        Long exceptionId = null;
        if (hasFailure) {
            exceptionId = createQualityException(order, record, failDesc.toString(), user);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", saved);
        result.put("hasFailure", hasFailure);
        result.put("exceptionId", exceptionId);
        return result;
    }

    private Long createQualityException(ProdWorkOrder order, ProdProcessRecord record, String description, SysUser user) {
        ExcEvent event = new ExcEvent();
        event.setEventNo(nextEventNo());
        event.setEventType("quality");
        event.setWorkOrderId(order.getId());
        event.setDescription("工序「" + record.getProcessName() + "」检验不合格：" + description);
        event.setStatus("open");
        event.setReporterId(user.getId());
        event.setOccurTime(LocalDateTime.now());
        event.setCreateTime(LocalDateTime.now());
        excEventMapper.insert(event);

        order.setStatus("exception");
        prodWorkOrderMapper.updateById(order);
        return event.getId();
    }

    private String evaluateResult(InspectionSubmitRequest.InspectionItemRequest item) {
        if (StringUtils.hasText(item.getResult())) {
            return "fail".equalsIgnoreCase(item.getResult()) ? "fail" : "pass";
        }
        if (!StringUtils.hasText(item.getMeasuredValue())) {
            return "pass";
        }
        if (item.getPlanId() != null) {
            QmsInspectionPlan plan = qmsInspectionPlanMapper.selectById(item.getPlanId());
            if (plan != null) {
                return checkNumericRange(item.getMeasuredValue(), plan.getMinValue(), plan.getMaxValue())
                        ? "pass" : "fail";
            }
        }
        return "pass";
    }

    private boolean checkNumericRange(String measured, String min, String max) {
        try {
            double value = Double.parseDouble(measured.trim());
            if (StringUtils.hasText(min)) {
                double minVal = Double.parseDouble(min.trim());
                if (value < minVal) {
                    return false;
                }
            }
            if (StringUtils.hasText(max)) {
                double maxVal = Double.parseDouble(max.trim());
                if (value > maxVal) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException ex) {
            return true;
        }
    }

    private Map<String, Object> planToView(QmsInspectionPlan plan) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", plan.getId());
        row.put("operationId", plan.getOperationId());
        row.put("itemName", plan.getItemName());
        row.put("standard", plan.getStandard());
        row.put("minValue", plan.getMinValue());
        row.put("maxValue", plan.getMaxValue());
        row.put("unit", plan.getUnit());
        row.put("sortNo", plan.getSortNo());
        return row;
    }

    private String nextEventNo() {
        long count = excEventMapper.selectCount(null) + 1;
        return "EXC-" + LocalDateTime.now().getYear() + "-" + String.format("%03d", count);
    }
}
