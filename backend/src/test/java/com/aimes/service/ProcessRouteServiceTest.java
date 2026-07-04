package com.aimes.service;

import com.aimes.common.OperationLogRunner;
import com.aimes.entity.MdmRouting;
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
import com.aimes.entity.SysUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessRouteServiceTest {

    @Mock
    private MdmRoutingMapper mdmRoutingMapper;
    @Mock
    private MdmOperationMapper mdmOperationMapper;
    @Mock
    private MdmOperationParamMapper mdmOperationParamMapper;
    @Mock
    private MdmOperationSopMapper mdmOperationSopMapper;
    @Mock
    private MdmOperationDeviceMapper mdmOperationDeviceMapper;
    @Mock
    private MdmOperationMaterialMapper mdmOperationMaterialMapper;
    @Mock
    private MdmRoutingHistoryMapper mdmRoutingHistoryMapper;
    @Mock
    private ProdWorkOrderMapper prodWorkOrderMapper;
    @Mock
    private ProdProcessRecordMapper prodProcessRecordMapper;
    @Mock
    private DevDeviceMapper devDeviceMapper;
    @Mock
    private DevDeviceCategoryMapper devDeviceCategoryMapper;
    @Mock
    private MatMaterialMapper matMaterialMapper;
    @Mock
    private AuthService authService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private RoleService roleService;
    @Mock
    private OperationLogRunner operationLogRunner;

    @InjectMocks
    private ProcessRouteService processRouteService;

    @org.junit.jupiter.api.BeforeEach
    void setUpRunner() throws Exception {
        when(operationLogRunner.runUnchecked(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(4);
            return callable.call();
        });
    }

    @Test
    void submitForApproval_shouldMoveDraftToPendingApproval() {
        MdmRouting routing = sampleRouting("draft");
        when(mdmRoutingMapper.selectById(1L)).thenReturn(routing);
        stubRouteViewQueries();

        Map<String, Object> result = processRouteService.submitForApproval(1L);

        assertEquals("pending_approval", routing.getStatus());
        assertEquals("pending_approval", result.get("status"));
        verify(mdmRoutingMapper).updateById(routing);
    }

    @Test
    void approveRoute_shouldPublishPendingRoute() {
        MdmRouting routing = sampleRouting("pending_approval");
        when(mdmRoutingMapper.selectById(1L)).thenReturn(routing);
        stubApprovalPermission();
        stubRouteViewQueries();

        Map<String, Object> result = processRouteService.approveRoute(1L);

        assertEquals("published", routing.getStatus());
        assertEquals(1, routing.getEnabled());
        assertEquals("V1.1", routing.getVersion());
        assertEquals("published", result.get("status"));
        verify(mdmRoutingMapper).updateById(routing);
    }

    @Test
    void rejectRoute_shouldMovePendingRouteToRejected() {
        MdmRouting routing = sampleRouting("pending_approval");
        when(mdmRoutingMapper.selectById(1L)).thenReturn(routing);
        stubApprovalPermission();
        stubRouteViewQueries();

        Map<String, Object> result = processRouteService.rejectRoute(1L, "参数不完整");

        assertEquals("rejected", routing.getStatus());
        assertEquals(0, routing.getEnabled());
        assertEquals("参数不完整", routing.getRejectedReason());
        assertEquals("rejected", result.get("status"));
        verify(mdmRoutingMapper).updateById(routing);
    }

    private MdmRouting sampleRouting(String status) {
        MdmRouting routing = new MdmRouting();
        routing.setId(1L);
        routing.setRouteCode("ROUTE-001");
        routing.setRouteName("标准工艺");
        routing.setVersion("V1.0");
        routing.setStatus(status);
        routing.setIsDefault(0);
        routing.setEnabled(0);
        return routing;
    }

    private void stubApprovalPermission() {
        SysUser user = new SysUser();
        user.setRole("admin");
        when(authService.currentUser()).thenReturn(user);
        when(roleService.hasFullAccess("admin")).thenReturn(true);
    }

    private void stubRouteViewQueries() {
        when(mdmOperationMapper.selectList(any())).thenReturn(List.of());
        when(mdmOperationParamMapper.selectList(any())).thenReturn(List.of());
        when(mdmOperationSopMapper.selectList(any())).thenReturn(List.of());
        when(mdmOperationDeviceMapper.selectList(any())).thenReturn(List.of());
        when(mdmOperationMaterialMapper.selectList(any())).thenReturn(List.of());
    }
}
