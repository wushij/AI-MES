package com.aimes.service.coze;

import com.aimes.entity.AiChatLog;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.mapper.ExcEventMapper;
import com.aimes.mapper.MatMaterialMapper;
import com.aimes.mapper.ProdTeamMapper;
import com.aimes.mapper.ProdWorkOrderMapper;
import com.aimes.service.DashboardService;
import com.aimes.service.DeviceService;
import com.aimes.service.PlanService;
import com.aimes.service.ProcessRouteService;
import com.aimes.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class CozeChatPromptServiceTest {

    @Mock
    private ProdWorkOrderMapper prodWorkOrderMapper;
    @Mock
    private ProdTeamMapper prodTeamMapper;
    @Mock
    private MatMaterialMapper matMaterialMapper;
    @Mock
    private ExcEventMapper excEventMapper;
    @Mock
    private DashboardService dashboardService;
    @Mock
    private DeviceService deviceService;
    @Mock
    private PlanService planService;
    @Mock
    private ProcessRouteService processRouteService;
    @Mock
    private ProductService productService;

    @InjectMocks
    private CozeChatPromptService cozeChatPromptService;

    @Test
    void resolvePromptMode_routesDeviceInventoryToRealtime() {
        assertEquals(
                CozeChatPromptMode.REALTIME,
                cozeChatPromptService.resolvePromptMode("现在有哪些设备", List.of(), List.of()));
    }

    @Test
    void resolvePromptMode_routesProcessRouteCountToRealtime() {
        assertEquals(
                CozeChatPromptMode.REALTIME,
                cozeChatPromptService.resolvePromptMode("现在有多少条工艺", List.of(), List.of()));
    }

    @Test
    void resolvePromptMode_routesOperationCountToRealtime() {
        assertEquals(
                CozeChatPromptMode.REALTIME,
                cozeChatPromptService.resolvePromptMode("现在有多少条工序", List.of(), List.of()));
    }

    @Test
    void resolvePromptMode_routesProductStatusToRealtime() {
        assertEquals(
                CozeChatPromptMode.REALTIME,
                cozeChatPromptService.resolvePromptMode("现在的产品情况如何", List.of(), List.of()));
    }

    @Test
    void resolvePromptMode_routesCompletedPlansToRealtime() {
        assertEquals(
                CozeChatPromptMode.REALTIME,
                cozeChatPromptService.resolvePromptMode("已完成的生产计划有哪些", List.of(), List.of()));
    }

    @Test
    void resolvePromptMode_routesOperationalSopToKnowledge() {
        assertEquals(
                CozeChatPromptMode.KNOWLEDGE,
                cozeChatPromptService.resolvePromptMode("物料缺料如何处理", List.of(), List.of()));
    }

    @Test
    void resolvePromptMode_routesDefinitionQuestionToKnowledge() {
        assertEquals(
                CozeChatPromptMode.KNOWLEDGE,
                cozeChatPromptService.resolvePromptMode("什么是工单", List.of(), List.of()));
    }

    @Test
    void resolvePromptMode_followUpInheritsRealtimeFromLastTurn() {
        AiChatLog lastTurn = new AiChatLog();
        lastTurn.setUserMessage("现在有哪些设备");
        lastTurn.setAiResponse("当前共 3 台设备…");

        assertEquals(
                CozeChatPromptMode.REALTIME,
                cozeChatPromptService.resolvePromptMode("还有呢", List.of(), List.of(lastTurn)));
    }

    @Test
    void resolvePromptMode_explicitOrderReferenceToRealtime() {
        List<ProdWorkOrder> orders = List.of(new ProdWorkOrder());
        assertEquals(
                CozeChatPromptMode.REALTIME,
                cozeChatPromptService.resolvePromptMode("WO-2025-001 进度多少", orders, List.of()));
    }
}
