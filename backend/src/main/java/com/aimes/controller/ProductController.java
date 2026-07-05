package com.aimes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.aimes.common.Result;
import com.aimes.dto.Requests.BomSaveRequest;
import com.aimes.dto.Requests.ProductSaveRequest;
import com.aimes.service.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "产品管理")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @SaCheckPermission("产品管理")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status) {
        return Result.ok(productService.list(page, size, keyword, status));
    }

    @GetMapping("/options")
    @SaCheckPermission(value = {"产品管理", "生产计划", "工艺管理"}, mode = SaMode.OR)
    public Result<List<Map<String, Object>>> options() {
        return Result.ok(productService.options());
    }

    @GetMapping("/{id}")
    @SaCheckPermission("产品管理")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(productService.detail(id));
    }

    @PostMapping
    @SaCheckPermission("产品管理")
    public Result<Map<String, Object>> create(@Valid @RequestBody ProductSaveRequest request) {
        return Result.ok(productService.create(request));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("产品管理")
    public Result<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody ProductSaveRequest request) {
        return Result.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("产品管理")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.ok("删除成功", null);
    }

    @GetMapping("/{id}/bom")
    @SaCheckPermission("产品管理")
    public Result<Map<String, Object>> getBom(@PathVariable Long id) {
        return Result.ok(productService.getBom(id));
    }

    @GetMapping("/{id}/transactions")
    @SaCheckPermission("产品管理")
    public Result<List<Map<String, Object>>> transactions(@PathVariable Long id) {
        return Result.ok(productService.listTransactions(id));
    }

    @PutMapping("/{id}/bom")
    @SaCheckPermission("产品管理")
    public Result<Map<String, Object>> saveBom(@PathVariable Long id, @RequestBody BomSaveRequest request) {
        return Result.ok(productService.saveBom(id, request));
    }
}
