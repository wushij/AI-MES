/**
 * Coze AI 集成实现层。
 * <p>
 * {@link com.aimes.service.CozeService} 为对外门面；本包按职责拆分：
 * <ul>
 *   <li>{@link CozeApiClient} — HTTP 调用 Coze API</li>
 *   <li>{@link CozeChatService} — 对话、流式输出、会话历史</li>
 *   <li>{@link CozeChatPromptService} — 提示词路由、实时数据注入、演示回复</li>
 *   <li>{@link CozeSchedulingService} — AI 排产工作流</li>
 *   <li>{@link CozeHealthService} — 连通性健康检查</li>
 * </ul>
 */
package com.aimes.service.coze;
