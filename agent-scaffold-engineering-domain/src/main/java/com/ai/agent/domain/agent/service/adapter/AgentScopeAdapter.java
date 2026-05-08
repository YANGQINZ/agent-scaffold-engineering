package com.ai.agent.domain.agent.service.adapter;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.aggregate.AgentscopeAgentDefinition;
import com.ai.agent.domain.agent.model.entity.AgentscopeAgentConfig;
import com.ai.agent.domain.agent.model.entity.McpServerConfig;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.common.interface_.ContextStore;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.domain.agent.service.tool.McpToolProvider;
import com.ai.agent.domain.common.valobj.StreamEvent;
import com.ai.agent.domain.common.valobj.ThinkingExtractor;
import com.ai.agent.domain.knowledge.service.rag.NodeRagService;
import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.exception.AgentException;
import com.ai.agent.types.exception.enums.ErrorCodeEnum;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * AgentScope引擎适配器 — 集成 agentscope-java 实现 Pipeline 编排
 * 职责：处理多个Agent之间的动态协作，支持Pipeline编排（Sequential）、
 * MCP工具注入、MsgHub广播模式。
 * 流式执行：最后一个 Agent 使用 Spring AI chatModel.stream() 实现 token 级流式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentScopeAdapter implements EngineAdapter {

    private final AgentRegistry agentRegistry;
    private final McpToolProvider mcpToolProvider;
    private final NodeRagService nodeRagService;
    private final ChatModel chatModel;  // Spring AI ChatModel（用于流式输出）

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${spring.ai.dashscope.chat.options.model:qwq-plus}")
    private String defaultModelName;

    /** 缓存的 agentscope Model 实例（延迟初始化） */
    private volatile io.agentscope.core.model.Model agentscopeModel;

    @Override
    public EngineType getType() {
        return EngineType.AGENTSCOPE;
    }

    @Override
    public AgentMessage execute(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        AgentscopeAgentDefinition asDef = (AgentscopeAgentDefinition) def;
        log.info("AgentScopeAdapter执行: agentId={}", asDef.getAgentId());

        try {
            // 1. 构建输入 Msg（内部调用 assembleMemoryContext 注入记忆）
            Msg inputMsg = toInputMsg(input, ctx);

            boolean enableThinking = Boolean.TRUE.equals(input.getMetadataValue("enableThinking"));

            // 节点级 RAG 增强
            WorkflowNode ragNode = WorkflowNode.builder()
                    .id(asDef.getAgentId())
                    .ragEnabled((Boolean) input.getMetadataValue("ragEnabled"))
                    .knowledgeBaseId((String) input.getMetadataValue("knowledgeBaseId"))
                    .build();
            String enhancedContent = nodeRagService.enhancePrompt(inputMsg.getTextContent(), ragNode);
            if (!enhancedContent.equals(inputMsg.getTextContent())) {
                inputMsg = Msg.builder()
                        .role(inputMsg.getRole())
                        .name(inputMsg.getName())
                        .textContent(enhancedContent)
                        .build();
            }

            // 2. 构建 ReActAgent 列表
            List<AgentBase> agents = buildAgents(asDef, enableThinking);

            // 3. 注入输入到 ContextStore
            ctx.appendHistory(input.getSenderId(), input.getContent(), input.getMetadata());

            // 4. Sequential Pipeline 执行
            Msg lastMsg = executeSequentialRaw(agents, inputMsg, asDef.getAgentId());

            String outputContent = lastMsg != null ? lastMsg.getTextContent() : "";

            // 提取思考内容
            String thinkingContent = null;
            if (enableThinking && lastMsg != null) {
                ThinkingExtractor.ThinkingResult thinkResult = ThinkingExtractor.extractFromAgentScope(lastMsg);
                thinkingContent = thinkResult.hasThinking() ? thinkResult.thinkingContent() : null;
            }

            // 5. 构建最终响应
            AgentMessage response = toAgentMessage(outputContent, thinkingContent, asDef.getAgentId(), ctx.getSessionId());

            // 6. 最终响应追加历史
            ctx.appendHistory(response.getSenderId(), response.getContent(), response.getMetadata());
            return response;

        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("AgentScopeAdapter执行失败: agentId={}, error={}",
                    asDef.getAgentId(), e.getMessage(), e);
            throw new AgentException(ErrorCodeEnum.AGENT_FAILED,
                    "AgentScope编排执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<StreamEvent> executeStream(AgentDefinition def, AgentMessage input, ContextStore ctx) {
        AgentscopeAgentDefinition asDef = (AgentscopeAgentDefinition) def;
        return Flux.defer(() -> {
            log.info("AgentScopeAdapter流式执行: agentId={}", asDef.getAgentId());

            try {
                // 入口处注入记忆上下文 + RAG 增强
                String content = input.getContent() != null ? input.getContent() : "";
                String memoryContext = ctx.assembleMemoryContext(content);
                if (!memoryContext.isBlank()) {
                    content = "记忆上下文:\n" + memoryContext + "\n\n当前输入: " + content;
                }

                // RAG 增强
                WorkflowNode ragNode = WorkflowNode.builder()
                        .id(asDef.getAgentId())
                        .ragEnabled((Boolean) input.getMetadataValue("ragEnabled"))
                        .knowledgeBaseId((String) input.getMetadataValue("knowledgeBaseId"))
                        .build();
                content = nodeRagService.enhancePrompt(content, ragNode);

                // 输入历史追加
                ctx.appendHistory(input.getSenderId(), input.getContent(), input.getMetadata());

                boolean enableThinking = Boolean.TRUE.equals(input.getMetadataValue("enableThinking"));

                // Sequential Pipeline：前 N-1 个同步 + 最后一个流式（单个 agent 时中间阶段为空，直接流式）
                return executeSequentialStream(asDef, content, ctx, enableThinking);
            } catch (Exception e) {
                log.error("AgentScopeAdapter流式执行失败: {}", e.getMessage(), e);
                return Flux.error(new AgentException(ErrorCodeEnum.AGENT_FAILED,
                        "AgentScope流式执行失败: " + e.getMessage(), e));
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnError(e -> {
            if (e instanceof java.io.IOException || (e.getMessage() != null
                    && e.getMessage().contains("Broken pipe"))) {
                log.warn("AgentScopeAdapter流式输出: 客户端已断开连接, agentId={}", def.getAgentId());
            }
        })
        .onErrorResume(java.io.IOException.class, e -> {
            log.warn("AgentScopeAdapter流式输出: 管道断裂，静默结束流, agentId={}", def.getAgentId());
            return Flux.empty();
        })
        .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
            log.warn("AgentScopeAdapter流式输出: 执行超时, agentId={}", def.getAgentId());
            return Flux.just(StreamEvent.done(false, Map.of("error", "timeout"), ctx.getSessionId()));
        });
    }

    // ═══════════════════════════════════════════════════════
    // 流式执行策略
    // ═══════════════════════════════════════════════════════

    /**
     * Sequential 流式：前 N-1 个通过 Pipelines.sequential() 同步执行 + 最后一个 Agent 流式
     */
    private Flux<StreamEvent> executeSequentialStream(
            AgentscopeAgentDefinition asDef, String content, ContextStore ctx,
            boolean enableThinking) {

        String sessionId = ctx.getSessionId();
        List<AgentscopeAgentConfig> configs = asDef.getAgentscopeAgents();
        int lastIdx = configs.size() - 1;

        String[] textAccumulator = {""};
        String[] thinkingAccumulator = {null};
        String[] finalContent = {content}; // 中间输出累积器

        // Phase 1: 用 Pipelines.sequential() 执行前 N-1 个 Agent
        Flux<StreamEvent> intermediateFlux = Flux.defer(() -> {
            // 构建前 N-1 个 Agent
            List<AgentBase> intermediateAgents = new ArrayList<>();
            for (int i = 0; i < lastIdx; i++) {
                AgentscopeAgentConfig config = configs.get(i);
                AgentDefinition subDef = config.getAgentId() != null && !config.getAgentId().isBlank()
                        ? agentRegistry.get(config.getAgentId()) : null;
                String instruction = resolveAgentInstruction(asDef, config);
                String agentName = resolveAgentName(asDef, config);
                Toolkit toolkit = mcpToolProvider.buildAgentScopeToolkit(config, asDef);
                Model model = getOrCreateModel(subDef != null ? subDef : asDef, enableThinking);

                intermediateAgents.add(ReActAgent.builder()
                        .name(agentName)
                        .sysPrompt(instruction != null ? instruction : "")
                        .model(model)
                        .toolkit(toolkit)
                        .maxIters(10)
                        .build());
            }

            // 发 nodeStart 进度事件
            Flux<StreamEvent> startEvents = Flux.fromStream(intermediateAgents.stream()
                    .map(agent -> StreamEvent.nodeStart(agent.getName(), sessionId)));

            // 构建输入 Msg
            Msg inputMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(content)
                    .build();

            // 使用 Pipelines.sequential() 编排执行
            String pipelineOutput;
            try {
                Mono<Msg> pipeline = Pipelines.sequential(intermediateAgents, inputMsg);
                Msg result = pipeline.block();
                pipelineOutput = (result != null && result.getTextContent() != null)
                        ? result.getTextContent() : content;
            } catch (Exception e) {
                log.warn("AgentScope Sequential Pipeline执行失败: {}", e.getMessage());
                pipelineOutput = content;
            }

            // 发 nodeEnd 进度事件
            Flux<StreamEvent> endEvents = Flux.fromStream(intermediateAgents.stream()
                    .map(agent -> StreamEvent.nodeEnd(agent.getName(), sessionId)));

            // 传递中间输出到 Phase 2
            finalContent[0] = pipelineOutput;

            return Flux.concat(startEvents, endEvents);
        });

        // Phase 2: 最后一个 Agent 流式输出
        AgentscopeAgentConfig lastConfig = configs.get(lastIdx);
        String lastSystemPrompt = resolveAgentInstruction(asDef, lastConfig);
        List<McpServerConfig> lastMcpServers = resolveAgentMcpServers(lastConfig, asDef);

        Flux<StreamEvent> leafFlux = Flux.defer(() ->
                streamAgentTokens(lastSystemPrompt, finalContent[0], enableThinking,
                        lastMcpServers, sessionId, textAccumulator, thinkingAccumulator)
        );

        Flux<StreamEvent> doneFlux = buildDoneFlux(asDef, ctx, textAccumulator, thinkingAccumulator);

        return Flux.concat(intermediateFlux, leafFlux, doneFlux);
    }

    // ═══════════════════════════════════════════════════════
    // 流式输出核心方法
    // ═══════════════════════════════════════════════════════

    /**
     * Agent token 级流式输出 — 使用 Spring AI chatModel.stream()
     * 支持 MCP 工具调用，流式失败自动降级为同步
     */
    private Flux<StreamEvent> streamAgentTokens(
            String systemPrompt, String input, boolean enableThinking,
            List<McpServerConfig> mcpServers, String sessionId,
            String[] textAccumulator, String[] thinkingAccumulator) {

        Prompt prompt = buildSpringAiPrompt(systemPrompt, input, enableThinking);

        Flux<org.springframework.ai.chat.model.ChatResponse> streamFlux;
        try {
            if (mcpServers != null && !mcpServers.isEmpty()) {
                List<ToolCallback> tools = mcpToolProvider.buildGraphTools(mcpServers);
                streamFlux = ChatClient.create(chatModel)
                        .prompt(prompt).toolCallbacks(tools).stream().chatResponse();
            } else {
                streamFlux = chatModel.stream(prompt);
            }
        } catch (Exception e) {
            log.warn("AgentScope流式初始化失败，降级为同步调用: {}", e.getMessage());
            return syncAgentFallback(prompt, sessionId, textAccumulator, thinkingAccumulator);
        }

        return streamFlux
                .flatMap(cr -> extractStreamingToken(cr, sessionId, textAccumulator, thinkingAccumulator))
                .onErrorResume(e -> {
                    log.warn("AgentScope流式输出失败，降级为同步调用: {}", e.getMessage());
                    return syncAgentFallback(prompt, sessionId, textAccumulator, thinkingAccumulator);
                });
    }

    /**
     * 同步降级 — 模型不支持流式或流式失败时调用
     */
    private Flux<StreamEvent> syncAgentFallback(
            Prompt prompt, String sessionId,
            String[] textAccumulator, String[] thinkingAccumulator) {
        try {
            org.springframework.ai.chat.model.ChatResponse syncResponse = chatModel.call(prompt);
            ThinkingExtractor.ThinkingResult result = ThinkingExtractor.extractFromSpringAi(syncResponse);
            textAccumulator[0] = result.textContent();
            if (result.hasThinking()) {
                thinkingAccumulator[0] = result.thinkingContent();
            }
            Flux<StreamEvent> events = Flux.empty();
            if (result.hasThinking()) {
                events = events.concatWith(Flux.just(
                        StreamEvent.thinking(result.thinkingContent(), sessionId)));
            }
            return events.concatWith(Flux.just(
                    StreamEvent.textDelta(result.textContent(), sessionId)));
        } catch (Exception fallbackEx) {
            log.error("AgentScope同步降级也失败: {}", fallbackEx.getMessage(), fallbackEx);
            return Flux.error(fallbackEx);
        }
    }

    /**
     * 从流式 ChatResponse 中提取 token 级事件
     */
    private Flux<StreamEvent> extractStreamingToken(
            org.springframework.ai.chat.model.ChatResponse cr, String sessionId,
            String[] textAccumulator, String[] thinkingAccumulator) {

        if (cr.getResult() == null || cr.getResult().getOutput() == null) {
            return Flux.empty();
        }

        ThinkingExtractor.ThinkingResult result = ThinkingExtractor.extractFromSpringAi(cr);
        Flux<StreamEvent> events = Flux.empty();

        if (result.hasThinking()) {
            thinkingAccumulator[0] = (thinkingAccumulator[0] == null)
                    ? result.thinkingContent() : thinkingAccumulator[0] + result.thinkingContent();
            events = events.concatWith(Flux.just(StreamEvent.thinking(result.thinkingContent(), sessionId)));
        }
        if (!result.textContent().isEmpty()) {
            textAccumulator[0] += result.textContent();
            events = events.concatWith(Flux.just(StreamEvent.textDelta(result.textContent(), sessionId)));
        }
        return events;
    }

    /**
     * 构建完成阶段 Flux — 历史追加 + thinking + done
     */
    private Flux<StreamEvent> buildDoneFlux(
            AgentscopeAgentDefinition asDef, ContextStore ctx,
            String[] textAccumulator, String[] thinkingAccumulator) {

        return Flux.defer(() -> {
            String sessionId = ctx.getSessionId();
            AgentMessage response = toAgentMessage(
                    textAccumulator[0].isEmpty() ? "（无输出）" : textAccumulator[0],
                    thinkingAccumulator[0], asDef.getAgentId(), sessionId);
            ctx.appendHistory(response.getSenderId(), response.getContent(), response.getMetadata());

            Flux<StreamEvent> thinkingFlux = Flux.empty();
            if (thinkingAccumulator[0] != null && !thinkingAccumulator[0].isBlank()) {
                thinkingFlux = Flux.just(StreamEvent.thinking(thinkingAccumulator[0], sessionId));
            }
            return thinkingFlux.concatWith(Flux.just(StreamEvent.done(false, null, sessionId)));
        });
    }

    // ═══════════════════════════════════════════════════════
    // Prompt 构建
    // ═══════════════════════════════════════════════════════

    /**
     * 构建 Spring AI Prompt
     */
    private Prompt buildSpringAiPrompt(String systemPrompt, String input, boolean enableThinking) {
        if (enableThinking) {
            DashScopeChatOptions options = DashScopeChatOptions.builder()
                    .enableThinking(true).build();
            return new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(input)
            ), options);
        }
        return new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(input)
        ));
    }

    // ═══════════════════════════════════════════════════════
    // Agent 构建
    // ═══════════════════════════════════════════════════════

    /**
     * 根据 AgentDefinition 构建 ReActAgent 列表
     */
    private List<AgentBase> buildAgents(AgentscopeAgentDefinition asDef, boolean enableThinking) {
        List<AgentBase> agents = new ArrayList<>();

        if (asDef.getAgentscopeAgents() != null && !asDef.getAgentscopeAgents().isEmpty()) {
            for (var agentConfig : asDef.getAgentscopeAgents()) {
                AgentDefinition subDef = agentConfig.getAgentId() != null && !agentConfig.getAgentId().isBlank()
                        ? agentRegistry.get(agentConfig.getAgentId()) : null;
                String instruction = resolveAgentInstruction(asDef, agentConfig);
                String agentName = subDef != null ? subDef.getName() : agentConfig.getAgentId();

                Toolkit toolkit = mcpToolProvider.buildAgentScopeToolkit(agentConfig, asDef);

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
    // Pipeline 执行（同步，供 execute() 使用）
    // ═══════════════════════════════════════════════════════

    private Msg executeSequentialRaw(List<AgentBase> agents, Msg inputMsg, String agentId) {
        log.info("Sequential Pipeline执行: agentId={}, agentCount={}", agentId, agents.size());

        if (agents.size() == 1) {
            return agents.get(0).call(List.of(inputMsg)).block();
        }

        Mono<Msg> pipeline = Pipelines.sequential(agents, inputMsg);
        return pipeline.block();
    }

    // ═══════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════

    /**
     * 解析子 Agent 名称
     */
    private String resolveAgentName(AgentscopeAgentDefinition asDef, AgentscopeAgentConfig config) {
        if (config.getAgentId() != null && !config.getAgentId().isBlank()) {
            AgentDefinition subDef = agentRegistry.get(config.getAgentId());
            if (subDef != null && subDef.getName() != null) {
                return subDef.getName();
            }
            return config.getAgentId();
        }
        return asDef.getName() != null ? asDef.getName() : asDef.getAgentId();
    }

    /**
     * 解析子 Agent 指令
     */
    private String resolveAgentInstruction(AgentscopeAgentDefinition asDef, AgentscopeAgentConfig config) {
        // 优先使用内联 instruction（画布 testRun 等场景直接传入）
        if (config.getInstruction() != null && !config.getInstruction().isBlank()) {
            return config.getInstruction();
        }
        if (config.getAgentId() != null && !config.getAgentId().isBlank()) {
            AgentDefinition subDef = agentRegistry.get(config.getAgentId());
            if (subDef != null && subDef.getInstruction() != null) {
                return subDef.getInstruction();
            }
        }
        return asDef.getInstruction() != null ? asDef.getInstruction() : "你是一个有用的助手。";
    }

    /**
     * 解析子 Agent 的 MCP 服务器配置
     */
    private List<McpServerConfig> resolveAgentMcpServers(AgentscopeAgentConfig config, AgentscopeAgentDefinition asDef) {
        if (config.getMcpServers() != null && !config.getMcpServers().isEmpty()) {
            return config.getMcpServers();
        }
        return asDef.getMcpServers();
    }

    // ═══════════════════════════════════════════════════════
    // Model 管理
    // ═══════════════════════════════════════════════════════

    private Model getOrCreateModel(AgentDefinition def, boolean enableThinking) {
        if (def.getModelConfig() == null || "qwq-plus".equals(def.getModelConfig().getName())) {
            return getOrCreateDefaultModel(enableThinking);
        }
        return buildDashScopeModel(def.getModelConfig().getName(), def.getModelConfig(), enableThinking);
    }

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
        return buildDashScopeModel(defaultModelName, null, true);
    }

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

    private Msg toInputMsg(AgentMessage input, ContextStore ctx) {
        String content = input.getContent() != null ? input.getContent() : "";

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
