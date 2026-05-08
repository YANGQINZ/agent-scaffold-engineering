package com.ai.agent.domain.agent.service.adapter;

import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.common.interface_.ContextStore;
import com.ai.agent.domain.common.valobj.StreamEvent;
import com.ai.agent.types.enums.EngineType;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 引擎适配器抽象基类 — 封装三个适配器共用的流式生命周期管理、消息转换和记忆增强逻辑
 *
 * <p>子类只需实现 {@link EngineAdapter#getType()}、{@link EngineAdapter#execute}、
 * {@link EngineAdapter#executeStream} 即可，通用逻辑由基类提供：</p>
 * <ul>
 *   <li>{@link #wrapFluxLifecycle} — Flux.defer + subscribeOn + 错误恢复统一包装</li>
 *   <li>{@link #toAgentMessage} — 标准消息构建（通过构造器注入 EngineType 区分来源）</li>
 *   <li>{@link #enrichInputWithMemory} — 记忆上下文注入 + 历史追加</li>
 *   <li>{@link #buildDoneFlux} — 完成阶段事件构建（THINKING + DONE）</li>
 * </ul>
 */
@Slf4j
public abstract class AbstractEngineAdapter implements EngineAdapter {

    /** 当前适配器的引擎类型，子类构造时注入 */
    private final EngineType engineType;

    protected AbstractEngineAdapter(EngineType engineType) {
        this.engineType = engineType;
    }

    @Override
    public EngineType getType() {
        return engineType;
    }

    // ═══════════════════════════════════════════════════════════
    // Flux 生命周期包装
    // ═══════════════════════════════════════════════════════════

    /**
     * 包装 Flux 流式生命周期：defer + subscribeOn + 错误处理
     *
     * <p>统一处理三种错误场景：</p>
     * <ul>
     *   <li>IOException / Broken pipe — 客户端断连，静默结束</li>
     *   <li>TimeoutException — 执行超时，发射带 error 标记的 DONE 事件</li>
     * </ul>
     *
     * @param innerSupplier 内部 Flux 逻辑提供者
     * @param agentId       Agent ID（用于日志）
     * @param sessionId     会话 ID（用于超时事件）
     * @return 包装后的 Flux
     */
    protected Flux<StreamEvent> wrapFluxLifecycle(
            Supplier<Flux<StreamEvent>> innerSupplier,
            String agentId,
            String sessionId) {
        return Flux.defer(innerSupplier)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> {
                    if (e instanceof java.io.IOException || (e.getMessage() != null
                            && e.getMessage().contains("Broken pipe"))) {
                        log.warn("{}流式输出: 客户端已断开连接, agentId={}", engineType.name(), agentId);
                    }
                })
                .onErrorResume(java.io.IOException.class, e -> {
                    log.warn("{}流式输出: 管道断裂，静默结束流, agentId={}", engineType.name(), agentId);
                    return Flux.empty();
                })
                .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                    log.warn("{}流式输出: 执行超时, agentId={}", engineType.name(), agentId);
                    return Flux.just(StreamEvent.done(false, Map.of("error", "timeout"), sessionId));
                });
    }

    // ═══════════════════════════════════════════════════════════
    // 消息转换
    // ═══════════════════════════════════════════════════════════

    /**
     * 统一构建 AgentMessage — 子类通过构造器注入的 EngineType 自动区分来源引擎
     *
     * @param output          输出内容
     * @param thinkingContent 思考过程内容（可为 null）
     * @param agentId         Agent ID
     * @param sessionId       会话 ID
     * @return 标准化的 AgentMessage
     */
    protected AgentMessage toAgentMessage(String output, String thinkingContent,
                                           String agentId, String sessionId) {
        return AgentMessage.builder()
                .senderId(agentId)
                .content(output)
                .thinkingContent(thinkingContent)
                .timestamp(System.currentTimeMillis())
                .metadata(Map.of(
                        "role", "assistant",
                        "engine", engineType.name(),
                        "sessionId", sessionId != null ? sessionId : ""
                ))
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // 记忆增强
    // ═══════════════════════════════════════════════════════════

    /**
     * 注入记忆上下文并追加历史记录
     *
     * <p>流程：读取输入内容 → 注入记忆上下文 → 追加历史</p>
     *
     * @param input 用户输入消息
     * @param ctx   上下文存储
     * @return 增强后的输入内容
     */
    protected String enrichInputWithMemory(AgentMessage input, ContextStore ctx) {
        String content = input.getContent() != null ? input.getContent() : "";
        String memoryContext = ctx.assembleMemoryContext(content);
        if (!memoryContext.isBlank()) {
            content = "记忆上下文:\n" + memoryContext + "\n\n当前输入: " + content;
        }
        ctx.appendHistory(input.getSenderId(), input.getContent(), input.getMetadata());
        return content;
    }

    // ═══════════════════════════════════════════════════════════
    // 完成阶段构建
    // ═══════════════════════════════════════════════════════════

    /**
     * 构建完成阶段 Flux：历史追加 + THINKING 事件 + DONE 事件
     *
     * @param finalContent    最终输出内容
     * @param thinkingContent 思考过程内容（可为 null）
     * @param agentId         Agent ID
     * @param sessionId       会话 ID
     * @param ctx             上下文存储
     * @return 包含可选 THINKING 和 DONE 事件的 Flux
     */
    protected Flux<StreamEvent> buildDoneFlux(String finalContent, String thinkingContent,
                                               String agentId, String sessionId, ContextStore ctx) {
        return Flux.defer(() -> {
            AgentMessage response = toAgentMessage(finalContent, thinkingContent, agentId, sessionId);
            ctx.appendHistory(response.getSenderId(), response.getContent(), response.getMetadata());

            Flux<StreamEvent> thinkingFlux = Flux.empty();
            if (thinkingContent != null && !thinkingContent.isBlank()) {
                thinkingFlux = Flux.just(StreamEvent.thinking(thinkingContent, sessionId));
            }
            return thinkingFlux.concatWith(Flux.just(StreamEvent.done(false, null, sessionId)));
        });
    }
}
