package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.MaterialCreateRequest;
import com.aimes.dto.Requests.MaterialUpdateRequest;
import com.aimes.entity.InvTransaction;
import com.aimes.entity.MatMaterial;
import com.aimes.mapper.InvTransactionMapper;
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
    private final InvTransactionMapper invTransactionMapper;
    private final com.aimes.mapper.SysUserMapper sysUserMapper;
    private final SysNotificationService sysNotificationService;
    private final AuthService authService;

    public Map<String, Object> list(String keyword, String status) {
        List<Map<String, Object>> records = matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                        .and(StringUtils.hasText(keyword), w -> w.like(MatMaterial::getMaterialCode, keyword)
                                .or()
                                .like(MatMaterial::getMaterialName, keyword))
                        .eq(StringUtils.hasText(status), MatMaterial::getAlertStatus, status)
                        .orderByDesc(MatMaterial::getAlertStatus)
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

    public List<Map<String, Object>> options() {
        return matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                        .orderByAsc(MatMaterial::getMaterialCode))
                .stream()
                .map(m -> Map.<String, Object>of(
                        "id", m.getId(),
                        "materialCode", m.getMaterialCode(),
                        "materialName", m.getMaterialName(),
                        "unit", m.getUnit() == null ? "" : m.getUnit(),
                        "stockQty", m.getStockQty()
                ))
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
        if (stockQty.compareTo(BigDecimal.ZERO) > 0) {
            recordTransaction(material.getId(), "in", stockQty, BigDecimal.ZERO, stockQty,
                    "material", material.getId(), "期初入库");
        }
        return toView(material);
    }

    @Transactional
    public Map<String, Object> update(Long id, MaterialUpdateRequest request) {
        MatMaterial material = matMaterialMapper.selectById(id);
        if (material == null) {
            throw new BusinessException("物料不存在");
        }

        BigDecimal stockQty = material.getStockQty();
        BigDecimal beforeQty = stockQty;
        String txnType = null;
        BigDecimal txnQty = null;

        if (request.getStockQty() != null) {
            stockQty = BigDecimal.valueOf(request.getStockQty());
            txnType = "adjust";
            txnQty = stockQty.subtract(beforeQty).abs();
        }
        if (request.getInboundQty() != null) {
            BigDecimal delta = BigDecimal.valueOf(request.getInboundQty());
            stockQty = stockQty.add(delta);
            txnType = delta.compareTo(BigDecimal.ZERO) >= 0 ? "in" : "out";
            txnQty = delta.abs();
        }
        material.setStockQty(stockQty);
        
        boolean wasWarning = "warning".equals(material.getAlertStatus());
        boolean isWarning = stockQty.compareTo(material.getSafetyStock()) < 0;
        
        material.setAlertStatus(isWarning ? "warning" : "normal");
        if (request.getRemark() != null) {
            material.setRemark(request.getRemark());
        }
        matMaterialMapper.updateById(material);

        if (txnType != null && txnQty != null && txnQty.compareTo(BigDecimal.ZERO) > 0) {
            recordTransaction(material.getId(), txnType, txnQty, beforeQty, stockQty,
                    "manual", material.getId(), request.getRemark());
        }

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

    @Transactional
    public void delete(Long id) {
        MatMaterial material = matMaterialMapper.selectById(id);
        if (material == null) {
            throw new BusinessException("物料不存在");
        }
        matMaterialMapper.deleteById(id);
    }

    /**
     * 工单完工按 BOM 扣减库存并写 pick 流水。
     */
    @Transactional
    public void pickForWorkOrder(Long workOrderId, Long productId, int orderQty, List<Map<String, Object>> demandItems) {
        if (workOrderId == null || demandItems == null || demandItems.isEmpty()) {
            return;
        }
        for (Map<String, Object> item : demandItems) {
            Long materialId = ((Number) item.get("materialId")).longValue();
            BigDecimal requiredQty = new BigDecimal(item.get("requiredQty").toString());
            if (requiredQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            MatMaterial material = matMaterialMapper.selectById(materialId);
            if (material == null) {
                continue;
            }
            BigDecimal beforeQty = material.getStockQty();
            BigDecimal afterQty = beforeQty.subtract(requiredQty);
            if (afterQty.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("物料「" + material.getMaterialName() + "」库存不足，无法完工领料");
            }
            material.setStockQty(afterQty);
            material.setAlertStatus(afterQty.compareTo(material.getSafetyStock()) < 0 ? "warning" : "normal");
            matMaterialMapper.updateById(material);
            recordTransaction(materialId, "pick", requiredQty, beforeQty, afterQty,
                    "work_order", workOrderId, "工单完工按BOM领料");
        }
    }

    public List<Map<String, Object>> listTransactions(Long materialId) {
        MatMaterial material = matMaterialMapper.selectById(materialId);
        if (material == null) {
            throw new BusinessException("物料不存在");
        }
        return invTransactionMapper.selectList(new LambdaQueryWrapper<InvTransaction>()
                        .eq(InvTransaction::getMaterialId, materialId)
                        .orderByDesc(InvTransaction::getCreatedTime)
                        .orderByDesc(InvTransaction::getId))
                .stream()
                .map(this::transactionToView)
                .toList();
    }

    private void recordTransaction(Long materialId, String txnType, BigDecimal qty,
                                   BigDecimal beforeQty, BigDecimal afterQty,
                                   String refType, Long refId, String remark) {
        InvTransaction txn = new InvTransaction();
        txn.setMaterialId(materialId);
        txn.setTxnType(txnType);
        txn.setQty(qty);
        txn.setBeforeQty(beforeQty);
        txn.setAfterQty(afterQty);
        txn.setRefType(refType);
        txn.setRefId(refId);
        txn.setOperatorId(authService.currentUser().getId());
        txn.setRemark(remark);
        txn.setCreatedTime(LocalDateTime.now());
        invTransactionMapper.insert(txn);
    }

    private Map<String, Object> transactionToView(InvTransaction txn) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", txn.getId());
        row.put("materialId", txn.getMaterialId());
        row.put("txnType", txn.getTxnType());
        row.put("qty", txn.getQty());
        row.put("beforeQty", txn.getBeforeQty());
        row.put("afterQty", txn.getAfterQty());
        row.put("refType", txn.getRefType());
        row.put("refId", txn.getRefId());
        row.put("operatorId", txn.getOperatorId());
        row.put("remark", txn.getRemark());
        row.put("createdTime", txn.getCreatedTime());
        return row;
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
