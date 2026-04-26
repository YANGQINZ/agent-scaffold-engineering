package com.ai.agent.domain.agent.service.engine;

import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.agent.repository.ContextStore;
import com.ai.agent.types.enums.EngineType;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.HookEventType;
import io.agentscope.core.message.Msg;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * AgentScope 上下文同步 Hook — 实现 agentscope Hook 接口
 *
 * 在 AgentScope Agent 的生命周期事件中自动同步 ContextStore：
 * - PRE_CALL：从 ContextStore 读取对话历史，注入到输入消息
 * - POST_CALL：将 Agent 输出转换为 AgentMessage 写入 ContextStore
 * - ERROR：记录异常日志
 *
 * 注册方式：ReActAgent.builder().hook(contextSyncHook).build()
 */
@Slf4j
public class ContextSyncHook implements Hook {

    private final ContextStore contextStore;
    private final String agentId;
    private final int priority;

    /**
     * 构造上下文同步 Hook
     *
     * @param contextStore 跨引擎共享的上下文存储
     * @param agentId      Agent ID（用于消息的 senderId）
     */
    public ContextSyncHook(ContextStore contextStore, String agentId) {
        this(contextStore, agentId, 0);
    }

    /**
     * 构造上下文同步 Hook（指定优先级）
     *
     * @param contextStore 跨引擎共享的上下文存储
     * @param agentId      Agent ID（用于消息的 senderId）
     * @param priority     Hook 优先级（数值越小越先执行）
     */
    public ContextSyncHook(ContextStore contextStore, String agentId, int priority) {
        this.contextStore = contextStore;
        this.agentId = agentId;
        this.priority = priority;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event == null) {
            return Mono.justOrEmpty(event);
        }

        HookEventType type = event.getType();

        if (type == HookEventType.PRE_CALL) {
            return handlePreCall(event);
        } else if (type == HookEventType.POST_CALL) {
            return handlePostCall(event);
        } else if (type == HookEventType.ERROR) {
            log.warn("ContextSyncHook捕获Agent错误: agentId={}, error={}", agentId, event);
        }

        return Mono.justOrEmpty(event);
    }

    /**
     * 处理 PRE_CALL 事件 — 将 ContextStore 中的对话历史注入到 Memory
     *
     * AgentScope 的 Hook 机制会在 Agent 调用前触发此事件，
     * 通过 event.getMemory() 获取 Agent 的 Memory 对象，
     * 将 ContextStore 中的跨引擎历史注入到当前 Agent 的 Memory 中。
     */
    private <T extends HookEvent> Mono<T> handlePreCall(T event) {
        String historyText = contextStore.buildHistoryText();
        if (historyText != null && !historyText.isBlank()) {
            log.debug("ContextSyncHook PRE_CALL注入对话历史: agentId={}, historyLength={}",
                    agentId, historyText.length());

            var memory = event.getMemory();
            if (memory != null) {
                try {
                    // 将 ContextStore 历史作为上下文消息注入到 Agent Memory
                    memory.addMessage(Msg.builder()
                            .role(io.agentscope.core.message.MsgRole.SYSTEM)
                            .textContent("跨引擎对话历史:\n" + historyText)
                            .build());
                    log.debug("ContextSyncHook已注入历史到Memory: agentId={}", agentId);
                } catch (Exception e) {
                    log.warn("ContextSyncHook Memory注入降级: agentId={}, error={}", agentId, e.getMessage());
                }
            }
        }
        return Mono.justOrEmpty(event);
    }

    /**
     * 处理 POST_CALL 事件 — 将 Agent 输出写入 ContextStore
     *
     * AgentScope 的 Hook 机制会在 Agent 调用完成后触发此事件，
     * 通过 event.getMemory() 获取最终输出并写入跨引擎共享的 ContextStore。
     */
    private <T extends HookEvent> Mono<T> handlePostCall(T event) {
        var memory = event.getMemory();
        if (memory == null) {
            return Mono.justOrEmpty(event);
        }

        try {
            // 从 Memory 获取最后一条助手消息
            var messages = memory.getMessages();
            if (messages != null && !messages.isEmpty()) {
                // 找到最后一条 ASSISTANT 消息
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Msg msg = messages.get(i);
                    if (msg.getRole() == io.agentscope.core.message.MsgRole.ASSISTANT) {
                        String content = msg.getTextContent();
                        if (content != null && !content.isBlank()) {
                            AgentMessage responseMsg = AgentMessage.builder()
                                    .senderId(agentId)
                                    .content(content)
                                    .timestamp(System.currentTimeMillis())
                                    .metadata(java.util.Map.of(
                                            "role", "assistant",
                                            "engine", EngineType.AGENTSCOPE.name(),
                                            "sessionId", contextStore.getSessionId() != null ? contextStore.getSessionId() : ""
                                    ))
                                    .build();
                            contextStore.appendHistory(responseMsg);
                            log.debug("ContextSyncHook POST_CALL写入ContextStore: agentId={}", agentId);
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("ContextSyncHook POST_CALL降级: agentId={}, error={}", agentId, e.getMessage());
        }

        return Mono.justOrEmpty(event);
    }

    // ═══════════════════════════════════════════════════════
    // 兼容旧调用方式的便捷方法（供 HybridEngineAdapter 等外部使用）
    // ═══════════════════════════════════════════════════════

    /**
     * 便捷方法：处理 Agent 调用前事件 — 注入对话历史
     *
     * @param currentContent 当前输入内容
     * @return 增强后的输入内容（拼接对话历史）
     */
    public String onPreCall(String currentContent) {
        String historyText = contextStore.buildHistoryText();
        if (historyText != null && !historyText.isBlank()) {
            log.debug("ContextSyncHook注入对话历史: agentId={}, historyLength={}",
                    agentId, historyText.length());
            return "对话历史:\n" + historyText + "\n\n当前输入: " + currentContent;
        }
        return currentContent;
    }

    /**
     * 便捷方法：处理 Agent 调用后事件 — 写入 ContextStore
     *
     * @param outputContent Agent 输出内容
     * @param sessionId     会话ID
     */
    public void onPostCall(String outputContent, String sessionId) {
        if (outputContent == null || outputContent.isBlank()) {
            return;
        }

        AgentMessage responseMsg = AgentMessage.builder()
                .senderId(agentId)
                .content(outputContent)
                .timestamp(System.currentTimeMillis())
                .metadata(java.util.Map.of(
                        "role", "assistant",
                        "engine", EngineType.AGENTSCOPE.name(),
                        "sessionId", sessionId != null ? sessionId : ""
                ))
                .build();

        contextStore.appendHistory(responseMsg);
        log.debug("ContextSyncHook写入ContextStore: agentId={}, sessionId={}", agentId, sessionId);
    }

}
