package com.aimes.service;

import com.aimes.common.BusinessException;
import com.aimes.dto.Requests.ExceptionCreateRequest;
import com.aimes.dto.Requests.ExceptionHandleRequest;
import com.aimes.entity.DevDevice;
import com.aimes.entity.ExcEvent;
import com.aimes.entity.ProdProcessRecord;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.entity.SysUser;
import com.aimes.mapper.DevDeviceMapper;
import com.aimes.mapper.ExcEventMapper;
import com.aimes.mapper.ProdProcessRecordMapper;
import com.aimes.mapper.ProdWorkOrderMapper;
import com.aimes.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExceptionService {

    private final ExcEventMapper excEventMapper;
    private final ProdWorkOrderMapper prodWorkOrderMapper;
    private final ProdProcessRecordMapper prodProcessRecordMapper;
    private final SysUserMapper sysUserMapper;
    private final AuthService authService;
    private final SysNotificationService sysNotificationService;
    private final DevDeviceMapper devDeviceMapper;
    private final DeviceService deviceService;

    @Transactional
    public void deleteByWorkOrderId(Long workOrderId) {
        if (workOrderId == null) {
            return;
        }
        excEventMapper.delete(new LambdaQueryWrapper<ExcEvent>()
                .eq(ExcEvent::getWorkOrderId, workOrderId));
    }

    @Transactional
    public void cleanupOrphanEvents() {
        List<ExcEvent> events = excEventMapper.selectList(new LambdaQueryWrapper<ExcEvent>()
                .select(ExcEvent::getId, ExcEvent::getWorkOrderId));
        if (events.isEmpty()) {
            return;
        }
        List<Long> workOrderIds = events.stream()
                .map(ExcEvent::getWorkOrderId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (workOrderIds.isEmpty()) {
            return;
        }
        List<Long> existingOrderIds = prodWorkOrderMapper.selectBatchIds(workOrderIds).stream()
                .map(ProdWorkOrder::getId)
                .toList();
        List<Long> orphanIds = events.stream()
                .filter(event -> event.getWorkOrderId() == null || !existingOrderIds.contains(event.getWorkOrderId()))
                .map(ExcEvent::getId)
                .toList();
        if (!orphanIds.isEmpty()) {
            excEventMapper.delete(new LambdaQueryWrapper<ExcEvent>().in(ExcEvent::getId, orphanIds));
        }
    }

    public Map<String, Object> list(long current, long size, String keyword, String type, String status) {
        cleanupOrphanEvents();
        Page<ExcEvent> page = excEventMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<ExcEvent>()
                .like(StringUtils.hasText(keyword), ExcEvent::getEventNo, keyword)
                .eq(StringUtils.hasText(type), ExcEvent::getEventType, type)
                .eq(StringUtils.hasText(status), ExcEvent::getStatus, status)
                .last("ORDER BY FIELD(status, 'open', 'processing', 'closed'), occur_time DESC"));
        return Map.of("total", page.getTotal(), "records", page.getRecords().stream().map(this::toView).toList());
    }

    public Map<String, Object> detail(Long id) {
        return toView(getEvent(id));
    }

    @Transactional
    public Map<String, Object> create(ExceptionCreateRequest request) {
        SysUser user = authService.currentUser();
        ProdWorkOrder order = requireOrder(request.getWorkOrderId());
        deviceService.validateDeviceForException(request.getDeviceId(), request.getEventType());

        ExcEvent event = new ExcEvent();
        event.setEventNo(nextEventNo());
        event.setEventType(request.getEventType());
        event.setWorkOrderId(request.getWorkOrderId());
        event.setDeviceId(request.getDeviceId());
        event.setDescription(request.getDescription());
        event.setStatus("open");
        event.setReporterId(user.getId());
        event.setOccurTime(request.getOccurTime());
        event.setCreateTime(LocalDateTime.now());
        excEventMapper.insert(event);

        if (request.getDeviceId() != null && "device".equals(request.getEventType())) {
            deviceService.onExceptionReported(request.getDeviceId(), event.getId(), user.getId(), user.getRealName());
        }

        order.setStatus("exception");
        prodWorkOrderMapper.updateById(order);

        ProdProcessRecord running = prodProcessRecordMapper.selectOne(new LambdaQueryWrapper<ProdProcessRecord>()
                .eq(ProdProcessRecord::getWorkOrderId, order.getId())
                .eq(ProdProcessRecord::getStatus, "running")
                .last("limit 1"));
        if (running != null) {
            running.setStatus("paused");
            prodProcessRecordMapper.updateById(running);
        }

        // Add system notifications for supervisors and admins
        List<SysUser> usersToNotify = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getRole, java.util.List.of("admin", "supervisor")));
        for (SysUser recipient : usersToNotify) {
            sysNotificationService.createNotification(
                    recipient.getId(),
                    "异常报警",
                    "工单 " + order.getOrderNo() + " 存在未处理的 " + translateType(request.getEventType()) + " 异常！",
                    "danger",
                    "/exceptions"
            );
        }

        return toView(event);
    }

    private String translateType(String type) {
        if ("shortage".equals(type) || "material".equals(type)) return "缺料";
        if ("quality".equals(type)) return "质量";
        if ("device".equals(type)) return "设备";
        return "生产";
    }

    @Transactional
    public Map<String, Object> handle(Long id, ExceptionHandleRequest request) {
        ExcEvent event = getEvent(id);
        SysUser user = authService.currentUser();
        ProdWorkOrder order = requireOrder(event.getWorkOrderId());

        event.setHandlerId(user.getId());
        event.setHandleAction(request.getHandleAction());
        event.setHandleResult(request.getHandleResult());
        event.setHandleTime(LocalDateTime.now());

        boolean recovered = request.getHandleResult().contains("恢复");
        event.setStatus(recovered ? "closed" : "processing");
        excEventMapper.updateById(event);

        order.setStatus(recovered ? "producing" : "exception");
        prodWorkOrderMapper.updateById(order);

        if (recovered) {
            ProdProcessRecord paused = prodProcessRecordMapper.selectOne(new LambdaQueryWrapper<ProdProcessRecord>()
                    .eq(ProdProcessRecord::getWorkOrderId, order.getId())
                    .eq(ProdProcessRecord::getStatus, "paused")
                    .orderByDesc(ProdProcessRecord::getSeqNo)
                    .last("limit 1"));
            if (paused != null) {
                paused.setStatus("running");
                prodProcessRecordMapper.updateById(paused);
            }
        }

        if (event.getDeviceId() != null && "device".equals(event.getEventType())) {
            deviceService.onExceptionHandled(event.getDeviceId(), event.getId(), recovered, user.getId(), user.getRealName());
        }
        return toView(event);
    }

    @Transactional
    public void delete(Long id) {
        ExcEvent event = getEvent(id);
        ProdWorkOrder order = event.getWorkOrderId() == null ? null : prodWorkOrderMapper.selectById(event.getWorkOrderId());
        Long workOrderId = event.getWorkOrderId();
        String eventStatus = event.getStatus();

        excEventMapper.deleteById(id);
        if (order != null) {
            sysNotificationService.deleteByWorkOrderNo(order.getOrderNo());
        }

        if (workOrderId == null || !isActiveStatus(eventStatus)) {
            return;
        }

        long remainingActive = excEventMapper.selectCount(new LambdaQueryWrapper<ExcEvent>()
                .eq(ExcEvent::getWorkOrderId, workOrderId)
                .in(ExcEvent::getStatus, List.of("open", "processing")));
        if (remainingActive > 0) {
            return;
        }

        if (order == null || !"exception".equals(order.getStatus())) {
            return;
        }

        ProdProcessRecord paused = prodProcessRecordMapper.selectOne(new LambdaQueryWrapper<ProdProcessRecord>()
                .eq(ProdProcessRecord::getWorkOrderId, workOrderId)
                .eq(ProdProcessRecord::getStatus, "paused")
                .orderByDesc(ProdProcessRecord::getSeqNo)
                .last("limit 1"));
        if (paused != null) {
            paused.setStatus("running");
            prodProcessRecordMapper.updateById(paused);
            order.setStatus("producing");
        } else if (order.getClaimUserId() != null) {
            order.setStatus("producing");
        } else if (order.getTeamId() != null) {
            order.setStatus("assigned");
        } else {
            order.setStatus("pending");
        }
        prodWorkOrderMapper.updateById(order);
    }

    private boolean isActiveStatus(String status) {
        return "open".equals(status) || "processing".equals(status);
    }

    private ExcEvent getEvent(Long id) {
        ExcEvent event = excEventMapper.selectById(id);
        if (event == null) {
            throw new BusinessException("异常事件不存在");
        }
        return event;
    }

    private ProdWorkOrder requireOrder(Long id) {
        ProdWorkOrder order = prodWorkOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("关联工单不存在");
        }
        return order;
    }

    private Map<String, Object> toView(ExcEvent event) {
        SysUser reporter = event.getReporterId() == null ? null : sysUserMapper.selectById(event.getReporterId());
        SysUser handler = event.getHandlerId() == null ? null : sysUserMapper.selectById(event.getHandlerId());
        ProdWorkOrder workOrder = event.getWorkOrderId() == null ? null : prodWorkOrderMapper.selectById(event.getWorkOrderId());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", event.getId());
        row.put("eventNo", event.getEventNo());
        row.put("eventType", event.getEventType());
        row.put("workOrderId", event.getWorkOrderId());
        row.put("workOrderNo", workOrder == null ? null : workOrder.getOrderNo());
        row.put("deviceId", event.getDeviceId());
        if (event.getDeviceId() != null) {
            DevDevice device = devDeviceMapper.selectById(event.getDeviceId());
            if (device != null) {
                row.put("deviceCode", device.getDeviceCode());
                row.put("deviceName", device.getDeviceName());
            }
        }
        row.put("description", event.getDescription());
        row.put("status", event.getStatus());
        row.put("reporterId", event.getReporterId());
        row.put("reporterName", reporter == null ? null : reporter.getRealName());
        row.put("handlerId", event.getHandlerId());
        row.put("handlerName", handler == null ? null : handler.getRealName());
        row.put("occurTime", event.getOccurTime());
        row.put("createTime", event.getCreateTime());
        row.put("handleTime", event.getHandleTime());
        row.put("handleAction", event.getHandleAction());
        row.put("handleResult", event.getHandleResult());
        return row;
    }

    private String nextEventNo() {
        long count = excEventMapper.selectCount(null) + 1;
        return "EXC-" + LocalDateTime.now().getYear() + "-" + String.format("%03d", count);
    }
}
