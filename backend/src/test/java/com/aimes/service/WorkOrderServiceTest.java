package com.aimes.service;

import com.aimes.common.OperationLogRunner;
import com.aimes.config.AimesProperties;
import com.aimes.dto.Requests.WorkOrderAssignRequest;
import com.aimes.entity.ProdProcessRecord;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.entity.SysUser;
import com.aimes.mapper.ExcEventMapper;
import com.aimes.mapper.ProdPlanMapper;
import com.aimes.mapper.ProdProcessRecordMapper;
import com.aimes.mapper.ProdTeamMapper;
import com.aimes.mapper.ProdWorkOrderMapper;
import com.aimes.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Callable;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock
    private ProdWorkOrderMapper prodWorkOrderMapper;
    @Mock
    private ProdPlanMapper prodPlanMapper;
    @Mock
    private ProdTeamMapper prodTeamMapper;
    @Mock
    private ProdProcessRecordMapper prodProcessRecordMapper;
    @Mock
    private ExcEventMapper excEventMapper;
    @Mock
    private AuthService authService;
    @Mock
    private WorkOrderNoService workOrderNoService;
    @Mock
    private SysNotificationService sysNotificationService;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private ProcessRouteService processRouteService;
    @Mock
    private OperationLogRunner operationLogRunner;
    @Mock
    private ProductService productService;
    @Mock
    private MaterialService materialService;
    @Mock
    private AimesProperties aimesProperties;

    @InjectMocks
    private WorkOrderService workOrderService;

    @org.junit.jupiter.api.BeforeEach
    void setUpRunner() throws Exception {
        when(operationLogRunner.runUnchecked(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(4);
            return callable.call();
        });
    }

    @Test
    void assign_shouldMovePendingOrderToAssigned() {
        ProdWorkOrder order = new ProdWorkOrder();
        order.setId(1L);
        order.setOrderNo("WO-001");
        order.setStatus("pending");
        when(prodWorkOrderMapper.selectById(1L)).thenReturn(order);
        when(sysUserMapper.selectList(any())).thenReturn(List.of());
        stubDetailQueries(order);

        WorkOrderAssignRequest request = new WorkOrderAssignRequest();
        request.setTeamId(2L);
        request.setPriority(2);

        Map<String, Object> result = workOrderService.assign(1L, request);

        assertEquals("assigned", order.getStatus());
        assertEquals(2L, order.getTeamId());
        assertEquals("assigned", result.get("status"));
        verify(prodWorkOrderMapper).updateById(order);
    }

    @Test
    void claim_shouldMoveAssignedOrderToProducingAndStartFirstProcess() {
        ProdWorkOrder order = new ProdWorkOrder();
        order.setId(1L);
        order.setOrderNo("WO-002");
        order.setStatus("assigned");
        order.setTeamId(2L);

        ProdProcessRecord first = new ProdProcessRecord();
        first.setId(10L);
        first.setWorkOrderId(1L);
        first.setSeqNo(1);
        first.setProcessName("备料");
        first.setStatus("pending");

        SysUser user = new SysUser();
        user.setId(5L);
        user.setRole("admin");

        when(authService.currentUser()).thenReturn(user);
        when(prodWorkOrderMapper.selectById(1L)).thenReturn(order);
        when(prodProcessRecordMapper.selectOne(any())).thenReturn(first);
        stubDetailQueries(order);

        Map<String, Object> result = workOrderService.claim(1L);

        assertEquals("producing", order.getStatus());
        assertEquals(5L, order.getClaimUserId());
        assertEquals("running", first.getStatus());
        assertEquals("producing", result.get("status"));
        verify(prodWorkOrderMapper).updateById(order);
        verify(prodProcessRecordMapper).updateById(first);
    }

    private void stubDetailQueries(ProdWorkOrder order) {
        when(prodPlanMapper.selectById(any())).thenReturn(null);
        when(prodTeamMapper.selectById(any())).thenReturn(null);
        when(excEventMapper.selectCount(any())).thenReturn(0L);
        when(prodProcessRecordMapper.selectList(any())).thenReturn(List.of());
    }
}
