package com.aimes.service.coze;

import com.aimes.entity.AiChatLog;
import com.aimes.entity.ExcEvent;
import com.aimes.entity.MatMaterial;
import com.aimes.entity.ProdTeam;
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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

@Service
@RequiredArgsConstructor
public class CozeChatPromptService {

    private final ProdWorkOrderMapper prodWorkOrderMapper;
    private final ProdTeamMapper prodTeamMapper;
    private final MatMaterialMapper matMaterialMapper;
    private final ExcEventMapper excEventMapper;
    private final DashboardService dashboardService;
    private final DeviceService deviceService;
    private final ProcessRouteService processRouteService;
    private final PlanService planService;
    private final ProductService productService;

    private void appendSessionHistorySection(StringBuilder builder, List<AiChatLog> sessionHistory) {
        if (sessionHistory.isEmpty()) {
            return;
        }
        builder.append("\n【本会话上下文】\n");
        builder.append("以下为当前对话 session 内的前轮问答，仅供理解追问（如「它」「刚才那个工单」）；");
        builder.append("实时业务数据以下文最新注入为准，不得编造。\n");
        for (AiChatLog log : sessionHistory) {
            builder.append("用户：").append(truncateHistoryText(log.getUserMessage())).append("\n");
            builder.append("助手：").append(truncateHistoryText(log.getAiResponse())).append("\n");
        }
    }

