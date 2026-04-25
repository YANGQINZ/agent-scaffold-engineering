package com.ai.agent.domain.agent.service.executor;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.domain.chat.model.valobj.ChatRequest;
import com.ai.agent.domain.chat.model.valobj.ChatResponse;
import com.ai.agent.domain.chat.model.valobj.StreamEvent;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.exception.AgentException;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * Graph执行器基类 — 封装StateGraph公共逻辑：
 * KeyStrategyFactory创建、编译配置、RunnableConfig构建、
 * 子Agent查找/执行、ChatModel调用、流式事件映射
 */
@Slf4j
public abstract class AbstractGraphExecutor {

    protected final ChatModel chatModel;
    protected final ReactExecutor reactExecutor;
    protected final AgentRegistry agentRegistry;

    protected AbstractGraphExecutor(ChatModel chatModel, ReactExecutor reactExecutor, AgentRegistry agentRegistry) {
        this.chatModel = chatModel;
        this.reactExecutor = reactExecutor;
        this.agentRegistry = agentRegistry;
    }

    // ─── 公共状态配置 ───────────────────────────────────────

    /**
     * 创建默认KeyStrategyFactory：output替换、messages追加
     */
    protected KeyStrategyFactory defaultKeyStrategyFactory() {
        return defaultKeyStrategyFactory(Map.of());
    }

    /**
     * 创建KeyStrategyFactory：output替换、messages追加，支持额外策略
     */
    protected KeyStrategyFactory defaultKeyStrategyFactory(Map<String, KeyStrategy> extraStrategies) {
        return () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("output", KeyStrategy.REPLACE);
            strategies.put("messages", new AppendStrategy());
            strategies.putAll(extraStrategies);
            return strategies;
        };
    }

    /**
     * 编译StateGraph为CompiledGraph（带MemorySaver检查点）
     */
    protected CompiledGraph compileGraph(StateGraph graph) {
        try {
            return graph.compile(
                    CompileConfig.builder()
                            .saverConfig(SaverConfig.builder().register(new MemorySaver()).build())
                            .build());
        } catch (GraphStateException e) {
            throw new AgentException(Constants.ErrorCode.AGENT_ORCHESTRATION_FAILED,
                    "StateGraph编译失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建RunnableConfig（threadId取自sessionId或生成UUID）
     */
    protected RunnableConfig buildRunnableConfig(ChatRequest request) {
        return RunnableConfig.builder()
                .threadId(request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString())
                .build();
    }

    // ─── 公共执行方法 ───────────────────────────────────────

    /**
     * 同步执行编译后的图
     */
    protected ChatResponse invokeGraph(CompiledGraph compiled, ChatRequest request, String agentId) {
        RunnableConfig config = buildRunnableConfig(request);
        Optional<OverAllState> result = compiled.invoke(Map.of("output", request.getQuery()), config);
        String answer = result.map(s -> s.value("output", "")).orElse(request.getQuery());
        return ChatResponse.builder()
                .answer(answer)
                .sessionId(request.getSessionId())
                .build();
    }

    /**
     * 流式执行编译后的图 — 从stream()获取节点执行流，
     * 最后一个NodeOutput的state中提取最终答案，避免二次invoke
     */
    protected Flux<StreamEvent> streamGraph(CompiledGraph compiled, ChatRequest request) {
        RunnableConfig config = buildRunnableConfig(request);
        String[] finalAnswer = {request.getQuery()};

        return compiled.stream(Map.of("output", request.getQuery()), config)
                .flatMap(nodeOutput -> {
                    String nodeName = nodeOutput.node();
                    // 从每个节点输出中更新最终答案（最后一个节点的output即为最终结果）
                    String output = nodeOutput.state().value("output", "");
                    if (output != null && !output.isBlank()) {
                        finalAnswer[0] = output;
                    }
                    return Flux.just(
                            StreamEvent.nodeStart(nodeName, request.getSessionId()),
                            StreamEvent.nodeEnd(nodeName, request.getSessionId())
                    );
                })
                .concatWith(Flux.defer(() -> Flux.just(
                        StreamEvent.textDelta(finalAnswer[0], request.getSessionId()),
                        StreamEvent.done(false, null, request.getSessionId())
                )));
    }

    // ─── 子Agent查找与执行 ──────────────────────────────────

    /**
     * 查找子Agent定义
     */
    protected AgentDefinition findSubAgentDefinition(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        return agentRegistry.get(agentId);
    }

    /**
     * 执行子Agent — 根据AgentMode分发到ReactExecutor或ChatModel
     */
    protected ChatResponse executeSubAgent(AgentDefinition subAgentDef, String input, ChatRequest originalRequest) {
        ChatRequest subRequest = ChatRequest.builder()
                .userId(originalRequest.getUserId())
                .sessionId(originalRequest.getSessionId())
                .query(input)
                .mode(originalRequest.getMode())
                .agentMode(subAgentDef.getMode())
                .agentId(subAgentDef.getAgentId())
                .build();

        return switch (subAgentDef.getMode()) {
            case REACT -> reactExecutor.execute(subAgentDef, subRequest);
            default -> {
                String answer = executeWithChatModel(subAgentDef, input);
                yield ChatResponse.builder()
                        .answer(answer)
                        .sessionId(originalRequest.getSessionId())
                        .build();
            }
        };
    }

    /**
     * 使用ChatModel直接执行
     */
    protected String executeWithChatModel(AgentDefinition agentDef, String input) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(agentDef.getInstruction()),
                new UserMessage(input)
        ));
        return chatModel.call(prompt).getResult().getOutput().getText();
    }
}
