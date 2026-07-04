package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.aimes.common.Result;
import com.aimes.dto.Requests.ProcessRouteRejectRequest;
import com.aimes.dto.Requests.ProcessRouteSaveRequest;
import com.aimes.entity.MdmOperationSop;
import com.aimes.service.FileStorageService;
import com.aimes.service.ProcessRouteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "工艺管理")
@RestController
@RequestMapping("/api/process-routes")
@RequiredArgsConstructor
public class ProcessRouteController {

    private final ProcessRouteService processRouteService;
    private final FileStorageService fileStorageService;

    @GetMapping("/default")
    @SaCheckPermission(value = {"生产计划", "工单管理", "工单反馈", "工序进度", "工艺管理", "系统配置"}, mode = SaMode.OR)
    public Result<Map<String, Object>> defaultRoute() {
        return Result.ok(processRouteService.getDefaultRoute());
    }

    @GetMapping("/operations")
    @SaCheckPermission(value = {"生产计划", "工单管理", "工单反馈", "工序进度", "工艺管理", "系统配置"}, mode = SaMode.OR)
    public Result<List<String>> operationNames() {
        return Result.ok(processRouteService.getDefaultOperationNames());
    }

    @GetMapping("/execution/{workOrderId}")
    @SaCheckPermission(value = {"工序进度", "工单反馈", "工单管理", "工艺管理", "系统配置"}, mode = SaMode.OR)
    public Result<Map<String, Object>> executionContext(@PathVariable Long workOrderId) {
        return Result.ok(processRouteService.getExecutionContext(workOrderId));
    }

    @GetMapping
    @SaCheckPermission(value = {"工艺管理", "系统配置", "生产计划", "工单管理"}, mode = SaMode.OR)
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(processRouteService.listRoutes());
    }

    @GetMapping("/{id}")
    @SaCheckPermission(value = {"工艺管理", "系统配置"}, mode = SaMode.OR)
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(processRouteService.getRoute(id));
    }

    @PostMapping
    @SaCheckPermission(value = {"工艺管理", "系统配置"}, mode = SaMode.OR)
    public Result<Map<String, Object>> create(@Valid @RequestBody ProcessRouteSaveRequest request) {
        return Result.ok(processRouteService.createRoute(request));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(value = {"工艺管理", "系统配置"}, mode = SaMode.OR)
    public Result<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody ProcessRouteSaveRequest request) {
        return Result.ok(processRouteService.updateRoute(id, request));
    }

    @PutMapping("/{id}/submit")
    @SaCheckPermission(value = {"工艺管理", "系统配置"}, mode = SaMode.OR)
    public Result<Map<String, Object>> submit(@PathVariable Long id) {
        return Result.ok(processRouteService.submitForApproval(id));
    }

    @PutMapping("/{id}/approve")
    @SaCheckPermission(value = {"工艺审批", "系统配置"}, mode = SaMode.OR)
    public Result<Map<String, Object>> approve(@PathVariable Long id) {
        return Result.ok(processRouteService.approveRoute(id));
    }

    @PutMapping("/{id}/reject")
    @SaCheckPermission(value = {"工艺审批", "系统配置"}, mode = SaMode.OR)
    public Result<Map<String, Object>> reject(@PathVariable Long id, @Valid @RequestBody ProcessRouteRejectRequest request) {
        return Result.ok(processRouteService.rejectRoute(id, request.getReason()));
    }

    @PutMapping("/default")
    @SaCheckPermission(value = {"工艺管理", "系统配置"}, mode = SaMode.OR)
    public Result<Map<String, Object>> updateDefault(@Valid @RequestBody ProcessRouteSaveRequest request) {
        return Result.ok(processRouteService.updateDefaultRoute(request));
    }

    @PostMapping("/{id}/copy")
    @SaCheckPermission(value = {"工艺管理", "系统配置"}, mode = SaMode.OR)
    public Result<Map<String, Object>> copy(@PathVariable Long id) {
        return Result.ok(processRouteService.copyRoute(id));
    }

    @PutMapping("/{id}/default")
    @SaCheckPermission(value = {"工艺管理", "系统配置"}, mode = SaMode.OR)
    public Result<Map<String, Object>> setDefault(@PathVariable Long id) {
        return Result.ok(processRouteService.setDefault(id));
    }

    @PutMapping("/{id}/toggle")
    @SaCheckPermission(value = {"工艺管理", "系统配置"}, mode = SaMode.OR)
    public Result<Map<String, Object>> toggle(@PathVariable Long id) {
        return Result.ok(processRouteService.toggleEnabled(id));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = {"工艺管理", "系统配置"}, mode = SaMode.OR)
    public Result<Void> delete(@PathVariable Long id) {
        processRouteService.deleteRoute(id);
        return Result.ok("删除成功", null);
    }

    @PostMapping("/operations/{operationId}/sop")
    @SaCheckPermission(value = {"工艺管理", "系统配置"}, mode = SaMode.OR)
    public Result<Map<String, Object>> uploadSop(@PathVariable Long operationId,
                                                  @RequestParam("file") MultipartFile file,
                                                  @RequestParam(required = false) String remark) {
        return Result.ok(processRouteService.uploadSop(operationId, file, remark));
    }

    @DeleteMapping("/sop/{sopId}")
    @SaCheckPermission(value = {"工艺管理", "系统配置"}, mode = SaMode.OR)
    public Result<Void> deleteSop(@PathVariable Long sopId) {
        processRouteService.deleteSop(sopId);
        return Result.ok("删除成功", null);
    }

    @GetMapping("/sop/{sopId}/file")
    @SaCheckPermission(value = {"工序进度", "工单反馈", "工艺管理", "系统配置"}, mode = SaMode.OR)
    public ResponseEntity<Resource> downloadSop(@PathVariable Long sopId) {
        MdmOperationSop sop = processRouteService.requireSop(sopId);
        Resource resource = fileStorageService.loadAsResource(sop.getFilePath());
        String contentType = switch (sop.getFileType()) {
            case "pdf" -> "application/pdf";
            case "image" -> MediaType.IMAGE_JPEG_VALUE;
            case "video" -> "video/mp4";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sop.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