    private String truncateHistoryText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").trim();
        if (normalized.length() <= CozeConstants.SESSION_HISTORY_MAX_CHARS) {
            return normalized;
        }
        return normalized.substring(0, CozeConstants.SESSION_HISTORY_MAX_CHARS) + "…";
    }

    public List<ProdWorkOrder> resolveReferencedOrdersFromText(String message) {
        if (!StringUtils.hasText(message)) {
            return List.of();
        }
        List<ProdWorkOrder> result = new ArrayList<>();
        Matcher matcher = CozeConstants.ORDER_NO_PATTERN.matcher(message.toUpperCase());
        while (matcher.find()) {
            ProdWorkOrder order = findWorkOrderByNo(matcher.group());
            if (order != null && result.stream().noneMatch(item -> item.getId().equals(order.getId()))) {
                result.add(order);
            }
        }
        return result;
    }

    public List<ProdWorkOrder> resolveReferencedOrdersFromHistory(List<AiChatLog> sessionHistory) {
        ProdWorkOrder lastMatch = null;
        for (AiChatLog log : sessionHistory) {
            for (String text : List.of(log.getUserMessage(), log.getAiResponse())) {
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                Matcher matcher = CozeConstants.ORDER_NO_PATTERN.matcher(text.toUpperCase());
                while (matcher.find()) {
                    ProdWorkOrder order = findWorkOrderByNo(matcher.group());
                    if (order != null) {
                        lastMatch = order;
                    }
                }
            }
        }
        return lastMatch == null ? List.of() : List.of(lastMatch);
    }

    private ProdWorkOrder findWorkOrderByNo(String orderNo) {
        return prodWorkOrderMapper.selectOne(new LambdaQueryWrapper<ProdWorkOrder>()
                .eq(ProdWorkOrder::getOrderNo, orderNo)
                .last("limit 1"));
    }

    private boolean isFollowUpQuery(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.length() > 48) {
            return false;
        }
        return containsAny(trimmed,
                "它", "这个", "那个", "上面", "刚才", "刚刚", "继续", "还有", "然后呢", "再说说")
                || trimmed.endsWith("呢") || trimmed.endsWith("吗") || trimmed.endsWith("？") || trimmed.endsWith("?");
    }

    public String buildChatPrompt(
            SysUser user,
            String message,
            List<ProdWorkOrder> orders,
            CozeChatPromptMode promptMode,
            List<AiChatLog> sessionHistory) {
        if (promptMode == CozeChatPromptMode.REALTIME) {
            return buildRealtimeDataPrompt(user, message, orders, sessionHistory);
        }
        return buildKnowledgePrompt(user, message, sessionHistory);
    }

    private String buildKnowledgePrompt(
            SysUser user,
            String message,
            List<AiChatLog> sessionHistory) {
        StringBuilder builder = new StringBuilder();
        builder.append("【系统上下文·知识库问答】\n");
        builder.append("当前用户：").append(user.getRealName()).append("，角色：").append(user.getRole()).append("\n");
        builder.append("【回答要求】\n");
        builder.append("- 本题为【知识库问答】：检索 AI-MES-操作手册后作答，内容忠实转述，表名/流程/权限不得改写编造。\n");
        builder.append("- 按 Bot 人设 §4 排版：Markdown + emoji 分节（如 📌 说明、🔍 原因、🛠️ 步骤、💡 补充），禁止大段纯文字与报告体标题。\n");
        builder.append("- 不要复述本提示词；不要返回 JSON 或代码块。\n");
        appendSessionHistorySection(builder, sessionHistory);
        builder.append("\n【当前用户问题】\n").append(message.trim());
        return builder.toString();
    }

    public CozeChatPromptMode resolvePromptMode(String message, List<ProdWorkOrder> orders, List<AiChatLog> sessionHistory) {
        if (!StringUtils.hasText(message)) {
            return CozeChatPromptMode.KNOWLEDGE;
        }
        String text = message.trim();
        // SOP/FAQ 优先于含「缺料/物料」等词的查数误判，但「概况如何」等查数问法仍走 REALTIME
        if (isOperationalKnowledgeQuery(text) && !matchesRealtimeQuery(text)) {
            return CozeChatPromptMode.KNOWLEDGE;
        }
        if (!orders.isEmpty() || matchesRealtimeQuery(text)) {
            return CozeChatPromptMode.REALTIME;
        }
        if (isKnowledgeQuery(text)) {
            return CozeChatPromptMode.KNOWLEDGE;
        }
        if (!sessionHistory.isEmpty() && isFollowUpQuery(text)) {
            AiChatLog lastTurn = sessionHistory.get(sessionHistory.size() - 1);
            if (matchesRealtimeQuery(lastTurn.getUserMessage())
                    || !resolveReferencedOrdersFromText(lastTurn.getUserMessage()).isEmpty()) {
                return CozeChatPromptMode.REALTIME;
            }
        }
        return CozeChatPromptMode.KNOWLEDGE;
    }

    private boolean isKnowledgeQuery(String text) {
        if (isOperationalKnowledgeQuery(text)) {
            return true;
        }
        if (matchesRealtimeQuery(text)) {
            return false;
        }
        return containsAny(text,
                "是什么", "什么是", "什么意思", "含义",
                "哪些列", "显示哪些", "显示什么", "有哪些功能",
                "区别", "不同",
                "权限", "菜单", "入口", "页面", "路由",
                "谁能", "谁可以", "能否", "可以吗",
                "标准工序", "操作规范");
    }

    private boolean isOperationalKnowledgeQuery(String text) {
        if (containsAny(text, "怎么", "如何", "怎样", "怎么办")
                && containsAny(text, "处理", "操作", "上报", "应对", "办")) {
            return true;
        }
        if (containsAny(text, "步骤", "流程", "SOP", "手册")) {
            return true;
        }
        if (text.contains("应该") && containsAny(text, "处理", "操作")) {
            return true;
        }
        return isMaterialModuleKnowledgeQuery(text);
    }

    private boolean matchesRealtimeQuery(String text) {
        if (text.toUpperCase().contains("WO-") || text.toUpperCase().contains("MAT-")) {
            return true;
        }
        if (containsAny(text, "概况", "预警物料", "交期", "在制", "待派工", "待认领", "已派工")) {
            return true;
        }
        if (text.contains("库存") || text.contains("缺货")) {
            return true;
        }
        if (text.contains("今日") && containsAny(text, "生产", "完工", "任务", "概况")) {
            return true;
        }
        if (text.contains("进度") && containsAny(text, "多少", "几", "百分比", "%")) {
            return true;
        }
        if (text.contains("进度") && text.toUpperCase().contains("WO-")) {
            return true;
        }
        if (containsAny(text, "甲班", "乙班", "丙班")
                && containsAny(text, "任务", "工单", "哪些", "多少")) {
            return true;
        }
        if (text.contains("计划") && containsAny(text, "状态", "多少", "几个", "几条", "哪些")) {
            return true;
        }
        // 模块说明 / 页面结构 / 权限 / SOP 走知识库，勿因含「物料」「预警」「缺料」误判为查数
        if (isMaterialModuleKnowledgeQuery(text)) {
            return false;
        }
        if (text.contains("缺料") && containsAny(text, "多少", "几个", "哪些", "当前", "触发", "涉及", "预警")) {
            return true;
        }
        if (text.contains("物料") && containsAny(text, "多少", "库存", "缺口")) {
            return true;
        }
        if (text.contains("物料") && containsAny(text, "预警", "缺料")
                && containsAny(text, "多少", "几个", "哪些", "列表", "当前", "触发", "涉及")) {
            return true;
        }
        if (text.contains("工单") && containsAny(text, "多少", "几个", "几条", "列表", "状态")) {
            return true;
        }
        if (text.contains("异常") && containsAny(text,
                "多少", "几个", "哪些", "列表", "当前", "最新",
                "待处理", "未处理", "open", "processing")) {
            return true;
        }
        if (matchesDeviceRealtimeQuery(text)) {
            return true;
        }
        if (matchesProcessRealtimeQuery(text)) {
            return true;
        }
        if (matchesOperationRealtimeQuery(text)) {
            return true;
        }
        if (matchesPlanRealtimeQuery(text)) {
            return true;
        }
        if (matchesProductRealtimeQuery(text)) {
            return true;
        }
        return false;
    }

    private boolean matchesProductRealtimeQuery(String text) {
        if (!text.contains("产品")) {
            return false;
        }
        if (text.toUpperCase().contains("PRD-")) {
            return true;
        }
        if (isMaterialModuleKnowledgeQuery(text)) {
            return false;
        }
        if (containsAny(text, "情况", "概况", "库存", "成品", "在产", "在制", "台账")) {
            return true;
        }
        if (containsAny(text, "SOP", "手册", "步骤", "应该", "规范")) {
            return false;
        }
        if (containsAny(text, "怎么", "如何", "怎样", "怎么办")
                && containsAny(text, "处理", "操作", "上报", "维护", "管理", "创建", "新增", "录入")) {
            return false;
        }
        return containsAny(text,
                "多少", "几个", "哪些", "列表", "当前", "现在", "有哪些", "条", "状态", "呢", "如何", "怎样");
    }

    private boolean matchesPlanRealtimeQuery(String text) {
        if (!containsAny(text, "计划", "生产计划")) {
            return false;
        }
        if (isMaterialModuleKnowledgeQuery(text)) {
            return false;
        }
        if (containsAny(text, "SOP", "手册", "步骤", "怎么", "如何", "怎样", "怎么办", "应该")) {
            return false;
        }
        return containsAny(text,
                "多少", "几个", "哪些", "列表", "当前", "现在", "有哪些", "条", "状态", "呢",
                "已完成", "完成", "草稿", "下发", "已下发", "进行中", "暂停");
    }

    private boolean matchesOperationRealtimeQuery(String text) {
        if (!text.contains("工序")) {
            return false;
        }
        if (text.contains("标准工序") || text.toUpperCase().contains("WO-") || text.contains("工单")) {
            return false;
        }
        if (text.contains("任务") && containsAny(text, "认领", "待办", "待领", "班组", "班")) {
            return false;
        }
        if (text.contains("进度") && !containsAny(text, "多少", "几个", "哪些", "条", "总数")) {
            return false;
        }
        if (isMaterialModuleKnowledgeQuery(text)) {
            return false;
        }
        if (containsAny(text, "SOP", "手册", "步骤", "怎么", "如何", "怎样", "怎么办", "应该", "规范", "操作")) {
            return false;
        }
        return containsAny(text,
                "多少", "几个", "哪些", "列表", "当前", "现在", "有哪些", "条", "总数", "一共");
    }

    private boolean matchesProcessRealtimeQuery(String text) {
        if (!containsAny(text, "工艺", "工艺路线")) {
            return false;
        }
        if (isMaterialModuleKnowledgeQuery(text)) {
            return false;
        }
        if (containsAny(text, "SOP", "手册", "步骤", "怎么", "如何", "怎样", "怎么办", "应该")) {
            return false;
        }
        return containsAny(text,
                "多少", "几个", "哪些", "列表", "当前", "现在", "有哪些", "状态", "条",
                "待审批", "已发布", "草稿", "发布", "驳回", "停用");
    }

    private boolean matchesDeviceRealtimeQuery(String text) {
        if (!text.contains("设备")) {
            return false;
        }
        if (isMaterialModuleKnowledgeQuery(text)) {
            return false;
        }
        if (containsAny(text, "SOP", "手册", "步骤", "流程", "怎么", "如何", "怎样", "怎么办", "应该")) {
            return false;
        }
        return containsAny(text,
                "多少", "几个", "哪些", "列表", "当前", "现在", "有哪些", "状态",
                "故障", "停机", "运行", "保养", "点检", "维修", "预警", "异常", "概况");
    }

    private boolean isMaterialModuleKnowledgeQuery(String text) {
        return containsAny(text,
                "模块", "页面", "做什么", "谁能", "谁可以", "访问", "功能", "菜单", "入口",
                "有哪些内容", "显示什么", "展示什么", "显示哪些", "有哪些功能",
                "步骤", "流程", "SOP", "手册", "上报")
                || containsAny(text, "是什么", "什么是");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String buildRealtimeDataPrompt(
            SysUser user,
            String message,
            List<ProdWorkOrder> orders,
            List<AiChatLog> sessionHistory) {
        List<ProdWorkOrder> activeOrders = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                .orderByDesc(ProdWorkOrder::getId)
                .last("limit 20"));
        Map<String, Object> planSummary = planService.summary();
        List<MatMaterial> warningMaterials = matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                .eq(MatMaterial::getAlertStatus, "warning"));
        List<ExcEvent> openExceptions = excEventMapper.selectList(new LambdaQueryWrapper<ExcEvent>()
                .in(ExcEvent::getStatus, List.of("open", "processing"))
                .last("ORDER BY FIELD(status, 'open', 'processing'), occur_time DESC limit 20"));

        StringBuilder builder = new StringBuilder();
        builder.append("你是 AI-MES 车间生产助手。请结合以下【本地实时数据】回答用户问题。\n");
        builder.append("【实时数据说明】以下由后端从 MySQL 实时查询注入，工单进度、库存、计划状态以此为准；");
        builder.append("勿调用 Bot 插件/云端数据库；知识库中的演示数字不得覆盖此处数据。\n");
        builder.append("当前系统时间：").append(LocalDateTime.now()).append("\n");
        builder.append("当前用户：").append(user.getRealName()).append("，角色：").append(user.getRole()).append("\n");

        appendPlanRealtimeSection(builder, message, planSummary);
        appendProductRealtimeSection(builder, message);

        if (!activeOrders.isEmpty()) {
            builder.append("\n【本地实时数据·工单（最新20条）】\n");
            for (ProdWorkOrder order : activeOrders) {
                ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
                builder.append("- 工单号=").append(order.getOrderNo())
                        .append(" 产品=").append(order.getProductName())
                        .append(" 进度=").append(order.getProgress()).append("%")
                        .append(" 工序=").append(order.getProcessName())
                        .append(" 班组=").append(team == null ? "未分配" : team.getTeamName())
                        .append(" 状态=").append(translateWorkOrderStatus(order.getStatus()))
                        .append("\n");
            }
        }

        if (!warningMaterials.isEmpty()) {
            builder.append("\n【本地实时数据·预警物料（全部）】\n");
            for (MatMaterial mat : warningMaterials) {
                builder.append("- 物料编码=").append(mat.getMaterialCode())
                        .append(" 名称=").append(mat.getMaterialName())
                        .append(" 库存=").append(mat.getStockQty()).append(mat.getUnit())
                        .append(" 安全库存=").append(mat.getSafetyStock()).append(mat.getUnit())
                        .append("\n");
            }
        }

        appendDeviceRealtimeSection(builder, message);
        appendProcessRealtimeSection(builder, message);
        appendOperationRealtimeSection(builder, message);

        builder.append("\n【本地实时数据·未处理异常（open/processing，最新20条）】\n");
        if (openExceptions.isEmpty()) {
            builder.append("- 当前无未处理异常\n");
        } else {
            for (ExcEvent event : openExceptions) {
                ProdWorkOrder relatedOrder = event.getWorkOrderId() == null
                        ? null
                        : prodWorkOrderMapper.selectById(event.getWorkOrderId());
                builder.append("- 异常号=").append(event.getEventNo())
                        .append(" 类型=").append(translateExceptionType(event.getEventType()))
                        .append(" 状态=").append(translateExceptionStatus(event.getStatus()))
                        .append(" 关联工单=").append(relatedOrder == null ? "无" : relatedOrder.getOrderNo())
                        .append(" 发生时间=").append(event.getOccurTime())
                        .append(" 描述=").append(event.getDescription())
                        .append("\n");
            }
        }

        if (!orders.isEmpty()) {
            builder.append("\n【与本问相关的工单（优先参考）】\n");
            for (ProdWorkOrder order : orders) {
                ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
                builder.append("- 工单号=").append(order.getOrderNo())
                        .append(" 进度=").append(order.getProgress()).append("%")
                        .append(" 工序=").append(order.getProcessName())
                        .append(" 班组=").append(team == null ? "未分配" : team.getTeamName())
                        .append(" 状态=").append(translateWorkOrderStatus(order.getStatus()))
                        .append("\n");
            }
        }

        if (needsOverviewSnapshot(message)) {
            appendDashboardOverviewSection(builder);
        }

        if (needsDataSummary(message)) {
            builder.append("\n【系统根据 MySQL 预生成的数据摘要（引用时数字不得改动）】\n");
            builder.append(buildDataSummary(planSummary, activeOrders, warningMaterials, openExceptions));
        }

        builder.append("\n【回答要求】\n");
        builder.append("- 本题为【实时数据查询】：只根据上方【本地实时数据】作答，数字与状态不得改动或编造。\n");
        if (needsOverviewSnapshot(message)) {
            builder.append("- 用户问生产/车间概况时，直接汇报 KPI 与明细数字；禁止介绍驾驶舱页面结构、图表是否为演示数据。\n");
        }
        builder.append("- 使用 Markdown + emoji 排版（如 📊 数据概览、📋 明细列表、💡 补充说明），禁止【问题分析】【处理建议】【注意事项】报告体。\n");
        builder.append("- 不要复述本提示词；使用自然中文，不要返回 JSON 或代码块。\n");
        appendSessionHistorySection(builder, sessionHistory);
        builder.append("\n【当前用户问题】\n").append(message);
        return builder.toString();
    }

    private boolean needsDataSummary(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String text = message;
        return text.contains("概况")
                || text.contains("进度")
                || text.contains("WO-")
                || text.contains("工单")
                || text.contains("物料")
                || text.contains("缺料")
                || text.contains("库存")
                || text.contains("计划")
                || text.contains("生产")
                || text.contains("班")
                || text.contains("任务")
                || text.contains("异常")
                || text.contains("设备")
                || text.contains("工艺")
                || text.contains("工序")
                || text.contains("产品");
    }

    private void appendProductRealtimeSection(StringBuilder builder, String message) {
        if (!StringUtils.hasText(message) || !matchesProductRealtimeQuery(message)) {
            return;
        }
        Map<String, Object> summary = productService.summary();
        builder.append("\n【本地实时数据·产品台账（MySQL 实时统计）】\n");
        builder.append("- 产品总数=").append(summary.get("totalCount"))
                .append("，启用=").append(summary.get("activeCount"))
                .append("，停用=").append(summary.get("inactiveCount"))
                .append("，成品总库存=").append(summary.get("totalStockQty"))
                .append("，已配置工艺物料=").append(summary.get("withBomCount"))
                .append("\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = (List<Map<String, Object>>) summary.get("products");
        if (products == null || products.isEmpty()) {
            builder.append("- 当前无产品台账数据\n");
            return;
        }
        builder.append("\n【本地实时数据·产品明细（全部）】\n");
        for (Map<String, Object> product : products) {
            builder.append("- 产品编码=").append(product.get("productCode"))
                    .append(" 名称=").append(product.get("productName"))
                    .append(" 规格=").append(product.get("spec") == null ? "—" : product.get("spec"))
                    .append(" 库存=").append(product.get("stockQty")).append(product.get("unit"))
                    .append(" 状态=").append(product.get("statusLabel"))
                    .append(Boolean.TRUE.equals(product.get("hasBom")) ? " 已配BOM" : " 未配BOM")
                    .append("\n");
        }
    }

    private void appendPlanRealtimeSection(StringBuilder builder, String message, Map<String, Object> summary) {
        if (!StringUtils.hasText(message) || !matchesPlanRealtimeQuery(message)) {
            return;
        }
        builder.append("\n【本地实时数据·生产计划（MySQL 实时统计）】\n");
        builder.append("- 计划总数=").append(summary.get("totalCount"))
                .append("，草稿=").append(summary.get("draftCount"))
                .append("，已下发=").append(summary.get("releasedCount"))
                .append("，已完成=").append(summary.get("completedCount"))
                .append("，已暂停=").append(summary.get("pausedCount"))
                .append("\n");

        List<Map<String, Object>> detailPlans;
        if (message.contains("已完成") || message.contains("完成的计划") || message.contains("完成的生产计划")) {
            detailPlans = planService.listBriefByStatuses(List.of("done", "completed"), 20);
            builder.append("\n【本地实时数据·已完成生产计划明细】\n");
        } else if (message.contains("草稿")) {
            detailPlans = planService.listBriefByStatuses(List.of("draft"), 20);
            builder.append("\n【本地实时数据·草稿生产计划明细】\n");
        } else if (message.contains("下发")) {
            detailPlans = planService.listBriefByStatuses(List.of("released"), 20);
            builder.append("\n【本地实时数据·已下发生产计划明细】\n");
        } else {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> all = (List<Map<String, Object>>) summary.get("plans");
            detailPlans = all == null ? List.of() : all.stream().limit(20).toList();
            builder.append("\n【本地实时数据·生产计划明细（最新20条）】\n");
        }

        if (detailPlans.isEmpty()) {
            builder.append("- 当前无匹配的生产计划数据\n");
            return;
        }
        for (Map<String, Object> plan : detailPlans) {
            builder.append("- 计划号=").append(plan.get("planNo"))
                    .append(" 产品=").append(plan.get("productName"))
                    .append(" 数量=").append(plan.get("planQty"))
                    .append(" 计划日期=").append(plan.get("planDate"))
                    .append(" 状态=").append(plan.get("statusLabel"))
                    .append("\n");
        }
    }

    private void appendOperationRealtimeSection(StringBuilder builder, String message) {
        if (!StringUtils.hasText(message) || !matchesOperationRealtimeQuery(message)) {
            return;
        }
        Map<String, Object> summary = processRouteService.operationSummary();
        builder.append("\n【本地实时数据·工序主数据（MySQL 实时统计）】\n");
        builder.append("- 工序总数=").append(summary.get("totalCount"))
                .append("，分布在 ").append(summary.get("routingCount")).append(" 条工艺路线中\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> operations = (List<Map<String, Object>>) summary.get("operations");
        if (operations == null || operations.isEmpty()) {
            builder.append("- 当前无工序主数据\n");
            return;
        }
        builder.append("\n【本地实时数据·工序明细（全部）】\n");
        for (Map<String, Object> operation : operations) {
            builder.append("- 工序序号=").append(operation.get("seqNo"))
                    .append(" 名称=").append(operation.get("operationName"))
                    .append(" 编码=").append(operation.get("operationCode") == null ? "—" : operation.get("operationCode"))
                    .append(" 所属工艺=").append(operation.get("routeName") == null ? "—" : operation.get("routeName"))
                    .append("（").append(operation.get("routeCode") == null ? "—" : operation.get("routeCode"))
                    .append("）")
                    .append(" 产品=").append(operation.get("productName") == null ? "通用" : operation.get("productName"))
                    .append("\n");
        }
    }

    private void appendProcessRealtimeSection(StringBuilder builder, String message) {
        if (!StringUtils.hasText(message) || !matchesProcessRealtimeQuery(message)) {
            return;
        }
        Map<String, Object> summary = processRouteService.summary();
        builder.append("\n【本地实时数据·工艺路线（MySQL 实时统计）】\n");
        builder.append("- 工艺总数=").append(summary.get("totalCount"))
                .append("，草稿=").append(summary.get("draftCount"))
                .append("，待审批=").append(summary.get("pendingApprovalCount"))
                .append("，已发布=").append(summary.get("publishedCount"))
                .append("，已驳回=").append(summary.get("rejectedCount"))
                .append("，已停用=").append(summary.get("disabledCount"))
                .append("\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> routes = (List<Map<String, Object>>) summary.get("routes");
        if (routes == null || routes.isEmpty()) {
            builder.append("- 当前无工艺路线数据\n");
            return;
        }
        builder.append("\n【本地实时数据·工艺路线明细（全部）】\n");
        for (Map<String, Object> route : routes) {
            builder.append("- 工艺编号=").append(route.get("routeCode"))
                    .append(" 名称=").append(route.get("routeName"))
                    .append(" 产品=").append(route.get("productName") == null ? "通用" : route.get("productName"))
                    .append(" 版本=").append(route.get("version") == null ? "—" : route.get("version"))
                    .append(" 状态=").append(route.get("statusLabel"))
                    .append(route.get("isDefault") == Boolean.TRUE ? " 默认" : "")
                    .append("\n");
        }
    }

    private void appendDeviceRealtimeSection(StringBuilder builder, String message) {
        if (!StringUtils.hasText(message)) {
            return;
        }
        if (!matchesDeviceRealtimeQuery(message) && !needsOverviewSnapshot(message)) {
            return;
        }
        Map<String, Object> summary = deviceService.summary();
        builder.append("\n【本地实时数据·设备台账（MySQL 实时统计）】\n");
        builder.append("- 设备总数=").append(summary.get("totalCount"))
                .append("，运行中=").append(summary.get("runningCount"))
                .append("，空闲=").append(summary.get("idleCount"))
                .append("，故障/维修=").append(summary.get("faultCount"))
                .append("，保养中=").append(summary.get("maintenanceCount"))
                .append("，今日报警=").append(summary.get("todayAlertCount"))
                .append("，保养逾期=").append(summary.get("maintenanceOverdueCount"))
                .append("，平均利用率=").append(summary.get("avgUtilizationRate")).append("%\n");

        if (!matchesDeviceRealtimeQuery(message == null ? "" : message)) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> devices = (List<Map<String, Object>>) summary.get("devices");
        if (devices == null || devices.isEmpty()) {
            builder.append("- 当前无设备台账数据\n");
            return;
        }
        builder.append("\n【本地实时数据·设备明细（全部）】\n");
        for (Map<String, Object> device : devices) {
            builder.append("- 设备编号=").append(device.get("deviceCode"))
                    .append(" 名称=").append(device.get("deviceName"))
                    .append(" 产线=").append(device.get("lineName") == null ? "—" : device.get("lineName"))
                    .append(" 车间=").append(device.get("workshop") == null ? "—" : device.get("workshop"))
                    .append(" 状态=").append(device.get("statusLabel"))
                    .append("\n");
        }
    }

    private boolean needsOverviewSnapshot(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String text = message.trim();
        return text.contains("概况")
                || text.contains("驾驶舱")
                || (text.contains("今日") && containsAny(text, "生产", "车间", "概况", "完工"));
    }

    private void appendDashboardOverviewSection(StringBuilder builder) {
        Map<String, Object> stats = dashboardService.stats();
        builder.append("\n【本地实时数据·今日驾驶舱 KPI（MySQL 统计，与首页一致）】\n");
        builder.append("- 今日计划数=").append(stats.get("planCount"))
                .append("，较昨日=").append(stats.get("planTrend")).append("\n");
        builder.append("- 在制工单数=").append(stats.get("inProgressWorkOrderCount"))
                .append("，较昨日新增趋势=").append(stats.get("inProgressTrend")).append("\n");
        builder.append("- 未处理异常=").append(stats.get("openExceptionCount"))
                .append("，今日新增=").append(stats.get("newExceptionCount")).append("\n");
        builder.append("- 缺料预警=").append(stats.get("materialAlertCount"))
                .append("，今日新增=").append(stats.get("newMaterialAlertCount")).append("\n");
        builder.append("- 设备总数=").append(stats.get("deviceTotalCount"))
                .append("，运行中=").append(stats.get("deviceRunningCount"))
                .append("，故障/维修=").append(stats.get("deviceFaultCount"))
                .append("，今日设备报警=").append(stats.get("deviceTodayAlertCount"))
                .append("，保养逾期=").append(stats.get("deviceMaintenanceOverdueCount"))
                .append("，平均利用率=").append(stats.get("deviceAvgUtilizationRate")).append("%\n");

        List<Map<String, Object>> teamProgress = dashboardService.progress();
        if (!teamProgress.isEmpty()) {
            builder.append("\n【本地实时数据·各班组平均进度（MySQL 真实统计）】\n");
            for (Map<String, Object> team : teamProgress) {
                builder.append("- ").append(team.get("teamName"))
                        .append(" 平均进度=").append(team.get("avgProgress")).append("%")
                        .append(" 生产中=").append(team.get("producingCount"))
                        .append(" 已完成=").append(team.get("doneCount"))
                        .append("\n");
            }
        }
    }

    private String buildDataSummary(
            Map<String, Object> planSummary,
            List<ProdWorkOrder> orders,
            List<MatMaterial> warnings,
            List<ExcEvent> openExceptions) {
        StringBuilder summary = new StringBuilder();
        summary.append("生产计划：共 ").append(planSummary.get("totalCount"))
                .append(" 个（已下发 ").append(planSummary.get("releasedCount"))
                .append("，已完成 ").append(planSummary.get("completedCount"))
                .append("，草稿 ").append(planSummary.get("draftCount")).append("）。");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> plans = (List<Map<String, Object>>) planSummary.get("plans");
        if (plans != null && !plans.isEmpty()) {
            Map<String, Object> sample = plans.get(0);
            summary.append(" 例如 ").append(sample.get("planNo"))
                    .append("（").append(sample.get("productName"))
                    .append("，").append(sample.get("planQty")).append("件，")
                    .append(sample.get("statusLabel")).append("）。");
        }
        long inProgress = orders.stream()
                .filter(o -> List.of("assigned", "producing", "exception").contains(o.getStatus()))
                .count();
        summary.append(" 在制工单 ").append(inProgress).append(" 个。");
        if (!orders.isEmpty()) {
            summary.append(" 工单明细：");
            int limit = Math.min(orders.size(), 5);
            for (int i = 0; i < limit; i++) {
                ProdWorkOrder order = orders.get(i);
                ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
                if (i > 0) {
                    summary.append("；");
                }
                summary.append(order.getOrderNo())
                        .append(" 进度").append(order.getProgress()).append("%")
                        .append(" 工序").append(order.getProcessName())
                        .append(" 班组").append(team == null ? "未分配" : team.getTeamName())
                        .append(" 状态").append(translateWorkOrderStatus(order.getStatus()));
            }
            summary.append("。");
        }
        summary.append(" 预警物料 ").append(warnings.size()).append(" 项。");
        if (!warnings.isEmpty()) {
            summary.append(" 明细：");
            for (int i = 0; i < warnings.size(); i++) {
                MatMaterial mat = warnings.get(i);
                if (i > 0) {
                    summary.append("；");
                }
                summary.append(mat.getMaterialCode())
                        .append(" ").append(mat.getMaterialName())
                        .append(" 库存").append(mat.getStockQty()).append(mat.getUnit())
                        .append(" 安全库存").append(mat.getSafetyStock()).append(mat.getUnit());
            }
            summary.append("。");
        }
        summary.append(" 未处理异常 ").append(openExceptions.size()).append(" 项。");
        if (!openExceptions.isEmpty()) {
            summary.append(" 明细：");
            int limit = Math.min(openExceptions.size(), 5);
            for (int i = 0; i < limit; i++) {
                ExcEvent event = openExceptions.get(i);
                ProdWorkOrder relatedOrder = event.getWorkOrderId() == null
                        ? null
                        : prodWorkOrderMapper.selectById(event.getWorkOrderId());
                if (i > 0) {
                    summary.append("；");
                }
                summary.append(event.getEventNo())
                        .append(" ").append(translateExceptionType(event.getEventType()))
                        .append(" ").append(translateExceptionStatus(event.getStatus()))
                        .append(" 工单").append(relatedOrder == null ? "无" : relatedOrder.getOrderNo());
            }
            summary.append("。");
        }
        return summary.toString();
    }

    private String translateExceptionStatus(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case "open" -> "待处理";
            case "processing" -> "处理中";
            case "closed" -> "已关闭";
            default -> status;
        };
    }

    private String translateExceptionType(String type) {
        if (type == null) {
            return "其他";
        }
        return switch (type) {
            case "device" -> "设备停机";
            case "material", "shortage" -> "缺料";
            case "quality" -> "质量异常";
            default -> "其他";
        };
    }

    private String translateWorkOrderStatus(String status) {
        if (status == null) return "未知";
        return switch (status) {
            case "pending" -> "已排产/待领工";
            case "assigned" -> "已指派/待开工";
            case "producing" -> "进行中";
            case "completed", "done" -> "已完成";
            case "exception" -> "异常";
            default -> status;
        };
    }

    public String buildMockReply(String message, List<ProdWorkOrder> orders, SysUser user) {
        if (!orders.isEmpty()) {
            ProdWorkOrder order = orders.get(0);
            ProdTeam team = order.getTeamId() == null ? null : prodTeamMapper.selectById(order.getTeamId());
            if (message.contains("交期") || message.contains("截止")) {
                return String.format("%s 计划交期为 %s。",
                        order.getOrderNo(),
                        order.getDeadline() == null ? "未设置" : order.getDeadline());
            }
            if (message.contains("进度") || message.toUpperCase().contains("WO-") || isFollowUpQuery(message)) {
                return String.format("%s 当前进度 %d%%，处于%s工序，负责班组：%s，状态：%s。",
                        order.getOrderNo(),
                        order.getProgress() == null ? 0 : order.getProgress(),
                        order.getProcessName(),
                        team == null ? "未分配" : team.getTeamName(),
                        order.getStatus());
            }
        }
        if (message.contains("班") || message.contains("任务")) {
            List<ProdWorkOrder> teamOrders = prodWorkOrderMapper.selectList(new LambdaQueryWrapper<ProdWorkOrder>()
                    .eq(user.getTeamId() != null, ProdWorkOrder::getTeamId, user.getTeamId())
                    .in(ProdWorkOrder::getStatus, List.of("assigned", "producing", "exception"))
                    .orderByAsc(ProdWorkOrder::getPriority)
                    .last("limit 5"));
            if (!teamOrders.isEmpty()) {
                return "当前班组重点任务：" + teamOrders.stream()
                        .map(order -> order.getOrderNo() + "（" + order.getProcessName() + "，" + order.getStatus() + "）")
                        .reduce((a, b) -> a + "；" + b)
                        .orElse("暂无任务");
            }
        }
        if (needsOverviewSnapshot(message) || (message.contains("概况") && matchesRealtimeQuery(message))) {
            Map<String, Object> stats = dashboardService.stats();
            return String.format(
                    "📊 今日车间生产概况：今日计划 %s 个（%s），在制工单 %s 个（%s），未处理异常 %s 项（今日新增 %s），缺料预警 %s 项（今日新增 %s）。",
                    stats.get("planCount"), stats.get("planTrend"),
                    stats.get("inProgressWorkOrderCount"), stats.get("inProgressTrend"),
                    stats.get("openExceptionCount"), stats.get("newExceptionCount"),
                    stats.get("materialAlertCount"), stats.get("newMaterialAlertCount"));
        }
        if (message.contains("物料") || message.contains("缺料")) {
            List<MatMaterial> warnings = matMaterialMapper.selectList(new LambdaQueryWrapper<MatMaterial>()
                    .eq(MatMaterial::getAlertStatus, "warning"));
            if (!warnings.isEmpty()) {
                MatMaterial material = warnings.get(0);
                return String.format("当前有 %d 项缺料预警。示例：%s 库存 %s%s，安全库存 %s%s，建议优先补料。",
                        warnings.size(),
                        material.getMaterialName(),
                        material.getStockQty(),
                        material.getUnit(),
                        material.getSafetyStock(),
                        material.getUnit());
            }
        }
        if (message.contains("异常") && matchesRealtimeQuery(message)) {
            List<ExcEvent> openExceptions = excEventMapper.selectList(new LambdaQueryWrapper<ExcEvent>()
                    .in(ExcEvent::getStatus, List.of("open", "processing"))
                    .last("ORDER BY FIELD(status, 'open', 'processing'), occur_time DESC limit 10"));
            if (openExceptions.isEmpty()) {
                return "当前没有未处理异常（待处理/处理中均为 0）。";
            }
            StringBuilder reply = new StringBuilder("当前未处理异常共 ")
                    .append(openExceptions.size())
                    .append(" 项：");
            for (int i = 0; i < openExceptions.size(); i++) {
                ExcEvent event = openExceptions.get(i);
                ProdWorkOrder relatedOrder = event.getWorkOrderId() == null
                        ? null
                        : prodWorkOrderMapper.selectById(event.getWorkOrderId());
                if (i > 0) {
                    reply.append("；");
                }
                reply.append(event.getEventNo())
                        .append("（").append(translateExceptionType(event.getEventType()))
                        .append("，").append(translateExceptionStatus(event.getStatus()))
                        .append("，工单 ")
                        .append(relatedOrder == null ? "无" : relatedOrder.getOrderNo())
                        .append("）");
            }
            reply.append("。");
            return reply.toString();
        }
        if (matchesDeviceRealtimeQuery(message)) {
            Map<String, Object> summary = deviceService.summary();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> devices = (List<Map<String, Object>>) summary.get("devices");
            if (devices == null || devices.isEmpty()) {
                return "当前系统中暂无设备台账数据。";
            }
            StringBuilder reply = new StringBuilder("📊 当前共有 ")
                    .append(summary.get("totalCount"))
                    .append(" 台设备（运行中 ")
                    .append(summary.get("runningCount"))
                    .append("，故障/维修 ")
                    .append(summary.get("faultCount"))
                    .append("）：");
            int limit = Math.min(devices.size(), 10);
            for (int i = 0; i < limit; i++) {
                Map<String, Object> device = devices.get(i);
                if (i > 0) {
                    reply.append("；");
                }
                reply.append(device.get("deviceCode"))
                        .append(" ")
                        .append(device.get("deviceName"))
                        .append("（")
                        .append(device.get("statusLabel"))
                        .append("）");
            }
            if (devices.size() > limit) {
                reply.append("；等共 ").append(devices.size()).append(" 台");
            }
            reply.append("。");
            return reply.toString();
        }
        if (matchesProcessRealtimeQuery(message)) {
            Map<String, Object> summary = processRouteService.summary();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> routes = (List<Map<String, Object>>) summary.get("routes");
            if (routes == null || routes.isEmpty()) {
                return "当前系统中暂无工艺路线数据。";
            }
            StringBuilder reply = new StringBuilder("📊 当前共有 ")
                    .append(summary.get("totalCount"))
                    .append(" 条工艺（已发布 ")
                    .append(summary.get("publishedCount"))
                    .append("，待审批 ")
                    .append(summary.get("pendingApprovalCount"))
                    .append("，草稿 ")
                    .append(summary.get("draftCount"))
                    .append("）：");
            int limit = Math.min(routes.size(), 10);
            for (int i = 0; i < limit; i++) {
                Map<String, Object> route = routes.get(i);
                if (i > 0) {
                    reply.append("；");
                }
                reply.append(route.get("routeCode"))
                        .append(" ")
                        .append(route.get("routeName"))
                        .append("（")
                        .append(route.get("statusLabel"))
                        .append("）");
            }
            if (routes.size() > limit) {
                reply.append("；等共 ").append(routes.size()).append(" 条");
            }
            reply.append("。");
            return reply.toString();
        }
        if (matchesOperationRealtimeQuery(message)) {
            Map<String, Object> summary = processRouteService.operationSummary();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> operations = (List<Map<String, Object>>) summary.get("operations");
            if (operations == null || operations.isEmpty()) {
                return "当前系统中暂无工序主数据。";
            }
            StringBuilder reply = new StringBuilder("📊 当前共有 ")
                    .append(summary.get("totalCount"))
                    .append(" 道工序，分布在 ")
                    .append(summary.get("routingCount"))
                    .append(" 条工艺路线中：");
            int limit = Math.min(operations.size(), 10);
            for (int i = 0; i < limit; i++) {
                Map<String, Object> operation = operations.get(i);
                if (i > 0) {
                    reply.append("；");
                }
                reply.append(operation.get("operationName"))
                        .append("（")
                        .append(operation.get("routeName") == null ? "—" : operation.get("routeName"))
                        .append("）");
            }
            if (operations.size() > limit) {
                reply.append("；等共 ").append(operations.size()).append(" 道");
            }
            reply.append("。");
            return reply.toString();
        }
        if (matchesPlanRealtimeQuery(message)) {
            Map<String, Object> summary = planService.summary();
            boolean askCompleted = message.contains("已完成")
                    || message.contains("完成的计划")
                    || message.contains("完成的生产计划");
            List<Map<String, Object>> plans;
            if (askCompleted) {
                plans = planService.listBriefByStatuses(List.of("done", "completed"), 20);
            } else if (message.contains("草稿")) {
                plans = planService.listBriefByStatuses(List.of("draft"), 20);
            } else if (message.contains("下发")) {
                plans = planService.listBriefByStatuses(List.of("released"), 20);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> allPlans = (List<Map<String, Object>>) summary.get("plans");
                plans = allPlans == null ? List.of() : allPlans;
            }
            if (askCompleted && plans.isEmpty()) {
                return "📊 当前暂无已完成的生产计划（系统中已完成计划共 "
                        + summary.get("completedCount") + " 条）。";
            }
            if (plans.isEmpty()) {
                return "📊 当前系统中暂无生产计划数据。";
            }
            String title = askCompleted ? "已完成生产计划" : message.contains("草稿") ? "草稿生产计划" : "生产计划";
            StringBuilder reply = new StringBuilder("📊 ").append(title).append("共 ")
                    .append(askCompleted ? summary.get("completedCount") : summary.get("totalCount"))
                    .append(" 条：");
            int limit = Math.min(plans.size(), 10);
            for (int i = 0; i < limit; i++) {
                Map<String, Object> plan = plans.get(i);
                if (i > 0) {
                    reply.append("；");
                }
                reply.append(plan.get("planNo"))
                        .append(" ")
                        .append(plan.get("productName"))
                        .append("（")
                        .append(plan.get("statusLabel"))
                        .append("，")
                        .append(plan.get("planQty"))
                        .append("件）");
            }
            if (plans.size() > limit) {
                reply.append("；等共 ").append(plans.size()).append(" 条");
            }
            reply.append("。");
            return reply.toString();
        }
        if (matchesProductRealtimeQuery(message)) {
            Map<String, Object> summary = productService.summary();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> products = (List<Map<String, Object>>) summary.get("products");
            if (products == null || products.isEmpty()) {
                return "当前系统中暂无产品台账数据。";
            }
            StringBuilder reply = new StringBuilder("📊 当前共有 ")
                    .append(summary.get("totalCount"))
                    .append(" 个产品（启用 ")
                    .append(summary.get("activeCount"))
                    .append("，成品总库存 ")
                    .append(summary.get("totalStockQty"))
                    .append("）：");
            int limit = Math.min(products.size(), 10);
            for (int i = 0; i < limit; i++) {
                Map<String, Object> product = products.get(i);
                if (i > 0) {
                    reply.append("；");
                }
                reply.append(product.get("productCode"))
                        .append(" ")
                        .append(product.get("productName"))
                        .append("（库存 ")
                        .append(product.get("stockQty"))
                        .append(product.get("unit"))
                        .append("，")
                        .append(product.get("statusLabel"))
                        .append("）");
            }
            if (products.size() > limit) {
                reply.append("；等共 ").append(products.size()).append(" 个");
            }
            reply.append("。");
            return reply.toString();
        }
        return "当前为演示模式。我可以帮助你查询工单进度、班组任务、异常处理建议、物料预警、设备状态、工艺路线、工序、生产计划或产品信息。";
    }
}
