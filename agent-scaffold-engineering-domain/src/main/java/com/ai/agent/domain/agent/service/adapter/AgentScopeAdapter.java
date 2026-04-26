package com.ai.agent.domain.agent.service.adapter;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.aggregate.SessionContext;
import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.agent.repository.ContextStore;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.domain.agent.service.engine.AgentScopeChannel;
import com.ai.agent.domain.agent.service.engine.ContextSyncHook;
import com.ai.agent.domain.agent.service.tool.McpToolProvider;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentScope引擎适配器 — 集成 agentscope-java 实现 Pipeline 编排
 *
 * 职责：处理多个Agent之间的动态协作，支持Pipeline编排（Sequential/Fanout）、
 * Hook驱动上下文同步、MCP工具注入、MsgHub广播模式。
 * 通过 ContextSyncHook（实现 agentscope Hook 接口）实现零侵入的 ContextStore 跨引擎同步。
 *
 * 核心集成路径：
 * - ReActAgent.builder() 构建每个子 Agent，注入 sysPrompt/model/toolkit/hook
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

    @Override
    public EngineType getType() {
        return EngineType.AGENTSCOPE;
    }

    @Override
    public AgentMessage execute(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        log.info("AgentScopeAdapter执行: agentId={}", def.getAgentId());

        EngineType previousEngine = EngineType.GRAPH;
        boolean engineSwitched = false;

        try {
            // 1. 注入输入到 ContextStore
            ctx.appendHistory(input);

            // 2. 保存当前引擎并切换
            if (ctx instanceof SessionContext sc) {
                previousEngine = sc.getCurrentEngine();
                sc.switchEngine(EngineType.AGENTSCOPE);
                engineSwitched = true;
            }

            // 3. 获取 agentscope Model
            io.agentscope.core.model.Model model = getOrCreateModel(def);

            // 4. 构建 ReActAgent 列表
            List<AgentBase> agents = buildAgents(def, ctx);

            // 5. 缓存 Agent 实例（供 AgentScopeChannel 使用）
            lastAgentsCache.put(def.getAgentId(), agents);

            // 6. 构建输入 Msg
            Msg inputMsg = toInputMsg(input, def, ctx);

            // 7. 根据 Pipeline 类型执行
            String pipelineType = def.getAgentscopePipelineType();
            String outputContent;

            if ("fanout".equalsIgnoreCase(pipelineType) && agents.size() > 1) {
                outputContent = executeFanout(agents, inputMsg, def);
            } else {
                outputContent = executeSequential(agents, inputMsg, def);
            }

            // 8. 转换输出（ContextSyncHook 已在每个 Agent 的 POST_CALL 中写入历史，
            // 此处仅构建最终响应消息，不再重复 appendHistory）
            AgentMessage response = toAgentMessage(outputContent, def.getAgentId(), ctx.getSessionId());

            return response;

        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("AgentScopeAdapter执行失败: agentId={}, error={}",
                    def.getAgentId(), e.getMessage(), e);
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "AgentScope编排执行失败: " + e.getMessage(), e);
        } finally {
            // 确保切回之前的引擎（即使子节点异常）
            if (engineSwitched && ctx instanceof SessionContext sc) {
                sc.switchEngine(previousEngine);
            }
        }
    }

    @Override
    public Flux<StreamEvent> executeStream(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        return Flux.defer(() -> {
            try {
                AgentMessage result = execute(def, input, ctx);
                return Flux.just(
                        StreamEvent.textDelta(result.getContent(), ctx.getSessionId()),
                        StreamEvent.done(false, null, ctx.getSessionId())
                );
            } catch (Exception e) {
                return Flux.error(new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                        "AgentScope流式执行失败: " + e.getMessage(), e));
            }
        });
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
     *
     * 每个 AgentscopeAgentConfig 对应一个 ReActAgent 实例，
     * 通过 AgentRegistry 查找子 Agent 定义获取指令。
     * ContextSyncHook 作为 Hook 注入到每个 Agent 中实现跨引擎上下文同步。
     */
    private List<AgentBase> buildAgents(AgentDefinition def, ContextStore ctx) {
        List<AgentBase> agents = new ArrayList<>();

        if (def.getAgentscopeAgents() != null && !def.getAgentscopeAgents().isEmpty()) {
            // 有子 Agent 配置时，逐个构建 ReActAgent
            for (var agentConfig : def.getAgentscopeAgents()) {
                AgentDefinition subDef = agentRegistry.get(agentConfig.getAgentId());
                String instruction = subDef != null ? subDef.getInstruction() : def.getInstruction();
                String agentName = subDef != null ? subDef.getName() : agentConfig.getAgentId();

                // 构建 ContextSyncHook（实现 agentscope Hook 接口）
                ContextSyncHook syncHook = new ContextSyncHook(ctx, agentConfig.getAgentId());

                // 构建 Toolkit（注入 MCP 工具）
                Toolkit toolkit = mcpToolProvider.buildAgentScopeToolkit(agentConfig, def);

                // 构建 ReActAgent
                ReActAgent agent = ReActAgent.builder()
                        .name(agentName)
                        .sysPrompt(instruction != null ? instruction : "")
                        .model(getOrCreateModel(subDef != null ? subDef : def))
                        .toolkit(toolkit)
                        .hook(syncHook)
                        .maxIters(10)
                        .build();

                agents.add(agent);
                log.info("构建ReActAgent: agentId={}, name={}, hasToolkit={}",
                        agentConfig.getAgentId(), agentName, toolkit != null);
            }
        } else {
            // 无子 Agent 配置时，构建单 Agent
            ContextSyncHook syncHook = new ContextSyncHook(ctx, def.getAgentId());
            Toolkit toolkit = mcpToolProvider.buildAgentScopeToolkit(null, def);

            ReActAgent agent = ReActAgent.builder()
                    .name(def.getName() != null ? def.getName() : def.getAgentId())
                    .sysPrompt(def.getInstruction() != null ? def.getInstruction() : "")
                    .model(getOrCreateModel(def))
                    .toolkit(toolkit)
                    .hook(syncHook)
                    .maxIters(10)
                    .build();

            agents.add(agent);
            log.info("构建单ReActAgent: agentId={}, name={}", def.getAgentId(), def.getName());
        }

        return agents;
    }

    // ═══════════════════════════════════════════════════════
    // Pipeline 执行
    // ═══════════════════════════════════════════════════════

    /**
     * Sequential Pipeline 执行 — Agent 串行调用，前一个输出作为后一个输入
     *
     * 使用 Pipelines.sequential(agents, msg) 编排，
     * 返回 Mono<Msg>，阻塞获取最终结果。
     */
    private String executeSequential(List<AgentBase> agents, Msg inputMsg, AgentDefinition def) {
        log.info("Sequential Pipeline执行: agentId={}, agentCount={}", def.getAgentId(), agents.size());

        if (agents.size() == 1) {
            // 单 Agent 直接调用
            Msg result = agents.get(0).call(List.of(inputMsg)).block();
            return result != null ? result.getTextContent() : "";
        }

        // 多 Agent 串行 Pipeline
        Mono<Msg> pipeline = Pipelines.sequential(agents, inputMsg);
        Msg result = pipeline.block();
        return result != null ? result.getTextContent() : "";
    }

    /**
     * Fanout Pipeline 执行 — Agent 并行调用，收集所有结果
     *
     * 使用 Pipelines.fanout(agents, msg) 编排，
     * 返回 Mono<List<Msg>>，阻塞获取所有结果后合并。
     */
    private String executeFanout(List<AgentBase> agents, Msg inputMsg, AgentDefinition def) {
        log.info("Fanout Pipeline执行: agentId={}, agentCount={}", def.getAgentId(), agents.size());

        Mono<List<Msg>> pipeline = Pipelines.fanout(agents, inputMsg);
        List<Msg> results = pipeline.block();

        if (results == null || results.isEmpty()) {
            return "";
        }

        // 合并所有 Agent 的输出
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            String content = results.get(i).getTextContent();
            if (content != null && !content.isBlank()) {
                if (combined.length() > 0) {
                    combined.append("\n\n---\n\n");
                }
                AgentBase agent = agents.get(i);
                combined.append("## ").append(agent.getName()).append("\n\n");
                combined.append(content);
            }
        }

        return combined.toString();
    }

    // ═══════════════════════════════════════════════════════
    // Model 管理
    // ═══════════════════════════════════════════════════════

    /**
     * 获取或创建 agentscope Model 实例
     *
     * 使用 DashScopeChatModel.builder() 构建，复用 Spring AI 已配置的 API Key。
     * 根据AgentDefinition的modelConfig调整模型参数。
     */
    private Model getOrCreateModel(AgentDefinition def) {
        // 根据 Agent 的模型配置决定是否使用缓存的默认 Model
        if (def.getModelConfig() == null || "qwq-plus".equals(def.getModelConfig().getName())) {
            return getOrCreateDefaultModel();
        }

        // 非 qwq-plus 模型，每次创建新实例
        return buildDashScopeModel(def.getModelConfig().getName(), def.getModelConfig());
    }

    /**
     * 获取默认模型（延迟初始化，线程安全）
     */
    private Model getOrCreateDefaultModel() {
        if (agentscopeModel == null) {
            synchronized (this) {
                if (agentscopeModel == null) {
                    agentscopeModel = buildDashScopeModel(defaultModelName, null);
                    log.info("初始化默认agentscope Model: modelName={}", defaultModelName);
                }
            }
        }
        return agentscopeModel;
    }

    /**
     * 构建 DashScopeChatModel 实例
     */
    private Model buildDashScopeModel(String modelName,
                                      com.ai.agent.domain.agent.model.valobj.ModelConfig config) {
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

        return DashScopeChatModel.builder()
                .apiKey(dashscopeApiKey)
                .modelName(modelName)
                .defaultOptions(optionsBuilder.build())
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // 消息转换
    // ═══════════════════════════════════════════════════════

    /**
     * 将 AgentMessage 转换为 agentscope Msg
     *
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
    private AgentMessage toAgentMessage(String output, String agentId, String sessionId) {
        return AgentMessage.builder()
                .senderId(agentId)
                .content(output)
                .timestamp(System.currentTimeMillis())
                .metadata(Map.of(
                        "role", "assistant",
                        "engine", EngineType.AGENTSCOPE.name(),
                        "sessionId", sessionId != null ? sessionId : ""
                ))
                .build();
    }

}
