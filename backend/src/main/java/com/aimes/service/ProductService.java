package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.BomSaveRequest;
import com.aimes.dto.Requests.ProductSaveRequest;
import com.aimes.entity.InvTransaction;
import com.aimes.entity.MdmProduct;
import com.aimes.mapper.InvTransactionMapper;
import com.aimes.mapper.MdmProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.context.annotation.Lazy;
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
public class ProductService {

    private static final Pattern PRODUCT_CODE_PATTERN = Pattern.compile("^PRD-(\\d+)$");

    private final MdmProductMapper mdmProductMapper;
    private final InvTransactionMapper invTransactionMapper;
    private final AuthService authService;
    private final ProcessRouteService processRouteService;

    public ProductService(MdmProductMapper mdmProductMapper,
                          InvTransactionMapper invTransactionMapper,
                          AuthService authService,
                          @Lazy ProcessRouteService processRouteService) {
        this.mdmProductMapper = mdmProductMapper;
        this.invTransactionMapper = invTransactionMapper;
        this.authService = authService;
        this.processRouteService = processRouteService;
    }

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

    public Map<String, Object> summary() {
        List<MdmProduct> products = mdmProductMapper.selectList(new LambdaQueryWrapper<MdmProduct>()
                .orderByAsc(MdmProduct::getProductCode));
        BigDecimal totalStock = products.stream()
                .map(p -> p.getStockQty() != null ? p.getStockQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCount", products.size());
        summary.put("activeCount", products.stream().filter(p -> "active".equals(p.getStatus())).count());
        summary.put("inactiveCount", products.stream().filter(p -> "inactive".equals(p.getStatus())).count());
        summary.put("totalStockQty", totalStock);
        summary.put("withBomCount", products.stream().filter(p -> hasActiveBom(p.getId())).count());
        summary.put("products", products.stream().map(this::toBriefView).toList());
        return summary;
    }

    public static String productStatusLabel(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case "active" -> "启用";
            case "inactive" -> "停用";
            default -> status;
        };
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
        product.setStockQty(BigDecimal.ZERO);
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
        throw new BusinessException("产品 BOM 由工艺路线工序物料自动汇总，请在「工艺管理」中维护各工序物料");
    }

    public boolean hasActiveBom(Long productId) {
        if (productId == null) {
            return false;
        }
        MdmProduct product = mdmProductMapper.selectById(productId);
        if (product == null) {
            return false;
        }
        return processRouteService.hasRoutingMaterials(productId, product.getProductName());
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

    /** 按生产数量计算理论用料（工艺工序物料汇总 × 数量） */
    public List<Map<String, Object>> computeBomDemand(Long productId, int orderQty) {
        if (productId == null || orderQty <= 0) {
            return List.of();
        }
        MdmProduct product = mdmProductMapper.selectById(productId);
        if (product == null) {
            return List.of();
        }
        var ctx = processRouteService.resolveRouting(productId, product.getProductName());
        if (ctx.routing() == null) {
            return List.of();
        }
        return processRouteService.computeRoutingMaterialDemand(ctx.routing().getId(), orderQty);
    }

    /** 工单完工成品入库（同一工单仅入库一次） */
    @Transactional
    public void receiveFromWorkOrder(Long workOrderId, Long productId, int qty, String orderNo) {
        if (workOrderId == null || productId == null || qty <= 0) {
            return;
        }
        if (hasProductReceiveForWorkOrder(workOrderId)) {
            return;
        }
        MdmProduct product = requireProduct(productId);
        BigDecimal receiveQty = BigDecimal.valueOf(qty);
        BigDecimal beforeQty = product.getStockQty() != null ? product.getStockQty() : BigDecimal.ZERO;
        BigDecimal afterQty = beforeQty.add(receiveQty);
        product.setStockQty(afterQty);
        product.setUpdatedTime(LocalDateTime.now());
        mdmProductMapper.updateById(product);

        String woLabel = StringUtils.hasText(orderNo) ? orderNo : String.valueOf(workOrderId);
        recordProductTransaction(productId, "in", receiveQty, beforeQty, afterQty, "work_order", workOrderId,
                "工单完工入库：工单 " + woLabel + "，入库 " + qty + " 件");
    }

    public List<Map<String, Object>> listTransactions(Long productId) {
        requireProduct(productId);
        return invTransactionMapper.selectList(new LambdaQueryWrapper<InvTransaction>()
                        .eq(InvTransaction::getProductId, productId)
                        .orderByDesc(InvTransaction::getCreatedTime)
                        .orderByDesc(InvTransaction::getId))
                .stream()
                .map(this::transactionToView)
                .toList();
    }

    private boolean hasProductReceiveForWorkOrder(Long workOrderId) {
        return invTransactionMapper.selectCount(new LambdaQueryWrapper<InvTransaction>()
                .eq(InvTransaction::getRefType, "work_order")
                .eq(InvTransaction::getRefId, workOrderId)
                .eq(InvTransaction::getTxnType, "in")
                .isNotNull(InvTransaction::getProductId)) > 0;
    }

    private void recordProductTransaction(Long productId, String txnType, BigDecimal qty,
                                          BigDecimal beforeQty, BigDecimal afterQty,
                                          String refType, Long refId, String remark) {
        InvTransaction txn = new InvTransaction();
        txn.setProductId(productId);
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
        row.put("productId", txn.getProductId());
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

    private Map<String, Object> getBomView(Long productId) {
        MdmProduct product = mdmProductMapper.selectById(productId);
        if (product == null) {
            return Map.of("items", List.of(), "details", List.of(), "source", "routing", "editable", false);
        }
        return processRouteService.buildProductBomView(productId, product.getProductName());
    }

    private MdmProduct requireProduct(Long id) {
        MdmProduct product = mdmProductMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        return product;
    }

    private Map<String, Object> toBriefView(MdmProduct product) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", product.getId());
        row.put("productCode", product.getProductCode());
        row.put("productName", product.getProductName());
        row.put("spec", product.getSpec());
        row.put("unit", product.getUnit() == null ? "件" : product.getUnit());
        row.put("stockQty", product.getStockQty() != null ? product.getStockQty() : BigDecimal.ZERO);
        row.put("status", product.getStatus());
        row.put("statusLabel", productStatusLabel(product.getStatus()));
        row.put("hasBom", hasActiveBom(product.getId()));
        return row;
    }

    private Map<String, Object> toView(MdmProduct product) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", product.getId());
        row.put("productCode", product.getProductCode());
        row.put("productName", product.getProductName());
        row.put("spec", product.getSpec());
        row.put("unit", product.getUnit());
        row.put("stockQty", product.getStockQty() != null ? product.getStockQty() : BigDecimal.ZERO);
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
