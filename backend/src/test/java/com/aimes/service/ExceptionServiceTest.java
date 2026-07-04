package com.aimes.service;

import com.aimes.dto.Requests.ExceptionCreateRequest;
import com.aimes.dto.Requests.ExceptionHandleRequest;
import com.aimes.entity.ExcEvent;
import com.aimes.entity.ProdProcessRecord;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.entity.SysUser;
import com.aimes.mapper.DevDeviceMapper;
import com.aimes.mapper.ExcEventMapper;
import com.aimes.mapper.ProdProcessRecordMapper;
import com.aimes.mapper.ProdWorkOrderMapper;
import com.aimes.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExceptionServiceTest {

    @Mock
    private ExcEventMapper excEventMapper;
    @Mock
    private ProdWorkOrderMapper prodWorkOrderMapper;
    @Mock
    private ProdProcessRecordMapper prodProcessRecordMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private AuthService authService;
    @Mock
    private SysNotificationService sysNotificationService;
    @Mock
    private DevDeviceMapper devDeviceMapper;
    @Mock
    private DeviceService deviceService;

    @InjectMocks
    private ExceptionService exceptionService;

    @Test
    void create_shouldPauseRunningProcessAndMarkOrderException() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setRealName("测试员");

        ProdWorkOrder order = new ProdWorkOrder();
        order.setId(10L);
        order.setOrderNo("WO-010");
        order.setStatus("producing");

        ProdProcessRecord running = new ProdProcessRecord();
        running.setId(20L);
        running.setWorkOrderId(10L);
        running.setStatus("running");

        ExceptionCreateRequest request = new ExceptionCreateRequest();
        request.setEventType("production");
        request.setWorkOrderId(10L);
        request.setDescription("设备抖动");
        request.setOccurTime(LocalDateTime.now());

        when(authService.currentUser()).thenReturn(user);
        when(prodWorkOrderMapper.selectById(10L)).thenReturn(order);
        when(prodProcessRecordMapper.selectOne(any())).thenReturn(running);
        when(sysUserMapper.selectList(any())).thenReturn(List.of());

        exceptionService.create(request);

        ArgumentCaptor<ProdWorkOrder> orderCaptor = ArgumentCaptor.forClass(ProdWorkOrder.class);
        verify(prodWorkOrderMapper).updateById(orderCaptor.capture());
        assertEquals("exception", orderCaptor.getValue().getStatus());

        ArgumentCaptor<ProdProcessRecord> recordCaptor = ArgumentCaptor.forClass(ProdProcessRecord.class);
        verify(prodProcessRecordMapper).updateById(recordCaptor.capture());
        assertEquals("paused", recordCaptor.getValue().getStatus());
    }

    @Test
    void handle_withRecovery_shouldResumePausedProcess() {
        SysUser user = new SysUser();
        user.setId(2L);
        user.setRealName("主管");

        ExcEvent event = new ExcEvent();
        event.setId(30L);
        event.setWorkOrderId(10L);
        event.setEventType("production");
        event.setStatus("open");

        ProdWorkOrder order = new ProdWorkOrder();
        order.setId(10L);
        order.setOrderNo("WO-010");
        order.setStatus("exception");

        ProdProcessRecord paused = new ProdProcessRecord();
        paused.setId(20L);
        paused.setWorkOrderId(10L);
        paused.setStatus("paused");
        paused.setSeqNo(2);

        ExceptionHandleRequest request = new ExceptionHandleRequest();
        request.setHandleAction("重启设备");
        request.setHandleResult("已恢复生产");

        when(excEventMapper.selectById(30L)).thenReturn(event);
        when(authService.currentUser()).thenReturn(user);
        when(prodWorkOrderMapper.selectById(10L)).thenReturn(order);
        when(prodProcessRecordMapper.selectOne(any())).thenReturn(paused);

        Map<String, Object> result = exceptionService.handle(30L, request);

        assertEquals("closed", event.getStatus());
        assertEquals("producing", order.getStatus());
        assertEquals("running", paused.getStatus());
        assertEquals("closed", result.get("status"));
        verify(prodProcessRecordMapper).updateById(paused);
    }
}
