package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.common.OperationLog;
import com.aimes.common.OperationLogRunner;
import com.aimes.dto.Requests.ProcessMaterialItem;
import com.aimes.dto.Requests.ProcessOperationItem;
import com.aimes.dto.Requests.ProcessParameterItem;
import com.aimes.dto.Requests.ProcessRouteSaveRequest;
import com.aimes.entity.DevDevice;
import com.aimes.entity.DevDeviceCategory;
import com.aimes.entity.MatMaterial;
import com.aimes.entity.MdmOperation;
import com.aimes.entity.MdmOperationDevice;
import com.aimes.entity.MdmOperationMaterial;
import com.aimes.entity.MdmOperationParam;
import com.aimes.entity.MdmOperationSop;
import com.aimes.entity.MdmRouting;
import com.aimes.entity.MdmRoutingHistory;
import com.aimes.entity.ProdProcessRecord;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.mapper.DevDeviceCategoryMapper;
import com.aimes.mapper.DevDeviceMapper;
import com.aimes.mapper.MatMaterialMapper;
import com.aimes.mapper.MdmOperationDeviceMapper;
import com.aimes.mapper.MdmOperationMapper;
import com.aimes.mapper.MdmOperationMaterialMapper;
import com.aimes.mapper.MdmOperationParamMapper;
import com.aimes.mapper.MdmOperationSopMapper;
import com.aimes.mapper.MdmRoutingHistoryMapper;
import com.aimes.mapper.MdmRoutingMapper;
import com.aimes.mapper.ProdProcessRecordMapper;
import com.aimes.mapper.ProdWorkOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProcessRouteService {

    private static final List<String> FALLBACK_OPERATIONS = List.of("备料", "装配", "检测", "包装");

    private final MdmRoutingMapper mdmRoutingMapper;
    private final MdmOperationMapper mdmOperationMapper;
    private final MdmOperationParamMapper mdmOperationParamMapper;
    private final MdmOperationSopMapper mdmOperationSopMapper;
    private final MdmOperationDeviceMapper mdmOperationDeviceMapper;
    private final MdmOperationMaterialMapper mdmOperationMaterialMapper;
    private final MdmRoutingHistoryMapper mdmRoutingHistoryMapper;
    private final ProdWorkOrderMapper prodWorkOrderMapper;
    private final ProdProcessRecordMapper prodProcessRecordMapper;
    private final DevDeviceMapper devDeviceMapper;
    private final DevDeviceCategoryMapper devDeviceCategoryMapper;
    private final MatMaterialMapper matMaterialMapper;
    private final AuthService authService;
    private final FileStorageService fileStorageService;
    private final RoleService roleService;
    private final OperationLogRunner operationLogRunner;
    private final ProductService productService;

    public List<String> getDefaultOperationNames() {
        return resolveRouting(null).operations().stream().map(MdmOperation::getOperationName).toList();
    }

    public Map<String, Object> getDefaultRoute() {
        RoutingContext ctx = resolveRouting(null);
        if (ctx.routing() == null) {
            return fallbackRouteView();
        }
        return toRouteView(ctx.routing(), true);
    }

    public List<Map<String, Object>> listRoutes() {
        return mdmRoutingMapper.selectList(new LambdaQueryWrapper<MdmRouting>()
                        .orderByDesc(MdmRouting::getIsDefault)
                        .orderByAsc(MdmRouting::getId))
                .stream()
                .map(r -> toRouteView(r, false))
                .toList();
    }

    public Map<String, Object> getRoute(Long id) {
        return toRouteView(requireRouting(id), true);
    }

    public Map<String, Object> getExecutionContext(Long workOrderId) {
        ProdWorkOrder order = prodWorkOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        RoutingContext ctx = order.getRoutingId() != null
                ? loadRoutingContext(order.getRoutingId())
                : resolveRouting(order.getProductId(), order.getProductName());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workOrderId", workOrderId);
        result.put("routingId", order.getRoutingId());
        result.put("routeVersion", order.getRouteVersion());
        result.put("productId", order.getProductId());
        result.put("productName", order.getProductName());
        List<Map<String, Object>> operations = new ArrayList<>();
        for (MdmOperation operation : ctx.operations()) {
            Map<String, Object> row = operationRow(operation, true);
            operations.add(row);
        }
        result.put("operations", operations);
        return result;
    }

    @Transactional
    public Map<String, Object> createRoute(ProcessRouteSaveRequest request) {
        if (hasOperations(request.getOperations())) {
            validateOperations(request.getOperations());
        } else if (requiresOperations(request.getSaveMode())) {
            throw new BusinessException("至少配置一道工序");
        }
        MdmRouting routing = new MdmRouting();
        routing.setRouteCode(StringUtils.hasText(request.getRouteCode()) ? request.getRouteCode().trim() : nextRouteCode());
        ensureUniqueCode(routing.getRouteCode(), null);
        routing.setRouteName(request.getRouteName().trim());
        applyProductBinding(routing, request.getProductId(), request.getProductName());
        routing.setVersion("V1.0");
        routing.setStatus(resolveInitialStatus(request.getSaveMode()));
        routing.setIsDefault(0);
        routing.setEnabled("published".equals(routing.getStatus()) ? 1 : 0);
        routing.setRemark(request.getRemark());
        routing.setCreatedTime(LocalDateTime.now());
        routing.setUpdatedTime(LocalDateTime.now());
        mdmRoutingMapper.insert(routing);
        if (hasOperations(request.getOperations())) {
            saveOperations(routing.getId(), request.getOperations());
        }
        String opSummary = summarizeOperations(request.getOperations());
        recordHistory(routing.getId(), routing.getVersion(), "create",
                "新建工艺路线「" + routing.getRouteName() + "」" + opSummary);
        if ("published".equals(routing.getStatus())) {
            recordHistory(routing.getId(), routing.getVersion(), "publish",
                    "发布工艺 " + routing.getVersion() + opSummary);
        } else if ("pending_approval".equals(routing.getStatus())) {
            recordHistory(routing.getId(), routing.getVersion(), "submit", "提交工艺审批");
        }
        return toRouteView(routing, true);
    }

    @Transactional
    public Map<String, Object> updateRoute(Long id, ProcessRouteSaveRequest request) {
        if (hasOperations(request.getOperations())) {
            validateOperations(request.getOperations());
        } else if (requiresOperations(request.getSaveMode())) {
            throw new BusinessException("至少配置一道工序");
        }
        MdmRouting routing = requireRouting(id);
        RoutingSnapshot before = captureRoutingSnapshot(routing);
        if (StringUtils.hasText(request.getRouteCode()) && !request.getRouteCode().equals(routing.getRouteCode())) {
            ensureUniqueCode(request.getRouteCode(), id);
            routing.setRouteCode(request.getRouteCode().trim());
        }
        routing.setRouteName(request.getRouteName().trim());
        applyProductBinding(routing, request.getProductId(), request.getProductName());
        routing.setRemark(request.getRemark());
        routing.setUpdatedTime(LocalDateTime.now());
        SaveModeAction saveAction = applySaveMode(routing, request.getSaveMode());
        mdmRoutingMapper.updateById(routing);
        if (hasOperations(request.getOperations())) {
            saveOperations(routing.getId(), request.getOperations());
        }
        String changeSummary = buildChangeSummary(before, routing, request);
        recordSaveHistory(routing, saveAction, changeSummary);
        return toRouteView(routing, true);
    }

    @Transactional
    public Map<String, Object> submitForApproval(Long id) {
        MdmRouting routing = requireRouting(id);
        if (!"draft".equals(routing.getStatus()) && !"rejected".equals(routing.getStatus())) {
            throw new BusinessException("仅草稿或已驳回工艺可提交审批");
        }
        routing.setStatus("pending_approval");
        routing.setRejectedReason(null);
        routing.setUpdatedTime(LocalDateTime.now());
        mdmRoutingMapper.updateById(routing);
        recordHistory(routing.getId(), routing.getVersion(), "submit", "提交工艺审批");
        return toRouteView(routing, true);
    }

    @Transactional
    @OperationLog(module = "工艺管理", action = "审批通过")
    public Map<String, Object> approveRoute(Long id) {
        return operationLogRunner.runUnchecked("工艺管理", "审批通过", "approveRoute", new Object[]{id},
                () -> approveRouteInternal(id));
    }

    private Map<String, Object> approveRouteInternal(Long id) {
        ensureApprovalPermission();
        MdmRouting routing = requireRouting(id);
        if (!"pending_approval".equals(routing.getStatus())) {
            throw new BusinessException("当前工艺不在待审批状态");
        }
        routing.setVersion(bumpVersion(routing.getVersion()));
        routing.setStatus("published");
        routing.setEnabled(1);
        routing.setRejectedReason(null);
        routing.setUpdatedTime(LocalDateTime.now());
        mdmRoutingMapper.updateById(routing);
        recordHistory(routing.getId(), routing.getVersion(), "approve", "审批通过并发布 " + routing.getVersion());
        return toRouteView(routing, true);
    }

    @Transactional
    @OperationLog(module = "工艺管理", action = "审批驳回")
    public Map<String, Object> rejectRoute(Long id, String reason) {
        return operationLogRunner.runUnchecked("工艺管理", "审批驳回", "rejectRoute", new Object[]{id, reason},
                () -> rejectRouteInternal(id, reason));
    }

    private Map<String, Object> rejectRouteInternal(Long id, String reason) {
        ensureApprovalPermission();
        MdmRouting routing = requireRouting(id);
        if (!"pending_approval".equals(routing.getStatus())) {
            throw new BusinessException("当前工艺不在待审批状态");
        }
        routing.setStatus("rejected");
        routing.setEnabled(0);
        routing.setRejectedReason(reason.trim());
        routing.setUpdatedTime(LocalDateTime.now());
        mdmRoutingMapper.updateById(routing);
        recordHistory(routing.getId(), routing.getVersion(), "reject", "审批驳回：" + reason.trim());
        return toRouteView(routing, true);
    }

    @Transactional
    public Map<String, Object> updateDefaultRoute(ProcessRouteSaveRequest request) {
        MdmRouting routing = getDefaultRoutingEntity();
        if (routing == null) {
            request.setRouteCode("ROUTE-STD");
            request.setSaveMode("publish");
            Map<String, Object> created = createRoute(request);
            Long newId = ((Number) created.get("id")).longValue();
            setDefaultInternal(newId);
            return getRoute(newId);
        }
        request.setSaveMode("publish");
        return updateRoute(routing.getId(), request);
    }

    @Transactional
    @OperationLog(module = "工艺管理", action = "删除")
    public void deleteRoute(Long id) {
        operationLogRunner.runVoid("工艺管理", "删除", "deleteRoute", new Object[]{id}, () -> deleteRouteInternal(id));
    }

    private void deleteRouteInternal(Long id) {
        MdmRouting routing = requireRouting(id);
        if (routing.getIsDefault() != null && routing.getIsDefault() == 1) {
            throw new BusinessException("默认工艺路线不能删除，请先指定其他默认路线");
        }
        long workOrders = prodWorkOrderMapper.selectCount(new LambdaQueryWrapper<ProdWorkOrder>()
                .eq(ProdWorkOrder::getRoutingId, id));
        if (workOrders > 0) {
            throw new BusinessException("工艺已被工单引用，不能删除");
        }
        mdmRoutingMapper.deleteById(id);
    }

    @Transactional
    public Map<String, Object> copyRoute(Long id) {
        Map<String, Object> source = getRoute(id);
        ProcessRouteSaveRequest request = new ProcessRouteSaveRequest();
        request.setRouteName(source.get("routeName") + " (副本)");
        if (source.get("productId") != null) {
            request.setProductId(((Number) source.get("productId")).longValue());
        }
        request.setProductName((String) source.get("productName"));
        request.setRemark((String) source.get("remark"));
        request.setSaveMode("draft");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ops = (List<Map<String, Object>>) source.get("operations");
        // 复制时必须清空原工序 ID，否则 createRoute 会误当作更新并校验 routingId 导致「工序不存在」
        request.setOperations(ops.stream().map(op -> {
            ProcessOperationItem item = mapOperationItem(op);
            item.setId(null);
            return item;
        }).toList());
        Map<String, Object> created = createRoute(request);
        Long newRoutingId = ((Number) created.get("id")).longValue();
        Map<Integer, Long> newOpBySeq = listOperations(newRoutingId).stream()
                .collect(Collectors.toMap(MdmOperation::getSeqNo, MdmOperation::getId, (a, b) -> a));
        for (MdmOperation srcOp : listOperations(id)) {
            Long newOpId = newOpBySeq.get(srcOp.getSeqNo());
            if (newOpId != null) {
                copyOperationSops(srcOp.getId(), newOpId);
            }
        }
        return getRoute(newRoutingId);
    }

    @Transactional
    public Map<String, Object> setDefault(Long id) {
        MdmRouting routing = requireRouting(id);
        if (!"published".equals(routing.getStatus())) {
            throw new BusinessException("仅已发布工艺可设为默认");
        }
        setDefaultInternal(id);
        return toRouteView(requireRouting(id), true);
    }

    @Transactional
    public Map<String, Object> toggleEnabled(Long id) {
        MdmRouting routing = requireRouting(id);
        if (!"published".equals(routing.getStatus()) && !"disabled".equals(routing.getStatus())) {
            throw new BusinessException("仅已发布工艺可启停");
        }
        if (routing.getIsDefault() != null && routing.getIsDefault() == 1 && routing.getEnabled() == 1) {
            throw new BusinessException("默认工艺路线不能停用");
        }
        routing.setEnabled(routing.getEnabled() != null && routing.getEnabled() == 1 ? 0 : 1);
        routing.setStatus(routing.getEnabled() == 1 ? "published" : "disabled");
        routing.setUpdatedTime(LocalDateTime.now());
        mdmRoutingMapper.updateById(routing);
        recordHistory(routing.getId(), routing.getVersion(), "status", routing.getEnabled() == 1 ? "启用工艺路线" : "停用工艺路线");
        return toRouteView(routing, true);
    }

    @Transactional
    public Map<String, Object> uploadSop(Long operationId, MultipartFile file, String remark) {
        MdmOperation operation = requireOperation(operationId);
        String storedPath = fileStorageService.storeSop(file);
        MdmOperationSop sop = new MdmOperationSop();
        sop.setOperationId(operationId);
        sop.setFileName(file.getOriginalFilename());
        sop.setFileType(fileStorageService.detectFileType(file.getOriginalFilename()));
        sop.setFilePath(storedPath);
        sop.setFileSize(file.getSize());
        sop.setRemark(remark);
        sop.setCreatedTime(LocalDateTime.now());
        sop.setUpdatedTime(LocalDateTime.now());
        mdmOperationSopMapper.insert(sop);
        MdmRouting routing = requireRouting(operation.getRoutingId());
        recordHistory(routing.getId(), routing.getVersion(), "sop", "上传SOP：" + sop.getFileName());
        return sopView(sop);
    }

    @Transactional
    public void deleteSop(Long sopId) {
        MdmOperationSop sop = mdmOperationSopMapper.selectById(sopId);
        if (sop == null) {
            throw new BusinessException("SOP不存在");
        }
        fileStorageService.delete(sop.getFilePath());
        mdmOperationSopMapper.deleteById(sopId);
    }

    public MdmOperationSop requireSop(Long sopId) {
        MdmOperationSop sop = mdmOperationSopMapper.selectById(sopId);
        if (sop == null) {
            throw new BusinessException("SOP不存在");
        }
        return sop;
    }

    public void validateDeviceForOperation(Long operationId, Long deviceId) {
        if (operationId == null || deviceId == null) {
            return;
        }
        List<MdmOperationDevice> bindings = mdmOperationDeviceMapper.selectList(new LambdaQueryWrapper<MdmOperationDevice>()
                .eq(MdmOperationDevice::getOperationId, operationId));
        if (bindings.isEmpty()) {
            return;
        }
        DevDevice device = devDeviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        if ("scrapped".equals(device.getStatus())) {
            throw new BusinessException("设备已报废，不可用于生产");
        }
        boolean allowed = bindings.stream().anyMatch(bind -> {
            if ("device".equals(bind.getBindType()) && deviceId.equals(bind.getDeviceId())) {
                return true;
            }
            return "category".equals(bind.getBindType()) && bind.getCategoryId() != null
                    && bind.getCategoryId().equals(device.getCategoryId());
        });
        if (!allowed) {
            throw new BusinessException("当前设备不在工序允许的设备范围内");
        }
    }

    @Transactional
    public void applyRoutingToWorkOrder(ProdWorkOrder order, Long productId, String productName) {
        RoutingContext ctx = resolveRouting(productId, productName);
        if (ctx.routing() != null) {
            order.setRoutingId(ctx.routing().getId());
            order.setRouteVersion(ctx.routing().getVersion());
        }
        order.setEstimatedHours(BigDecimal.valueOf(ctx.totalHours()).setScale(2, RoundingMode.HALF_UP));
        List<MdmOperation> operations = ctx.operations();
        if (!operations.isEmpty()) {
            order.setProcessName(operations.get(0).getOperationName());
        }
    }

    @Transactional
    public void applyRoutingToWorkOrder(ProdWorkOrder order, String productName) {
        applyRoutingToWorkOrder(order, order.getProductId(), productName);
    }

    @Transactional
    public void initProcessRecords(Long workOrderId, Long productId, String productName) {
        RoutingContext ctx = resolveRouting(productId, productName);
        for (MdmOperation operation : ctx.operations()) {
            ProdProcessRecord record = new ProdProcessRecord();
            record.setWorkOrderId(workOrderId);
            record.setOperationId(operation.getId());
            record.setSeqNo(operation.getSeqNo());
            record.setProcessName(operation.getOperationName());
            record.setStatus("waiting");
            record.setCreatedTime(LocalDateTime.now());
            record.setUpdatedTime(LocalDateTime.now());
            prodProcessRecordMapper.insert(record);
        }
    }

    @Transactional
    public void initProcessRecords(Long workOrderId, String productName) {
        initProcessRecords(workOrderId, null, productName);
    }

    public RoutingContext resolveRouting(Long productId, String productName) {
        MdmRouting routing = null;
        if (productId != null) {
            routing = mdmRoutingMapper.selectOne(new LambdaQueryWrapper<MdmRouting>()
                    .eq(MdmRouting::getProductId, productId)
                    .eq(MdmRouting::getEnabled, 1)
                    .eq(MdmRouting::getStatus, "published")
                    .orderByAsc(MdmRouting::getId)
                    .last("limit 1"));
        }
        if (routing == null && StringUtils.hasText(productName)) {
            routing = mdmRoutingMapper.selectOne(new LambdaQueryWrapper<MdmRouting>()
                    .eq(MdmRouting::getProductName, productName.trim())
                    .eq(MdmRouting::getEnabled, 1)
                    .eq(MdmRouting::getStatus, "published")
                    .orderByAsc(MdmRouting::getId)
                    .last("limit 1"));
        }
        if (routing == null) {
            routing = getDefaultRoutingEntity();
        }
        if (routing == null) {
            return RoutingContext.fallback();
        }
        List<MdmOperation> operations = listOperations(routing.getId());
        if (operations.isEmpty()) {
            return RoutingContext.fallback();
        }
        return new RoutingContext(routing, operations, sumHours(operations));
    }

    public RoutingContext resolveRouting(String productName) {
        return resolveRouting(null, productName);
    }

    private void applyProductBinding(MdmRouting routing, Long productId, String productName) {
        if (productId != null) {
            var product = productService.getProductSummary(productId);
            if (product.isEmpty()) {
                throw new BusinessException("关联产品不存在");
            }
            routing.setProductId(productId);
            routing.setProductName((String) product.get("productName"));
            return;
        }
        if (StringUtils.hasText(productName)) {
            String name = productName.trim();
            routing.setProductName(name);
            var found = productService.findByName(name);
            routing.setProductId(found == null ? null : found.getId());
        } else {
            routing.setProductName(null);
            routing.setProductId(null);
        }
    }

    private RoutingContext loadRoutingContext(Long routingId) {
        MdmRouting routing = mdmRoutingMapper.selectById(routingId);
        if (routing == null) {
            return RoutingContext.fallback();
        }
        List<MdmOperation> operations = listOperations(routingId);
        if (operations.isEmpty()) {
            return RoutingContext.fallback();
        }
        return new RoutingContext(routing, operations, sumHours(operations));
    }

    private ProcessOperationItem mapOperationItem(Map<String, Object> row) {
        ProcessOperationItem item = new ProcessOperationItem();
        if (row.get("id") != null) {
            item.setId(((Number) row.get("id")).longValue());
        }
        item.setSeqNo((Integer) row.get("seqNo"));
        item.setOperationCode((String) row.get("operationCode"));
        item.setOperationName((String) row.get("operationName"));
        item.setStandardHours(row.get("standardHours") == null ? null : ((Number) row.get("standardHours")).doubleValue());
        item.setPrepHours(row.get("prepHours") == null ? null : ((Number) row.get("prepHours")).doubleValue());
        item.setChangeoverHours(row.get("changeoverHours") == null ? null : ((Number) row.get("changeoverHours")).doubleValue());
        item.setNeedReport(row.get("needReport") == null ? 1 : ((Number) row.get("needReport")).intValue());
        item.setNeedCheck(row.get("needCheck") == null ? 0 : ((Number) row.get("needCheck")).intValue());
        item.setNeedScan(row.get("needScan") == null ? 0 : ((Number) row.get("needScan")).intValue());
        item.setRemark((String) row.get("remark"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> params = (List<Map<String, Object>>) row.get("parameters");
        if (params != null) {
            item.setParameters(params.stream().map(p -> {
                ProcessParameterItem param = new ProcessParameterItem();
                param.setParamName((String) p.get("paramName"));
                param.setParamValue((String) p.get("paramValue"));
                param.setMinValue((String) p.get("minValue"));
                param.setMaxValue((String) p.get("maxValue"));
                param.setUnit((String) p.get("unit"));
                return param;
            }).toList());
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> devices = (List<Map<String, Object>>) row.get("devices");
        if (devices != null) {
            item.setDeviceIds(devices.stream()
                    .filter(d -> "device".equals(d.get("bindType")))
                    .map(d -> ((Number) d.get("deviceId")).longValue())
                    .toList());
            item.setCategoryIds(devices.stream()
                    .filter(d -> "category".equals(d.get("bindType")))
                    .map(d -> ((Number) d.get("categoryId")).longValue())
                    .toList());
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> materials = (List<Map<String, Object>>) row.get("materials");
        if (materials != null) {
            item.setMaterials(materials.stream().map(m -> {
                ProcessMaterialItem mat = new ProcessMaterialItem();
                mat.setMaterialId(((Number) m.get("materialId")).longValue());
                mat.setQty(m.get("qty") == null ? BigDecimal.ONE : new BigDecimal(m.get("qty").toString()));
                mat.setUnit((String) m.get("unit"));
                mat.setMaterialType((String) m.get("materialType"));
                mat.setRemark((String) m.get("remark"));
                return mat;
            }).toList());
        }
        return item;
    }

    private SaveModeAction applySaveMode(MdmRouting routing, String saveMode) {
        String mode = StringUtils.hasText(saveMode) ? saveMode.trim().toLowerCase() : "draft";
        return switch (mode) {
            case "publish" -> {
                if (canDirectPublish()) {
                    routing.setVersion(bumpVersion(routing.getVersion()));
                    routing.setStatus("published");
                    routing.setEnabled(1);
                    routing.setRejectedReason(null);
                    yield new SaveModeAction("publish", "保存并发布 " + routing.getVersion());
                } else {
                    routing.setStatus("pending_approval");
                    routing.setEnabled(0);
                    yield new SaveModeAction("submit", "提交工艺审批");
                }
            }
            case "submit" -> {
                routing.setStatus("pending_approval");
                routing.setEnabled(0);
                routing.setRejectedReason(null);
                yield new SaveModeAction("submit", "提交工艺审批");
            }
            default -> {
                String currentStatus = routing.getStatus();
                if ("published".equals(currentStatus) || "disabled".equals(currentStatus) || "pending_approval".equals(currentStatus)) {
                    yield new SaveModeAction("update", "更新已发布工艺");
                }
                routing.setStatus("draft");
                routing.setEnabled(0);
                yield new SaveModeAction("update", "保存工艺草稿");
            }
        };
    }

    private void recordSaveHistory(MdmRouting routing, SaveModeAction action, String changeSummary) {
        String desc = action.baseDesc();
        if (StringUtils.hasText(changeSummary)) {
            desc = desc + "：" + changeSummary;
        }
        recordHistory(routing.getId(), routing.getVersion(), action.actionType(), desc);
    }

    private String summarizeOperations(List<ProcessOperationItem> operations) {
        if (operations == null || operations.isEmpty()) {
            return "";
        }
        String names = operations.stream()
                .map(ProcessOperationItem::getOperationName)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("、"));
        return "，配置 " + operations.size() + " 道工序" + (StringUtils.hasText(names) ? "（" + names + "）" : "");
    }

    private RoutingSnapshot captureRoutingSnapshot(MdmRouting routing) {
        RoutingSnapshot snapshot = new RoutingSnapshot();
        snapshot.routeCode = routing.getRouteCode();
        snapshot.routeName = routing.getRouteName();
        snapshot.productName = routing.getProductName();
        snapshot.remark = routing.getRemark();
        snapshot.operations = listOperations(routing.getId()).stream()
                .map(this::captureOperationSnapshot)
                .toList();
        return snapshot;
    }

    private OperationSnapshot captureOperationSnapshot(MdmOperation operation) {
        OperationSnapshot snapshot = new OperationSnapshot();
        snapshot.id = operation.getId();
        snapshot.seqNo = operation.getSeqNo();
        snapshot.operationCode = operation.getOperationCode();
        snapshot.operationName = operation.getOperationName();
        snapshot.standardHours = operation.getStandardHours();
        snapshot.deviceLabels = listDeviceBindings(operation.getId()).stream()
                .map(this::deviceBindingLabel)
                .sorted()
                .toList();
        snapshot.materialCount = listMaterialBindings(operation.getId()).size();
        snapshot.paramCount = listParameters(operation.getId()).size();
        return snapshot;
    }

    private String deviceBindingLabel(Map<String, Object> bind) {
        if ("category".equals(bind.get("bindType"))) {
            Object name = bind.get("categoryName");
            return "分类:" + (name != null ? name : bind.get("categoryId"));
        }
        Object code = bind.get("deviceCode");
        Object name = bind.get("deviceName");
        if (code != null && name != null) {
            return code + " " + name;
        }
        return "设备:" + bind.get("deviceId");
    }

    private List<String> formatDeviceBindingLabels(ProcessOperationItem item) {
        List<String> labels = new ArrayList<>();
        if (item.getCategoryIds() != null) {
            for (Long categoryId : item.getCategoryIds()) {
                DevDeviceCategory category = devDeviceCategoryMapper.selectById(categoryId);
                labels.add("分类:" + (category != null ? category.getCategoryName() : categoryId));
            }
        }
        if (item.getDeviceIds() != null) {
            for (Long deviceId : item.getDeviceIds()) {
                DevDevice device = devDeviceMapper.selectById(deviceId);
                if (device != null) {
                    labels.add(device.getDeviceCode() + " " + device.getDeviceName());
                } else {
                    labels.add("设备:" + deviceId);
                }
            }
        }
        labels.sort(String::compareTo);
        return labels;
    }

    private String buildChangeSummary(RoutingSnapshot before, MdmRouting afterRouting, ProcessRouteSaveRequest request) {
        List<String> parts = new ArrayList<>();
        appendTextChange(parts, "工艺编号", before.routeCode, afterRouting.getRouteCode());
        appendTextChange(parts, "工艺名称", before.routeName, afterRouting.getRouteName());
        appendTextChange(parts, "适用产品", before.productName, afterRouting.getProductName());
        appendTextChange(parts, "备注", before.remark, afterRouting.getRemark());

        if (request.getOperations() != null) {
            Map<Long, OperationSnapshot> beforeById = before.operations.stream()
                    .filter(op -> op.id != null)
                    .collect(Collectors.toMap(op -> op.id, op -> op, (a, b) -> a));
            Set<Long> retainedIds = new HashSet<>();
            for (ProcessOperationItem item : request.getOperations()) {
                if (item.getId() == null) {
                    parts.add("新增工序「" + item.getOperationName() + "」");
                    continue;
                }
                retainedIds.add(item.getId());
                OperationSnapshot old = beforeById.get(item.getId());
                if (old != null) {
                    appendOperationChanges(parts, old, item);
                }
            }
            for (OperationSnapshot old : before.operations) {
                if (old.id != null && !retainedIds.contains(old.id)) {
                    parts.add("删除工序「" + old.operationName + "」");
                }
            }
        }

        return joinChangeSummary(parts);
    }

    private void appendTextChange(List<String> parts, String label, String before, String after) {
        String left = normalizeText(before);
        String right = normalizeText(after);
        if (!Objects.equals(left, right)) {
            parts.add(label + "：" + displayText(left) + " → " + displayText(right));
        }
    }

    private void appendOperationChanges(List<String> parts, OperationSnapshot before, ProcessOperationItem after) {
        String opLabel = "工序「" + before.operationName + "」";
        if (!Objects.equals(normalizeText(before.operationName), normalizeText(after.getOperationName()))) {
            parts.add(opLabel + "名称：" + before.operationName + " → " + after.getOperationName());
        }
        if (!Objects.equals(normalizeText(before.operationCode), normalizeText(after.getOperationCode()))) {
            parts.add(opLabel + "编号：" + displayText(before.operationCode) + " → " + displayText(after.getOperationCode()));
        }
        if (!Objects.equals(before.seqNo, after.getSeqNo())) {
            parts.add(opLabel + "序号：" + before.seqNo + " → " + after.getSeqNo());
        }
        if (!Objects.equals(before.standardHours, after.getStandardHours())) {
            parts.add(opLabel + "标准工时：" + formatHours(before.standardHours) + " → " + formatHours(after.getStandardHours()));
        }
        List<String> afterDeviceLabels = formatDeviceBindingLabels(after);
        if (!new HashSet<>(before.deviceLabels).equals(new HashSet<>(afterDeviceLabels))) {
            parts.add(opLabel + "设备绑定：" + joinLabels(before.deviceLabels) + " → " + joinLabels(afterDeviceLabels));
        }
        int afterMaterialCount = after.getMaterials() == null ? 0 : after.getMaterials().size();
        if (before.materialCount != afterMaterialCount) {
            parts.add(opLabel + "物料：" + before.materialCount + " 项 → " + afterMaterialCount + " 项");
        }
        int afterParamCount = after.getParameters() == null ? 0 : (int) after.getParameters().stream()
                .filter(param -> StringUtils.hasText(param.getParamName()))
                .count();
        if (before.paramCount != afterParamCount) {
            parts.add(opLabel + "参数：" + before.paramCount + " 项 → " + afterParamCount + " 项");
        }
    }

    private String joinChangeSummary(List<String> parts) {
        if (parts.isEmpty()) {
            return "未检测到字段变更";
        }
        if (parts.size() <= 6) {
            return String.join("；", parts);
        }
        return String.join("；", parts.subList(0, 6)) + " 等 " + parts.size() + " 项变更";
    }

    private String joinLabels(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return "无";
        }
        return String.join("、", labels);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String displayText(String value) {
        return StringUtils.hasText(value) ? value : "空";
    }

    private String formatHours(Double hours) {
        if (hours == null) {
            return "空";
        }
        return hours + "h";
    }

    private record SaveModeAction(String actionType, String baseDesc) {}

    private static final class RoutingSnapshot {
        private String routeCode;
        private String routeName;
        private String productName;
        private String remark;
        private List<OperationSnapshot> operations = List.of();
    }

    private static final class OperationSnapshot {
        private Long id;
        private Integer seqNo;
        private String operationCode;
        private String operationName;
        private Double standardHours;
        private List<String> deviceLabels = List.of();
        private int materialCount;
        private int paramCount;
    }

    private String resolveInitialStatus(String saveMode) {
        if (!StringUtils.hasText(saveMode)) {
            return "draft";
        }
        return switch (saveMode.trim().toLowerCase()) {
            case "publish" -> canDirectPublish() ? "published" : "pending_approval";
            case "submit" -> "pending_approval";
            default -> "draft";
        };
    }

    private boolean canDirectPublish() {
        try {
            var user = authService.currentUser();
            return roleService.hasFullAccess(user.getRole())
                    || roleService.getPermissionsByRoleKey(user.getRole()).contains("工艺审批");
        } catch (Exception ex) {
            return false;
        }
    }

    private void ensureApprovalPermission() {
        if (!canDirectPublish()) {
            throw new BusinessException("无工艺审批权限");
        }
    }

    private void setDefaultInternal(Long id) {
        requireRouting(id);
        mdmRoutingMapper.update(null, new LambdaUpdateWrapper<MdmRouting>().set(MdmRouting::getIsDefault, 0));
        MdmRouting routing = requireRouting(id);
        routing.setIsDefault(1);
        routing.setEnabled(1);
        routing.setStatus("published");
        routing.setUpdatedTime(LocalDateTime.now());
        mdmRoutingMapper.updateById(routing);
        recordHistory(id, routing.getVersion(), "default", "设为默认工艺路线");
    }

    private void validateOperations(List<ProcessOperationItem> items) {
        for (ProcessOperationItem item : items) {
            if (!StringUtils.hasText(item.getOperationName())) {
                throw new BusinessException("工序名称不能为空");
            }
        }
    }

    private boolean hasOperations(List<ProcessOperationItem> items) {
        return items != null && !items.isEmpty();
    }

    private boolean requiresOperations(String saveMode) {
        if (!StringUtils.hasText(saveMode)) {
            return false;
        }
        String mode = saveMode.trim().toLowerCase();
        return "publish".equals(mode) || "submit".equals(mode);
    }

    private void copyOperationSops(Long sourceOperationId, Long targetOperationId) {
        List<MdmOperationSop> sops = mdmOperationSopMapper.selectList(new LambdaQueryWrapper<MdmOperationSop>()
                .eq(MdmOperationSop::getOperationId, sourceOperationId)
                .orderByAsc(MdmOperationSop::getId));
        for (MdmOperationSop src : sops) {
            String newPath = fileStorageService.copySop(src.getFilePath());
            MdmOperationSop sop = new MdmOperationSop();
            sop.setOperationId(targetOperationId);
            sop.setFileName(src.getFileName());
            sop.setFileType(src.getFileType());
            sop.setFilePath(newPath);
            sop.setFileSize(src.getFileSize());
            sop.setRemark(src.getRemark());
            sop.setCreatedTime(LocalDateTime.now());
            sop.setUpdatedTime(LocalDateTime.now());
            mdmOperationSopMapper.insert(sop);
        }
    }

    private void saveOperations(Long routingId, List<ProcessOperationItem> items) {
        List<MdmOperation> existing = listOperations(routingId);
        Set<Long> incomingIds = items.stream()
                .map(ProcessOperationItem::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (MdmOperation operation : existing) {
            if (!incomingIds.contains(operation.getId())) {
                mdmOperationMapper.deleteById(operation.getId());
            }
        }
        for (ProcessOperationItem item : items) {
            MdmOperation operation;
            if (item.getId() != null) {
                operation = mdmOperationMapper.selectById(item.getId());
                if (operation == null || !routingId.equals(operation.getRoutingId())) {
                    throw new BusinessException("工序不存在");
                }
            } else {
                operation = new MdmOperation();
                operation.setRoutingId(routingId);
                operation.setCreatedTime(LocalDateTime.now());
            }
            operation.setOperationCode(StringUtils.hasText(item.getOperationCode())
                    ? item.getOperationCode().trim()
                    : "OP" + String.format("%02d", item.getSeqNo() * 10));
            operation.setSeqNo(item.getSeqNo());
            operation.setOperationName(item.getOperationName().trim());
            operation.setStandardHours(item.getStandardHours());
            operation.setPrepHours(item.getPrepHours());
            operation.setChangeoverHours(item.getChangeoverHours());
            operation.setNeedReport(item.getNeedReport() == null ? 1 : item.getNeedReport());
            operation.setNeedCheck(item.getNeedCheck() == null ? 0 : item.getNeedCheck());
            operation.setNeedScan(item.getNeedScan() == null ? 0 : item.getNeedScan());
            operation.setRemark(item.getRemark());
            operation.setUpdatedTime(LocalDateTime.now());
            if (item.getId() == null) {
                mdmOperationMapper.insert(operation);
            } else {
                mdmOperationMapper.updateById(operation);
            }
            saveParameters(operation.getId(), item.getParameters());
            saveDeviceBindings(operation.getId(), item);
            saveMaterialBindings(operation.getId(), item);
        }
    }

    private void saveParameters(Long operationId, List<ProcessParameterItem> items) {
        mdmOperationParamMapper.delete(new LambdaQueryWrapper<MdmOperationParam>()
                .eq(MdmOperationParam::getOperationId, operationId));
        if (items == null) {
            return;
        }
        for (ProcessParameterItem paramItem : items) {
            if (!StringUtils.hasText(paramItem.getParamName())) {
                continue;
            }
            MdmOperationParam param = new MdmOperationParam();
            param.setOperationId(operationId);
            param.setParamName(paramItem.getParamName().trim());
            param.setParamValue(paramItem.getParamValue());
            param.setMinValue(paramItem.getMinValue());
            param.setMaxValue(paramItem.getMaxValue());
            param.setUnit(paramItem.getUnit());
            param.setCreatedTime(LocalDateTime.now());
            param.setUpdatedTime(LocalDateTime.now());
            mdmOperationParamMapper.insert(param);
        }
    }

    private void saveDeviceBindings(Long operationId, ProcessOperationItem item) {
        mdmOperationDeviceMapper.delete(new LambdaQueryWrapper<MdmOperationDevice>()
                .eq(MdmOperationDevice::getOperationId, operationId));
        Set<Long> deviceIds = item.getDeviceIds() == null ? Set.of() : new HashSet<>(item.getDeviceIds());
        Set<Long> categoryIds = item.getCategoryIds() == null ? Set.of() : new HashSet<>(item.getCategoryIds());
        for (Long deviceId : deviceIds) {
            DevDevice device = devDeviceMapper.selectById(deviceId);
            if (device == null) {
                throw new BusinessException("设备不存在：" + deviceId);
            }
            MdmOperationDevice bind = new MdmOperationDevice();
            bind.setOperationId(operationId);
            bind.setDeviceId(deviceId);
            bind.setBindType("device");
            bind.setCreatedTime(LocalDateTime.now());
            mdmOperationDeviceMapper.insert(bind);
        }
        for (Long categoryId : categoryIds) {
            DevDeviceCategory category = devDeviceCategoryMapper.selectById(categoryId);
            if (category == null) {
                throw new BusinessException("设备分类不存在：" + categoryId);
            }
            MdmOperationDevice bind = new MdmOperationDevice();
            bind.setOperationId(operationId);
            bind.setCategoryId(categoryId);
            bind.setBindType("category");
            bind.setCreatedTime(LocalDateTime.now());
            mdmOperationDeviceMapper.insert(bind);
        }
    }

    private void saveMaterialBindings(Long operationId, ProcessOperationItem item) {
        mdmOperationMaterialMapper.delete(new LambdaQueryWrapper<MdmOperationMaterial>()
                .eq(MdmOperationMaterial::getOperationId, operationId));
        if (item.getMaterials() == null) {
            return;
        }
        for (ProcessMaterialItem matItem : item.getMaterials()) {
            if (matItem.getMaterialId() == null) {
                continue;
            }
            MatMaterial material = matMaterialMapper.selectById(matItem.getMaterialId());
            if (material == null) {
                throw new BusinessException("物料不存在：" + matItem.getMaterialId());
            }
            MdmOperationMaterial bind = new MdmOperationMaterial();
            bind.setOperationId(operationId);
            bind.setMaterialId(matItem.getMaterialId());
            bind.setQty(matItem.getQty() == null ? BigDecimal.ONE : matItem.getQty());
            bind.setUnit(StringUtils.hasText(matItem.getUnit()) ? matItem.getUnit() : material.getUnit());
            bind.setMaterialType(StringUtils.hasText(matItem.getMaterialType()) ? matItem.getMaterialType() : "raw");
            bind.setRemark(matItem.getRemark());
            bind.setCreatedTime(LocalDateTime.now());
            bind.setUpdatedTime(LocalDateTime.now());
            mdmOperationMaterialMapper.insert(bind);
        }
    }

    private MdmRouting getDefaultRoutingEntity() {
        return mdmRoutingMapper.selectOne(new LambdaQueryWrapper<MdmRouting>()
                .eq(MdmRouting::getIsDefault, 1)
                .eq(MdmRouting::getEnabled, 1)
                .eq(MdmRouting::getStatus, "published")
                .orderByAsc(MdmRouting::getId)
                .last("limit 1"));
    }

    private MdmRouting requireRouting(Long id) {
        MdmRouting routing = mdmRoutingMapper.selectById(id);
        if (routing == null) {
            throw new BusinessException("工艺路线不存在");
        }
        return routing;
    }

    private MdmOperation requireOperation(Long id) {
        MdmOperation operation = mdmOperationMapper.selectById(id);
        if (operation == null) {
            throw new BusinessException("工序不存在");
        }
        return operation;
    }

    private List<MdmOperation> listOperations(Long routingId) {
        return mdmOperationMapper.selectList(new LambdaQueryWrapper<MdmOperation>()
                .eq(MdmOperation::getRoutingId, routingId)
                .orderByAsc(MdmOperation::getSeqNo));
    }

    private double sumHours(List<MdmOperation> operations) {
        return operations.stream()
                .mapToDouble(op -> {
                    double std = op.getStandardHours() == null ? 0 : op.getStandardHours();
                    double prep = op.getPrepHours() == null ? 0 : op.getPrepHours();
                    double change = op.getChangeoverHours() == null ? 0 : op.getChangeoverHours();
                    return std + prep + change;
                })
                .sum();
    }

    private String nextRouteCode() {
        long count = mdmRoutingMapper.selectCount(null) + 1;
        return "ROUTE-" + String.format("%03d", count);
    }

    private void ensureUniqueCode(String code, Long excludeId) {
        MdmRouting existing = mdmRoutingMapper.selectOne(new LambdaQueryWrapper<MdmRouting>()
                .eq(MdmRouting::getRouteCode, code)
                .ne(excludeId != null, MdmRouting::getId, excludeId)
                .last("limit 1"));
        if (existing != null) {
            throw new BusinessException("工艺编号已存在");
        }
    }

    private String bumpVersion(String current) {
        if (!StringUtils.hasText(current) || !current.startsWith("V")) {
            return "V1.1";
        }
        try {
            String num = current.substring(1);
            String[] parts = num.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            minor += 1;
            if (minor >= 10) {
                major += 1;
                minor = 0;
            }
            return "V" + major + "." + minor;
        } catch (NumberFormatException ex) {
            return "V1.1";
        }
    }

    private void recordHistory(Long routingId, String version, String actionType, String actionDesc) {
        MdmRoutingHistory history = new MdmRoutingHistory();
        history.setRoutingId(routingId);
        history.setVersion(version);
        history.setActionType(actionType);
        history.setActionDesc(actionDesc);
        try {
            history.setOperatorId(authService.currentUser().getId());
            history.setOperatorName(authService.currentUser().getRealName());
        } catch (Exception ignored) {
            // background or anonymous
        }
        history.setCreateTime(LocalDateTime.now());
        mdmRoutingHistoryMapper.insert(history);
    }

    private Map<String, Object> fallbackRouteView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", 0);
        view.put("routeCode", "ROUTE-STD");
        view.put("routeName", "标准装配路线");
        view.put("version", "V1.0");
        view.put("status", "published");
        view.put("isDefault", true);
        view.put("enabled", true);
        view.put("operations", FALLBACK_OPERATIONS.stream().map(name -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("operationName", name);
            row.put("seqNo", FALLBACK_OPERATIONS.indexOf(name) + 1);
            row.put("operationCode", "OP" + String.format("%02d", (FALLBACK_OPERATIONS.indexOf(name) + 1) * 10));
            return row;
        }).toList());
        return view;
    }

    private Map<String, Object> toRouteView(MdmRouting routing, boolean includeDetails) {
        List<Map<String, Object>> operations = new ArrayList<>();
        for (MdmOperation operation : listOperations(routing.getId())) {
            operations.add(operationRow(operation, includeDetails));
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", routing.getId());
        view.put("routeCode", routing.getRouteCode());
        view.put("routeName", routing.getRouteName());
        view.put("productId", routing.getProductId());
        view.put("productName", routing.getProductName());
        view.put("version", routing.getVersion());
        view.put("status", routing.getStatus());
        view.put("rejectedReason", routing.getRejectedReason());
        view.put("isDefault", routing.getIsDefault() != null && routing.getIsDefault() == 1);
        view.put("enabled", routing.getEnabled() == null || routing.getEnabled() == 1);
        view.put("remark", routing.getRemark());
        view.put("totalStandardHours", sumHours(listOperations(routing.getId())));
        view.put("operations", operations);
        if (includeDetails) {
            view.put("history", listHistory(routing.getId()));
        }
        return view;
    }

    private Map<String, Object> operationRow(MdmOperation operation, boolean includeDetails) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", operation.getId());
        row.put("operationCode", operation.getOperationCode());
        row.put("seqNo", operation.getSeqNo());
        row.put("operationName", operation.getOperationName());
        row.put("standardHours", operation.getStandardHours());
        row.put("prepHours", operation.getPrepHours());
        row.put("changeoverHours", operation.getChangeoverHours());
        row.put("needReport", operation.getNeedReport());
        row.put("needCheck", operation.getNeedCheck());
        row.put("needScan", operation.getNeedScan());
        row.put("remark", operation.getRemark());
        if (includeDetails) {
            row.put("parameters", listParameters(operation.getId()));
            row.put("devices", listDeviceBindings(operation.getId()));
            row.put("materials", listMaterialBindings(operation.getId()));
            row.put("sops", listSops(operation.getId()));
        }
        return row;
    }

    private List<Map<String, Object>> listParameters(Long operationId) {
        return mdmOperationParamMapper.selectList(new LambdaQueryWrapper<MdmOperationParam>()
                        .eq(MdmOperationParam::getOperationId, operationId)
                        .orderByAsc(MdmOperationParam::getId))
                .stream()
                .map(param -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", param.getId());
                    row.put("paramName", param.getParamName());
                    row.put("paramValue", param.getParamValue());
                    row.put("minValue", param.getMinValue());
                    row.put("maxValue", param.getMaxValue());
                    row.put("unit", param.getUnit());
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> listDeviceBindings(Long operationId) {
        return mdmOperationDeviceMapper.selectList(new LambdaQueryWrapper<MdmOperationDevice>()
                        .eq(MdmOperationDevice::getOperationId, operationId)
                        .orderByAsc(MdmOperationDevice::getId))
                .stream()
                .map(bind -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", bind.getId());
                    row.put("bindType", bind.getBindType());
                    row.put("deviceId", bind.getDeviceId());
                    row.put("categoryId", bind.getCategoryId());
                    if (bind.getDeviceId() != null) {
                        DevDevice device = devDeviceMapper.selectById(bind.getDeviceId());
                        if (device != null) {
                            row.put("deviceName", device.getDeviceName());
                            row.put("deviceCode", device.getDeviceCode());
                            row.put("deviceStatus", device.getStatus());
                        }
                    }
                    if (bind.getCategoryId() != null) {
                        DevDeviceCategory category = devDeviceCategoryMapper.selectById(bind.getCategoryId());
                        if (category != null) {
                            row.put("categoryName", category.getCategoryName());
                        }
                    }
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> listMaterialBindings(Long operationId) {
        return mdmOperationMaterialMapper.selectList(new LambdaQueryWrapper<MdmOperationMaterial>()
                        .eq(MdmOperationMaterial::getOperationId, operationId)
                        .orderByAsc(MdmOperationMaterial::getId))
                .stream()
                .map(bind -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", bind.getId());
                    row.put("materialId", bind.getMaterialId());
                    row.put("qty", bind.getQty());
                    row.put("unit", bind.getUnit());
                    row.put("materialType", bind.getMaterialType());
                    row.put("remark", bind.getRemark());
                    MatMaterial material = matMaterialMapper.selectById(bind.getMaterialId());
                    if (material != null) {
                        row.put("materialCode", material.getMaterialCode());
                        row.put("materialName", material.getMaterialName());
                        row.put("stockQty", material.getStockQty());
                    }
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> listSops(Long operationId) {
        return mdmOperationSopMapper.selectList(new LambdaQueryWrapper<MdmOperationSop>()
                        .eq(MdmOperationSop::getOperationId, operationId)
                        .orderByDesc(MdmOperationSop::getCreatedTime))
                .stream()
                .map(this::sopView)
                .toList();
    }

    private Map<String, Object> sopView(MdmOperationSop sop) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", sop.getId());
        row.put("fileName", sop.getFileName());
        row.put("fileType", sop.getFileType());
        row.put("fileSize", sop.getFileSize());
        row.put("remark", sop.getRemark());
        row.put("previewUrl", "/api/process-routes/sop/" + sop.getId() + "/file");
        row.put("createTime", sop.getCreatedTime());
        return row;
    }

    private List<Map<String, Object>> listHistory(Long routingId) {
        return mdmRoutingHistoryMapper.selectList(new LambdaQueryWrapper<MdmRoutingHistory>()
                        .eq(MdmRoutingHistory::getRoutingId, routingId)
                        .orderByDesc(MdmRoutingHistory::getCreateTime)
                        .last("limit 30"))
                .stream()
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", item.getId());
                    row.put("version", item.getVersion());
                    row.put("actionType", item.getActionType());
                    row.put("actionDesc", item.getActionDesc());
                    row.put("operatorName", item.getOperatorName());
                    row.put("createTime", item.getCreateTime());
                    return row;
                })
                .toList();
    }

    public record RoutingContext(MdmRouting routing, List<MdmOperation> operations, double totalHours) {
        static RoutingContext fallback() {
            List<MdmOperation> ops = new ArrayList<>();
            for (int i = 0; i < FALLBACK_OPERATIONS.size(); i++) {
                MdmOperation op = new MdmOperation();
                op.setSeqNo(i + 1);
                op.setOperationName(FALLBACK_OPERATIONS.get(i));
                op.setOperationCode("OP" + String.format("%02d", (i + 1) * 10));
                op.setStandardHours(1.0);
                ops.add(op);
            }
            return new RoutingContext(null, ops, ops.size() * 1.0);
        }
    }
}
