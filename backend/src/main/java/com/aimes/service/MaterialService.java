package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.MaterialCreateRequest;
import com.aimes.dto.Requests.MaterialUpdateRequest;
import com.aimes.entity.MatMaterial;
import com.aimes.mapper.MatMaterialMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MaterialService {

    private static final Pattern MATERIAL_CODE_PATTERN = Pattern.compile("^MAT-(\\d+)$");

    private final MatMaterialMapper matMaterialMapper;
    private final com.aimes.mapper.SysUserMapper sysUserMapper;
    private final SysNotificationService sysNotificationService;

    public Map<String, Object> list(String keyword, String status) {
        List<Map<String, Object>> records = matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                        .and(StringUtils.hasText(keyword), w -> w.like(MatMaterial::getMaterialCode, keyword)
                                .or()
                                .like(MatMaterial::getMaterialName, keyword))
                        .eq(StringUtils.hasText(status), MatMaterial::getAlertStatus, status)
                        .orderByAsc(MatMaterial::getAlertStatus)
                        .orderByAsc(MatMaterial::getStockQty))
                .stream()
                .map(this::toView)
                .toList();

        long warningCount = records.stream().filter(item -> "warning".equals(item.get("alertStatus"))).count();
        return Map.of(
                "summary", Map.of(
                        "total", records.size(),
                        "normal", records.size() - warningCount,
                        "warning", warningCount
                ),
                "records", records
        );
    }

    public List<Map<String, Object>> alerts() {
        return matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                        .eq(MatMaterial::getAlertStatus, "warning")
                        .orderByAsc(MatMaterial::getStockQty))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public Map<String, Object> create(MaterialCreateRequest request) {
        String code = StringUtils.hasText(request.getMaterialCode())
                ? request.getMaterialCode().trim()
                : nextMaterialCode();
        if (matMaterialMapper.selectCount(new LambdaQueryWrapper<MatMaterial>()
                .eq(MatMaterial::getMaterialCode, code)) > 0) {
            throw new BusinessException("物料编号已存在");
        }

        BigDecimal stockQty = request.getStockQty() == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(request.getStockQty());
        BigDecimal safetyStock = BigDecimal.valueOf(request.getSafetyStock());

        MatMaterial material = new MatMaterial();
        material.setMaterialCode(code);
        material.setMaterialName(request.getMaterialName().trim());
        material.setStockQty(stockQty);
        material.setSafetyStock(safetyStock);
        material.setUnit(StringUtils.hasText(request.getUnit()) ? request.getUnit().trim() : "件");
        material.setAlertStatus(stockQty.compareTo(safetyStock) < 0 ? "warning" : "normal");
        material.setRemark(request.getRemark());
        material.setCreatedTime(LocalDateTime.now());
        material.setUpdatedTime(LocalDateTime.now());
        matMaterialMapper.insert(material);
        return toView(material);
    }

    @Transactional
    public Map<String, Object> update(Long id, MaterialUpdateRequest request) {
        MatMaterial material = matMaterialMapper.selectById(id);
        if (material == null) {
            throw new BusinessException("物料不存在");
        }

        BigDecimal stockQty = material.getStockQty();
        if (request.getStockQty() != null) {
            stockQty = BigDecimal.valueOf(request.getStockQty());
        }
        if (request.getInboundQty() != null) {
            stockQty = stockQty.add(BigDecimal.valueOf(request.getInboundQty()));
        }
        material.setStockQty(stockQty);
        
        boolean wasWarning = "warning".equals(material.getAlertStatus());
        boolean isWarning = stockQty.compareTo(material.getSafetyStock()) < 0;
        
        material.setAlertStatus(isWarning ? "warning" : "normal");
        if (request.getRemark() != null) {
            material.setRemark(request.getRemark());
        }
        matMaterialMapper.updateById(material);

        if (isWarning && !wasWarning) {
            BigDecimal gap = material.getSafetyStock().subtract(stockQty);
            List<com.aimes.entity.SysUser> usersToNotify = sysUserMapper.selectList(new LambdaQueryWrapper<com.aimes.entity.SysUser>()
                    .in(com.aimes.entity.SysUser::getRole, java.util.List.of("admin", "supervisor")));
            for (com.aimes.entity.SysUser recipient : usersToNotify) {
                sysNotificationService.createNotification(
                        recipient.getId(),
                        "库存预警",
                        "物料 “" + material.getMaterialName() + "” 库存报警（缺口 " + gap.intValue() + " 件）",
                        "warning",
                        "/materials"
                );
            }
        }
        
        return toView(material);
    }

    private Map<String, Object> toView(MatMaterial material) {
        BigDecimal gap = material.getSafetyStock().subtract(material.getStockQty());
        if (gap.compareTo(BigDecimal.ZERO) < 0) {
            gap = BigDecimal.ZERO;
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", material.getId());
        row.put("materialCode", material.getMaterialCode());
        row.put("materialName", material.getMaterialName());
        row.put("stockQty", material.getStockQty());
        row.put("safetyStock", material.getSafetyStock());
        row.put("gap", gap);
        row.put("unit", material.getUnit());
        row.put("alertStatus", material.getAlertStatus());
        row.put("remark", material.getRemark());
        row.put("updatedTime", material.getUpdatedTime());
        return row;
    }

    private String nextMaterialCode() {
        List<MatMaterial> materials = matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                .likeRight(MatMaterial::getMaterialCode, "MAT-"));
        int maxSeq = 0;
        for (MatMaterial material : materials) {
            if (!StringUtils.hasText(material.getMaterialCode())) {
                continue;
            }
            Matcher matcher = MATERIAL_CODE_PATTERN.matcher(material.getMaterialCode().trim());
            if (matcher.matches()) {
                maxSeq = Math.max(maxSeq, Integer.parseInt(matcher.group(1)));
            }
        }
        return "MAT-" + String.format("%03d", maxSeq + 1);
    }
}
