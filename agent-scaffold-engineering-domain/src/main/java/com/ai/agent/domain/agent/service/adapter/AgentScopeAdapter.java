package com.ai.agent.domain.agent.service.adapter;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.aggregate.AgentscopeAgentDefinition;
import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.agent.repository.ContextStore;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.domain.agent.service.engine.AgentScopeChannel;
import com.ai.agent.domain.agent.service.tool.McpToolProvider;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.domain.chat.model.valobj.ThinkingExtractor;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.exception.AgentException;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.pipeline.Pipelines;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentScope引擎适配器 — 集成 agentscope-java 实现 Pipeline 编排
 * 职责：处理多个Agent之间的动态协作，支持Pipeline编排（Sequential/Fanout）、
 * MCP工具注入、MsgHub广播模式。
 * 通过 ContextStore.assembleMemoryContext() 在构建输入时注入上下文，实现跨引擎状态传递。
 * 核心集成路径：
 * - ReActAgent.builder() 构建每个子 Agent，注入 sysPrompt/model/toolkit
 * - Pipelines.sequential()/fanout() 编排 Agent 列表
 * - AgentBase.call(List<Msg>) 执行推理
 * - MsgHub 广播模式支持多 Agent 协作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentScopeAdapter implements EngineAdapter {

    private final AgentRegistry agentRegistry;
    private final McpToolProvider mcpToolProvider;

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${spring.ai.dashscope.chat.options.model:qwq-plus}")
    private String defaultModelName;

    /** 缓存的 agentscope Model 实例（延迟初始化） */
    private volatile io.agentscope.core.model.Model agentscopeModel;

    /** Pipeline执行后保留的Agent实例，用于AgentScopeChannel暴露 */
    private final Map<String, List<AgentBase>> lastAgentsCache = new ConcurrentHashMap<>();

    /** 缓存最大条目数，防止内存泄漏 */
    private static final int MAX_CACHE_SIZE = 100;

    /**
     * 获取Agent缓存（包级可见，供测试和AgentScopeChannel使用）
     */
    Map<String, List<AgentBase>> getAgentsCache() {
        return lastAgentsCache;
    }

    /**
     * 清理指定Agent的缓存实例，防止内存泄漏
     *
     * @param agentId Agent唯一标识
     */
    public void clearAgentCache(String agentId) {
        lastAgentsCache.remove(agentId);
    }

    /**
     * 清理所有Agent缓存实例
     */
    public void clearAllAgentCache() {
        lastAgentsCache.clear();
    }

    /**
     * 缓存Agent实例，并在超过阈值时淘汰最早的一半条目
     */
    void cacheAgents(String agentId, List<AgentBase> agents) {
        lastAgentsCache.put(agentId, agents);
        // 防止缓存无限增长：超过阈值时淘汰一半
        if (lastAgentsCache.size() > MAX_CACHE_SIZE) {
            int toRemove = lastAgentsCache.size() / 2;
            var iterator = lastAgentsCache.keySet().iterator();
            for (int i = 0; i < toRemove && iterator.hasNext(); i++) {
                iterator.next();
                iterator.remove();
            }
            log.info("Agent缓存淘汰: 保留{}个条目", lastAgentsCache.size());
        }
    }

    @Override
    public EngineType getType() {
        return EngineType.AGENTSCOPE;
    }

    @Override
    public AgentMessage execute(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        AgentscopeAgentDefinition asDef = (AgentscopeAgentDefinition) def;
        log.info("AgentScopeAdapter执行: agentId={}", asDef.getAgentId());

        try {
            // 1. 注入输入到 ContextStore
            ctx.appendHistory(input);

            boolean enableThinking = Boolean.TRUE.equals(input.getMetadataValue("enableThinking"));

            // 2. 构建 ReActAgent 列表（不再注入 ContextSyncHook）
            List<AgentBase> agents = buildAgents(asDef, enableThinking);

            // 3. 缓存 Agent 实例（带淘汰策略，防止内存泄漏）
            cacheAgents(asDef.getAgentId(), agents);

            // 4. 构建输入 Msg（内部调用 assembleMemoryContext 注入记忆）
            Msg inputMsg = toInputMsg(input, asDef, ctx);

            // 5. 根据 Pipeline 类型执行
            String pipelineType = asDef.getAgentscopePipelineType();
            Msg lastMsg;

            if ("fanout".equalsIgnoreCase(pipelineType) && agents.size() > 1) {
                lastMsg = executeFanoutRaw(agents, inputMsg, asDef.getAgentId());
            } else {
                lastMsg = executeSequentialRaw(agents, inputMsg, asDef.getAgentId());
            }

            String outputContent = lastMsg != null ? lastMsg.getTextContent() : "";

            // 提取思考内容
            String thinkingContent = null;
            if (enableThinking && lastMsg != null) {
                ThinkingExtractor.ThinkingResult thinkResult = ThinkingExtractor.extractFromAgentScope(lastMsg);
                thinkingContent = thinkResult.hasThinking() ? thinkResult.thinkingContent() : null;
            }

            // 6. 构建最终响应
            AgentMessage response = toAgentMessage(outputContent, thinkingContent, asDef.getAgentId(), ctx.getSessionId());

            // 7. 最终响应追加历史
            ctx.appendHistory(response);
            return response;

        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("AgentScopeAdapter执行失败: agentId={}, error={}",
                    asDef.getAgentId(), e.getMessage(), e);
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "AgentScope编排执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<StreamEvent> executeStream(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        return Flux.defer(() -> {
            try {
                AgentMessage result = execute(def, input, ctx);
                String sessionId = ctx.getSessionId();

                Flux<StreamEvent> events = Flux.empty();

                // 先发射思考过程
                if (result.getThinkingContent() != null && !result.getThinkingContent().isBlank()) {
                    events = events.concatWith(Flux.just(
                            StreamEvent.thinking(result.getThinkingContent(), sessionId)));
                }

                // 再发射文本内容
                return events.concatWith(Flux.just(
                        StreamEvent.textDelta(result.getContent(), sessionId),
                        StreamEvent.done(false, null, sessionId)
                ));
            } catch (Exception e) {
                return Flux.error(new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                        "AgentScope流式执行失败: " + e.getMessage(), e));
            }
        }).subscribeOn(Schedulers.boundedElastic()); // 避免阻塞WebFlux事件循环
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getTypedChannel(Class<T> channelType) {
        if (channelType == AgentScopeChannel.class) {
            return (T) new AgentScopeChannel(lastAgentsCache);
        }
        throw new AgentException(Constants.ErrorCode.AGENT_MODE_UNSUPPORTED,
                "不支持的通道类型: " + channelType.getSimpleName());
    }

    // ═══════════════════════════════════════════════════════
    // Agent 构建
    // ═══════════════════════════════════════════════════════

    /**
     * 根据 AgentDefinition 构建 ReActAgent 列表
     * 每个 AgentscopeAgentConfig 对应一个 ReActAgent 实例，
     * 通过 AgentRegistry 查找子 Agent 定义获取指令。
     */
    private List<AgentBase> buildAgents(AgentscopeAgentDefinition asDef, boolean enableThinking) {
        List<AgentBase> agents = new ArrayList<>();

        if (asDef.getAgentscopeAgents() != null && !asDef.getAgentscopeAgents().isEmpty()) {
            // 有子 Agent 配置时，逐个构建 ReActAgent
            for (var agentConfig : asDef.getAgentscopeAgents()) {
                AgentDefinition subDef = agentRegistry.get(agentConfig.getAgentId());
                String instruction = subDef != null ? subDef.getInstruction() : asDef.getInstruction();
                String agentName = subDef != null ? subDef.getName() : agentConfig.getAgentId();

                // 构建 Toolkit（注入 MCP 工具）
                Toolkit toolkit = mcpToolProvider.buildAgentScopeToolkit(agentConfig, asDef);

                // 构建 ReActAgent
                ReActAgent agent = ReActAgent.builder()
                        .name(agentName)
                        .sysPrompt(instruction != null ? instruction : "")
                        .model(getOrCreateModel(subDef != null ? subDef : asDef, enableThinking))
                        .toolkit(toolkit)
                        .maxIters(10)
                        .build();

                agents.add(agent);
                log.info("构建ReActAgent: agentId={}, name={}, hasToolkit={}",
                        agentConfig.getAgentId(), agentName, toolkit != null);
            }
        } else {
            // 无子 Agent 配置时，构建单 Agent
            Toolkit toolkit = mcpToolProvider.buildAgentScopeToolkit(null, asDef);

            ReActAgent agent = ReActAgent.builder()
                    .name(asDef.getName() != null ? asDef.getName() : asDef.getAgentId())
                    .sysPrompt(asDef.getInstruction() != null ? asDef.getInstruction() : "")
                    .model(getOrCreateModel(asDef, enableThinking))
                    .toolkit(toolkit)
                    .maxIters(10)
                    .build();

            agents.add(agent);
            log.info("构建单ReActAgent: agentId={}, name={}", asDef.getAgentId(), asDef.getName());
        }

        return agents;
    }

    // ═══════════════════════════════════════════════════════
    // Pipeline 执行
    // ═══════════════════════════════════════════════════════

    /**
     * Sequential Pipeline 执行 — Agent 串行调用，前一个输出作为后一个输入
     */
    private Msg executeSequentialRaw(List<AgentBase> agents, Msg inputMsg, String agentId) {
        log.info("Sequential Pipeline执行: agentId={}, agentCount={}", agentId, agents.size());

        if (agents.size() == 1) {
            return agents.get(0).call(List.of(inputMsg)).block();
        }

        Mono<Msg> pipeline = Pipelines.sequential(agents, inputMsg);
        return pipeline.block();
    }

    /**
     * Fanout Pipeline 执行 — Agent 并行调用，收集所有结果合并
     */
    private Msg executeFanoutRaw(List<AgentBase> agents, Msg inputMsg, String agentId) {
        log.info("Fanout Pipeline执行: agentId={}, agentCount={}", agentId, agents.size());

        Mono<List<Msg>> pipeline = Pipelines.fanout(agents, inputMsg);
        List<Msg> results = pipeline.block();

        if (results == null || results.isEmpty()) {
            return null;
        }

        if (results.size() == 1) {
            return results.get(0);
        }

        // 合并所有 Agent 的输出
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            String content = results.get(i).getTextContent();
            if (content != null && !content.isBlank()) {
                if (!combined.isEmpty()) {
                    combined.append("\n\n---\n\n");
                }
                AgentBase agent = agents.get(i);
                combined.append("## ").append(agent.getName()).append("\n\n");
                combined.append(content);
            }
        }

        // 返回最后一个 Msg 以便提取 ThinkingBlock
        return Msg.builder()
                .role(MsgRole.ASSISTANT)
                .textContent(combined.toString())
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // Model 管理
    // ═══════════════════════════════════════════════════════

    /**
     * 获取或创建 agentscope Model 实例
     * 使用 DashScopeChatModel.builder() 构建，复用 Spring AI 已配置的 API Key。
     * 根据AgentDefinition的modelConfig调整模型参数。
     */
    private Model getOrCreateModel(AgentDefinition def, boolean enableThinking) {
        if (def.getModelConfig() == null || "qwq-plus".equals(def.getModelConfig().getName())) {
            return getOrCreateDefaultModel(enableThinking);
        }
        return buildDashScopeModel(def.getModelConfig().getName(), def.getModelConfig(), enableThinking);
    }

    /**
     * 获取默认模型（延迟初始化，线程安全）
     */
    private Model getOrCreateDefaultModel(boolean enableThinking) {
        if (!enableThinking && agentscopeModel != null) {
            return agentscopeModel;
        }
        if (!enableThinking) {
            synchronized (this) {
                if (agentscopeModel == null) {
                    agentscopeModel = buildDashScopeModel(defaultModelName, null, false);
                    log.info("初始化默认agentscope Model: modelName={}", defaultModelName);
                }
            }
            return agentscopeModel;
        }
        // enableThinking=true 时创建新实例（不缓存，避免污染默认Model）
        return buildDashScopeModel(defaultModelName, null, true);
    }

    /**
     * 构建 DashScopeChatModel 实例
     */
    private Model buildDashScopeModel(String modelName,
                                      com.ai.agent.domain.agent.model.valobj.ModelConfig config,
                                      boolean enableThinking) {
        GenerateOptions.Builder optionsBuilder = GenerateOptions.builder()
                .modelName(modelName);

        if (config != null) {
            if (config.getTemperature() != null) {
                optionsBuilder.temperature(config.getTemperature());
            }
            if (config.getMaxTokens() != null) {
                optionsBuilder.maxTokens(config.getMaxTokens());
            }
        }

        DashScopeChatModel.Builder modelBuilder = DashScopeChatModel.builder()
                .apiKey(dashscopeApiKey)
                .modelName(modelName)
                .defaultOptions(optionsBuilder.build());

        if (enableThinking) {
            modelBuilder.enableThinking(true);
        }

        return modelBuilder.build();
    }

    // ═══════════════════════════════════════════════════════
    // 消息转换
    // ═══════════════════════════════════════════════════════

    /**
     * 将 AgentMessage 转换为 agentscope Msg
     * 注入对话历史作为上下文（从 ContextStore 获取），
     * 确保跨引擎状态传递。
     */
    private Msg toInputMsg(AgentMessage input, AgentDefinition def, ContextStore ctx) {
        String content = input.getContent() != null ? input.getContent() : "";

        // 注入记忆上下文（摘要 + 语义记忆 + 近期消息）
        String memoryContext = ctx.assembleMemoryContext(content);
        if (!memoryContext.isBlank()) {
            content = "记忆上下文:\n" + memoryContext + "\n\n当前输入: " + content;
        }

        return Msg.builder()
                .role(MsgRole.USER)
                .name(input.getSenderId() != null ? input.getSenderId() : "user")
                .textContent(content)
                .build();
    }

    /**
     * 构建输出 AgentMessage
     */
    private AgentMessage toAgentMessage(String output, String thinkingContent, String agentId, String sessionId) {
        return AgentMessage.builder()
                .senderId(agentId)
                .content(output)
                .thinkingContent(thinkingContent)
                .timestamp(System.currentTimeMillis())
                .metadata(Map.of(
                        "role", "assistant",
                        "engine", EngineType.AGENTSCOPE.name(),
                        "sessionId", sessionId != null ? sessionId : ""
                ))
                .build();
    }

}
