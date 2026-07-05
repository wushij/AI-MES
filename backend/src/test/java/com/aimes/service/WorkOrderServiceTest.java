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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    void claim_shouldMoveAssignedOrderToProducingWithoutStartingProcess() {
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
        first.setStatus("waiting");

        SysUser user = new SysUser();
        user.setId(5L);
        user.setRole("admin");

        when(authService.currentUser()).thenReturn(user);
        when(prodWorkOrderMapper.selectById(1L)).thenReturn(order);
        when(prodProcessRecordMapper.selectList(any())).thenReturn(List.of(first));
        stubDetailQueries(order);

        Map<String, Object> result = workOrderService.claim(1L);

        assertEquals("producing", order.getStatus());
        assertEquals(5L, order.getClaimUserId());
        assertEquals("waiting", first.getStatus());
        assertEquals("producing", result.get("status"));
        verify(prodWorkOrderMapper).updateById(order);
        verify(prodProcessRecordMapper, never()).updateById(any(ProdProcessRecord.class));
    }

    @Test
    void complete_whenAlreadyDone_shouldSkipPickAndUpdates() {
        ProdWorkOrder order = new ProdWorkOrder();
        order.setId(1L);
        order.setOrderNo("WO-DONE");
        order.setStatus("done");
        order.setProgress(100);
        when(prodWorkOrderMapper.selectById(1L)).thenReturn(order);
        stubDetailQueries(order);

        Map<String, Object> result = workOrderService.complete(1L);

        assertEquals("done", result.get("status"));
        verify(materialService, never()).pickForWorkOrder(anyLong(), anyLong(), anyInt(), any());
        verify(productService, never()).receiveFromWorkOrder(anyLong(), anyLong(), anyInt(), any());
        verify(prodWorkOrderMapper, never()).updateById(order);
    }

    @Test
    void complete_whenProducing_shouldPickMaterialsOnce() {
        ProdWorkOrder order = new ProdWorkOrder();
        order.setId(2L);
        order.setOrderNo("WO-PROD");
        order.setStatus("producing");
        order.setProgress(80);
        order.setProductId(10L);
        order.setOrderQty(1);
        when(prodWorkOrderMapper.selectById(2L)).thenReturn(order);
        when(aimesProperties.isBomPickOnComplete()).thenReturn(true);
        when(materialService.hasPickActivity(eq(2L), anyCollection())).thenReturn(false);
        when(processRouteService.resolveRouting(10L, null)).thenReturn(ProcessRouteService.RoutingContext.fallback());
        when(productService.hasActiveBom(10L)).thenReturn(true);
        when(productService.computeBomDemand(10L, 1)).thenReturn(List.of(Map.of("materialId", 1L, "requiredQty", 1)));
        stubDetailQueries(order);

        workOrderService.complete(2L);

        assertEquals("done", order.getStatus());
        verify(materialService).pickForWorkOrder(2L, 10L, 1, List.of(Map.of("materialId", 1L, "requiredQty", 1)), "bom");
        verify(productService).receiveFromWorkOrder(2L, 10L, 1, "WO-PROD");
        verify(prodWorkOrderMapper).updateById(order);
    }

    private void stubDetailQueries(ProdWorkOrder order) {
        when(prodPlanMapper.selectById(any())).thenReturn(null);
        when(prodTeamMapper.selectById(any())).thenReturn(null);
        when(excEventMapper.selectCount(any())).thenReturn(0L);
        when(prodProcessRecordMapper.selectList(any())).thenReturn(List.of());
    }
}

