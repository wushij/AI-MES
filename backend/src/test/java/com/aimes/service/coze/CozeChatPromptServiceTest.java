package com.aimes.service.coze;

import com.aimes.entity.AiChatLog;
import com.aimes.entity.ProdWorkOrder;
import com.aimes.entity.SysUser;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void resolvePromptMode_routesPlanCreationHowToToKnowledge() {
        assertEquals(
                CozeChatPromptMode.KNOWLEDGE,
                cozeChatPromptService.resolvePromptMode(
                        "怎么创建生产计划？有哪些限制？",
                        List.of(),
                        List.of()));
    }

    @Test
    void resolvePromptMode_routesPlanListQueryToRealtime() {
        assertEquals(
                CozeChatPromptMode.REALTIME,
                cozeChatPromptService.resolvePromptMode(
                        "已完成的生产计划有哪些",
                        List.of(),
                        List.of()));
    }

    @Test
    void buildChatPrompt_planCreationHowToUsesKnowledgeMode() {
        SysUser user = new SysUser();
        user.setRealName("测试员");
        user.setRole("planner");

        String prompt = cozeChatPromptService.buildChatPrompt(
                user,
                "怎么创建生产计划？有哪些限制？",
                List.of(),
                CozeChatPromptMode.KNOWLEDGE,
                List.of());

        assertTrue(prompt.contains("【知识库问答】"));
        assertFalse(prompt.contains("【本地实时数据】"));
    }

    @Test
    void resolvePromptMode_explicitOrderReferenceToRealtime() {
        List<ProdWorkOrder> orders = List.of(new ProdWorkOrder());
        assertEquals(
                CozeChatPromptMode.REALTIME,
                cozeChatPromptService.resolvePromptMode("WO-2025-001 进度多少", orders, List.of()));
    }

    @Test
    void resolvePromptMode_systemQuestionAfterRealtimeHistory_staysKnowledge() {
        AiChatLog lastTurn = new AiChatLog();
        lastTurn.setUserMessage("当前未处理异常有哪些？");
        lastTurn.setAiResponse("当前共有 2 条未处理异常…");

        assertEquals(
                CozeChatPromptMode.KNOWLEDGE,
                cozeChatPromptService.resolvePromptMode(
                        "AI-MES 是什么系统？技术架构是什么？",
                        List.of(),
                        List.of(lastTurn)));
    }

    @Test
    void isStandaloneKnowledgeQuestion_detectsDefinitionWithoutPronoun() {
        assertTrue(cozeChatPromptService.isStandaloneKnowledgeQuestion("AI-MES 是什么系统？"));
        assertFalse(cozeChatPromptService.isStandaloneKnowledgeQuestion("它是什么状态？"));
    }

    @Test
    void buildChatPrompt_standaloneKnowledgeOmitsSessionHistory() {
        SysUser user = new SysUser();
        user.setRealName("测试员");
        user.setRole("admin");

        AiChatLog lastTurn = new AiChatLog();
        lastTurn.setUserMessage("当前未处理异常有哪些？");
        lastTurn.setAiResponse("当前共有 2 条未处理异常…");

        String prompt = cozeChatPromptService.buildChatPrompt(
                user,
                "AI-MES 是什么系统？",
                List.of(),
                CozeChatPromptMode.KNOWLEDGE,
                List.of(lastTurn));

        assertTrue(prompt.contains("【知识库问答】"));
        assertFalse(prompt.contains("【本会话上下文】"));
        assertFalse(prompt.contains("实时业务数据以下文最新注入为准"));
    }
}

