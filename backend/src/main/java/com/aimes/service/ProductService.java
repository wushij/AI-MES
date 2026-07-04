package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.BomSaveRequest;
import com.aimes.dto.Requests.ProductSaveRequest;
import com.aimes.entity.MatMaterial;
import com.aimes.entity.MdmBom;
import com.aimes.entity.MdmBomItem;
import com.aimes.entity.MdmProduct;
import com.aimes.mapper.MatMaterialMapper;
import com.aimes.mapper.MdmBomItemMapper;
import com.aimes.mapper.MdmBomMapper;
import com.aimes.mapper.MdmProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Pattern PRODUCT_CODE_PATTERN = Pattern.compile("^PRD-(\\d+)$");

    private final MdmProductMapper mdmProductMapper;
    private final MdmBomMapper mdmBomMapper;
    private final MdmBomItemMapper mdmBomItemMapper;
    private final MatMaterialMapper matMaterialMapper;

    public Map<String, Object> list(long current, long size, String keyword, String status) {
        LambdaQueryWrapper<MdmProduct> wrapper = new LambdaQueryWrapper<MdmProduct>()
                .and(StringUtils.hasText(keyword), w -> w.like(MdmProduct::getProductCode, keyword)
                        .or()
                        .like(MdmProduct::getProductName, keyword))
                .eq(StringUtils.hasText(status), MdmProduct::getStatus, status)
                .orderByDesc(MdmProduct::getUpdatedTime);
        Page<MdmProduct> page = mdmProductMapper.selectPage(new Page<>(current, size), wrapper);
        List<Map<String, Object>> records = page.getRecords().stream().map(this::toView).toList();
        return Map.of("total", page.getTotal(), "records", records);
    }

    public Map<String, Object> detail(Long id) {
        MdmProduct product = requireProduct(id);
        Map<String, Object> view = toView(product);
        view.put("bom", getBomView(id));
        view.put("hasBom", hasActiveBom(id));
        return view;
    }

    public List<Map<String, Object>> options() {
        return mdmProductMapper.selectList(new LambdaQueryWrapper<MdmProduct>()
                        .eq(MdmProduct::getStatus, "active")
                        .orderByAsc(MdmProduct::getProductCode))
                .stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.getId(),
                        "productCode", p.getProductCode(),
                        "productName", p.getProductName(),
                        "unit", p.getUnit() == null ? "件" : p.getUnit()
                ))
                .toList();
    }

    @Transactional
    public Map<String, Object> create(ProductSaveRequest request) {
        String code = StringUtils.hasText(request.getProductCode())
                ? request.getProductCode().trim()
                : nextProductCode();
        if (mdmProductMapper.selectCount(new LambdaQueryWrapper<MdmProduct>()
                .eq(MdmProduct::getProductCode, code)) > 0) {
            throw new BusinessException("产品编码已存在");
        }
        MdmProduct product = new MdmProduct();
        product.setProductCode(code);
        product.setProductName(request.getProductName().trim());
        product.setSpec(request.getSpec());
        product.setUnit(StringUtils.hasText(request.getUnit()) ? request.getUnit().trim() : "件");
        product.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "active");
        product.setRemark(request.getRemark());
        product.setCreatedTime(LocalDateTime.now());
        product.setUpdatedTime(LocalDateTime.now());
        mdmProductMapper.insert(product);
        return toView(product);
    }

    @Transactional
    public Map<String, Object> update(Long id, ProductSaveRequest request) {
        MdmProduct product = requireProduct(id);
        product.setProductName(request.getProductName().trim());
        product.setSpec(request.getSpec());
        if (StringUtils.hasText(request.getUnit())) {
            product.setUnit(request.getUnit().trim());
        }
        if (StringUtils.hasText(request.getStatus())) {
            product.setStatus(request.getStatus());
        }
        product.setRemark(request.getRemark());
        product.setUpdatedTime(LocalDateTime.now());
        mdmProductMapper.updateById(product);
        return toView(product);
    }

    @Transactional
    public void delete(Long id) {
        requireProduct(id);
        mdmProductMapper.deleteById(id);
    }

    public Map<String, Object> getBom(Long productId) {
        requireProduct(productId);
        return getBomView(productId);
    }

    @Transactional
    public Map<String, Object> saveBom(Long productId, BomSaveRequest request) {
        requireProduct(productId);
        MdmBom bom = mdmBomMapper.selectOne(new LambdaQueryWrapper<MdmBom>()
                .eq(MdmBom::getProductId, productId)
                .eq(MdmBom::getIsDefault, 1)
                .last("limit 1"));
        if (bom == null) {
            bom = new MdmBom();
            bom.setProductId(productId);
            bom.setVersion(StringUtils.hasText(request.getVersion()) ? request.getVersion() : "V1.0");
            bom.setStatus("active");
            bom.setIsDefault(1);
            bom.setRemark(request.getRemark());
            bom.setCreatedTime(LocalDateTime.now());
            bom.setUpdatedTime(LocalDateTime.now());
            mdmBomMapper.insert(bom);
        } else {
            if (StringUtils.hasText(request.getVersion())) {
                bom.setVersion(request.getVersion());
            }
            bom.setRemark(request.getRemark());
            bom.setUpdatedTime(LocalDateTime.now());
            mdmBomMapper.updateById(bom);
        }

        mdmBomItemMapper.delete(new LambdaQueryWrapper<MdmBomItem>().eq(MdmBomItem::getBomId, bom.getId()));
        if (request.getItems() != null) {
            for (BomSaveRequest.BomItemRequest item : request.getItems()) {
                if (item.getMaterialId() == null) {
                    continue;
                }
                MatMaterial material = matMaterialMapper.selectById(item.getMaterialId());
                if (material == null) {
                    throw new BusinessException("物料不存在: " + item.getMaterialId());
                }
                MdmBomItem row = new MdmBomItem();
                row.setBomId(bom.getId());
                row.setMaterialId(item.getMaterialId());
                row.setQty(item.getQty() == null ? BigDecimal.ONE : BigDecimal.valueOf(item.getQty()));
                row.setUnit(StringUtils.hasText(item.getUnit()) ? item.getUnit() : material.getUnit());
                row.setLossRate(item.getLossRate() == null ? BigDecimal.ZERO : BigDecimal.valueOf(item.getLossRate()));
                row.setRemark(item.getRemark());
                row.setCreatedTime(LocalDateTime.now());
                row.setUpdatedTime(LocalDateTime.now());
                mdmBomItemMapper.insert(row);
            }
        }
        return getBomView(productId);
    }

    public boolean hasActiveBom(Long productId) {
        if (productId == null) {
            return false;
        }
        MdmBom bom = mdmBomMapper.selectOne(new LambdaQueryWrapper<MdmBom>()
                .eq(MdmBom::getProductId, productId)
                .eq(MdmBom::getStatus, "active")
                .eq(MdmBom::getIsDefault, 1)
                .last("limit 1"));
        if (bom == null) {
            return false;
        }
        return mdmBomItemMapper.selectCount(new LambdaQueryWrapper<MdmBomItem>()
                .eq(MdmBomItem::getBomId, bom.getId())) > 0;
    }

    public MdmProduct findByName(String productName) {
        if (!StringUtils.hasText(productName)) {
            return null;
        }
        return mdmProductMapper.selectOne(new LambdaQueryWrapper<MdmProduct>()
                .eq(MdmProduct::getProductName, productName.trim())
                .last("limit 1"));
    }

    public Map<String, Object> getProductSummary(Long productId) {
        if (productId == null) {
            return Map.of();
        }
        return toView(requireProduct(productId));
    }

    /** 按工单数量计算 BOM 理论用量 */
    public List<Map<String, Object>> computeBomDemand(Long productId, int orderQty) {
        if (productId == null || orderQty <= 0) {
            return List.of();
        }
        Map<String, Object> bom = getBomView(productId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) bom.getOrDefault("items", List.of());
        List<Map<String, Object>> demand = new ArrayList<>();
        for (Map<String, Object> item : items) {
            BigDecimal unitQty = item.get("qty") == null ? BigDecimal.ZERO : new BigDecimal(item.get("qty").toString());
            BigDecimal lossRate = item.get("lossRate") == null ? BigDecimal.ZERO : new BigDecimal(item.get("lossRate").toString());
            BigDecimal multiplier = BigDecimal.ONE.add(lossRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            BigDecimal requiredQty = unitQty.multiply(BigDecimal.valueOf(orderQty)).multiply(multiplier)
                    .setScale(4, RoundingMode.HALF_UP);
            Map<String, Object> row = new LinkedHashMap<>(item);
            row.put("unitQty", unitQty);
            row.put("orderQty", orderQty);
            row.put("requiredQty", requiredQty);
            demand.add(row);
        }
        return demand;
    }

    private Map<String, Object> getBomView(Long productId) {
        MdmBom bom = mdmBomMapper.selectOne(new LambdaQueryWrapper<MdmBom>()
                .eq(MdmBom::getProductId, productId)
                .eq(MdmBom::getIsDefault, 1)
                .last("limit 1"));
        if (bom == null) {
            return Map.of("items", List.of());
        }
        List<MdmBomItem> items = mdmBomItemMapper.selectList(new LambdaQueryWrapper<MdmBomItem>()
                .eq(MdmBomItem::getBomId, bom.getId())
                .orderByAsc(MdmBomItem::getId));
        List<Map<String, Object>> itemViews = new ArrayList<>();
        for (MdmBomItem item : items) {
            MatMaterial material = matMaterialMapper.selectById(item.getMaterialId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("materialId", item.getMaterialId());
            row.put("materialCode", material == null ? null : material.getMaterialCode());
            row.put("materialName", material == null ? null : material.getMaterialName());
            row.put("qty", item.getQty());
            row.put("unit", item.getUnit());
            row.put("lossRate", item.getLossRate());
            row.put("remark", item.getRemark());
            itemViews.add(row);
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", bom.getId());
        view.put("productId", bom.getProductId());
        view.put("version", bom.getVersion());
        view.put("status", bom.getStatus());
        view.put("remark", bom.getRemark());
        view.put("items", itemViews);
        return view;
    }

    private MdmProduct requireProduct(Long id) {
        MdmProduct product = mdmProductMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        return product;
    }

    private Map<String, Object> toView(MdmProduct product) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", product.getId());
        row.put("productCode", product.getProductCode());
        row.put("productName", product.getProductName());
        row.put("spec", product.getSpec());
        row.put("unit", product.getUnit());
        row.put("status", product.getStatus());
        row.put("remark", product.getRemark());
        row.put("hasBom", hasActiveBom(product.getId()));
        row.put("createdTime", product.getCreatedTime());
        row.put("updatedTime", product.getUpdatedTime());
        return row;
    }

    private String nextProductCode() {
        List<MdmProduct> products = mdmProductMapper.selectList(new LambdaQueryWrapper<MdmProduct>()
                .likeRight(MdmProduct::getProductCode, "PRD-"));
        int maxSeq = 0;
        for (MdmProduct product : products) {
            if (!StringUtils.hasText(product.getProductCode())) {
                continue;
            }
            Matcher matcher = PRODUCT_CODE_PATTERN.matcher(product.getProductCode().trim());
            if (matcher.matches()) {
                maxSeq = Math.max(maxSeq, Integer.parseInt(matcher.group(1)));
            }
        }
        return "PRD-" + String.format("%03d", maxSeq + 1);
    }
}
