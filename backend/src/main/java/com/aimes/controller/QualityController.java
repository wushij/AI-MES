package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.aimes.common.Result;
import com.aimes.dto.Requests.InspectionSubmitRequest;
import com.aimes.service.QmsInspectionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "质量管理")
@RestController
@RequestMapping("/api/quality")
@RequiredArgsConstructor
public class QualityController {

    private final QmsInspectionService qmsInspectionService;

    @GetMapping("/inspection-plans")
    @SaCheckPermission(value = {"工序进度", "工单反馈", "工艺管理"}, mode = SaMode.OR)
    public Result<List<Map<String, Object>>> inspectionPlans(@RequestParam Long operationId) {
        return Result.ok(qmsInspectionService.listPlansByOperation(operationId));
    }

    @GetMapping("/inspection-plans/by-process")
    @SaCheckPermission(value = {"工序进度", "工单反馈"}, mode = SaMode.OR)
    public Result<List<Map<String, Object>>> inspectionPlansByProcess(@RequestParam Long workOrderId,
                                                                        @RequestParam String processName) {
        return Result.ok(qmsInspectionService.listPlansByProcessName(workOrderId, processName));
    }

    @PostMapping("/inspection-records")
    @SaCheckPermission(value = {"工序进度", "工单反馈"}, mode = SaMode.OR)
    public Result<Map<String, Object>> submitInspections(@Valid @RequestBody InspectionSubmitRequest request) {
        return Result.ok(qmsInspectionService.submitInspections(request));
    }
}
