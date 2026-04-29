package com.ai.agent.domain.agent.service;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.common.interface_.ContextStore;
import com.ai.agent.domain.agent.service.adapter.EngineAdapter;
import com.ai.agent.domain.common.valobj.ChatRequest;
import com.ai.agent.domain.common.valobj.ChatResponse;
import com.ai.agent.domain.common.valobj.StreamEvent;
import com.ai.agent.domain.common.interface_.ChatStrategy;
import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.exception.AgentException;
import com.ai.agent.types.exception.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统一调度入口 — 替代 AgentOrchestratorStrategy
 *
 * 实现 ChatStrategy 接口，与 ChatFacade/ModeRouter 对接。
 * 根据 YAML 中 engine 字段路由到对应的 EngineAdapter 执行，
 * 支持 Graph/AgentScope/Hybrid 三种引擎类型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskRuntime implements ChatStrategy {

    private final AgentRegistry agentRegistry;
    private final ContextStoreFactory contextStoreFactory;
    private final List<EngineAdapter> adapterList;

    /** 引擎适配器映射（EngineType → EngineAdapter），使用 volatile 保证可见性 */
    private volatile Map<EngineType, EngineAdapter> adapters;

    /**
     * 获取引擎适配器映射（延迟初始化，线程安全）
     */
    private Map<EngineType, EngineAdapter> getAdapters() {
        if (adapters == null) {
            synchronized (this) {
                if (adapters == null) {
                    adapters = adapterList.stream()
                            .collect(Collectors.toMap(EngineAdapter::getType, a -> a));
                    log.info("TaskRuntime引擎适配器注册完成: {}", adapters.keySet());
                }
            }
        }
        return adapters;
    }

    // ═══════════════════════════════════════════════════════
    // ChatStrategy 接口实现
    // ═══════════════════════════════════════════════════════

    @Override
    public ChatResponse execute(ChatRequest request) {
        AgentMessage input = toAgentMessage(request);
        AgentMessage result = execute(request.getAgentId(), input);
        return toChatResponse(result);
    }

    @Override
    public Flux<StreamEvent> executeStream(ChatRequest request) {
        AgentMessage input = toAgentMessage(request);
        AgentDefinition def = resolveAgentDefinition(request);
        EngineAdapter adapter = routeAdapter(def.getEngine());
        ContextStore ctx = contextStoreFactory.getOrCreate(
                request.getSessionId(),
                request.getUserId(),
                request.getAgentId(),
                request.getEngine() != null ? request.getEngine() : EngineType.GRAPH,
                Boolean.TRUE.equals(request.getRagEnabled()),
                request.getKnowledgeBaseId()
        );
        return adapter.executeStream(def, input, ctx);
    }

    // ═══════════════════════════════════════════════════════
    // 核心执行逻辑
    // ═══════════════════════════════════════════════════════

    /**
     * 内部核心执行逻辑 — 根据 agentId 路由到对应引擎执行
     *
     * @param agentId Agent 定义ID
     * @param input   输入消息
     * @return 执行结果消息
     */
    public AgentMessage execute(String agentId, AgentMessage input) {
        // 1. 从 Registry 加载 AgentDefinition
        AgentDefinition def = agentRegistry.get(agentId);
        if (def == null) {
            throw new AgentException(ErrorCodeEnum.AGENT_NOT_FOUND,
                    "未找到Agent定义: agentId=" + agentId);
        }

        // 2. 路由决策：根据 engine 字段选择适配器
        EngineAdapter adapter = routeAdapter(def.getEngine());

        log.info("TaskRuntime路由: agentId={}, engine={}, adapter={}",
                agentId, def.getEngine(), adapter.getClass().getSimpleName());

        // 3. 创建/复用 ContextStore（跨引擎共享）
        String sessionId = input.getMetadataValue("sessionId") != null
                ? input.getMetadataValue("sessionId").toString()
                : null;
        ContextStore ctx = contextStoreFactory.getOrCreate(sessionId);

        // 4. 委托执行
        return adapter.execute(def, input, ctx);
    }

    /**
     * 获取指定引擎类型的适配器
     *
     * 公开API — 供管理端点、监控接口等外部调用者获取引擎适配器实例。
     * 内部执行链路使用 routeAdapter 私有方法。
     *
     * @param engineType 引擎类型
     * @return 对应的 EngineAdapter
     */
    public EngineAdapter getAdapter(EngineType engineType) {
        return routeAdapter(engineType);
    }

    // ═══════════════════════════════════════════════════════
    // 路由与转换
    // ═══════════════════════════════════════════════════════

    /**
     * 路由到对应引擎适配器
     */
    private EngineAdapter routeAdapter(EngineType engineType) {
        if (engineType == null) {
            engineType = EngineType.GRAPH;
        }
        EngineAdapter adapter = getAdapters().get(engineType);
        if (adapter == null) {
            throw new AgentException(ErrorCodeEnum.AGENT_ENGINE_INVALID,
                    "不支持的引擎类型: " + engineType);
        }
        return adapter;
    }

    /**
     * 解析 Agent 定义 — 优先按 agentId 查找
     */
    private AgentDefinition resolveAgentDefinition(ChatRequest request) {
        if (request.getAgentId() != null && !request.getAgentId().isBlank()) {
            AgentDefinition def = agentRegistry.get(request.getAgentId());
            if (def != null) {
                return def;
            }
            log.warn("未找到指定agentId的Agent定义: agentId={}", request.getAgentId());
        }

        // 按 engine name 查找（兼容旧逻辑）
        if (request.getEngine() != null) {
            AgentDefinition def = agentRegistry.get(request.getEngine().name());
            if (def != null) {
                return def;
            }
        }

        throw new AgentException(ErrorCodeEnum.AGENT_NOT_FOUND,
                "未找到Agent定义: agentId=" + request.getAgentId());
    }

    /**
     * ChatRequest → AgentMessage 转换
     */
    private AgentMessage toAgentMessage(ChatRequest request) {
        return AgentMessage.builder()
                .senderId(request.getUserId())
                .content(request.getQuery())
                .timestamp(System.currentTimeMillis())
                .metadata(Map.of(
                        "role", "user",
                        "sessionId", request.getSessionId() != null ? request.getSessionId() : "",
                        "enableThinking", request.getEnableThinking() != null ? request.getEnableThinking() : false
                ))
                .build();
    }

    /**
     * AgentMessage → ChatResponse 转换
     */
    private ChatResponse toChatResponse(AgentMessage msg) {
        Object sessionId = msg.getMetadataValue("sessionId");
        return ChatResponse.builder()
                .answer(msg.getContent())
                .thinkingContent(msg.getThinkingContent())
                .sessionId(sessionId != null ? sessionId.toString() : "")
                .metadata(Map.of(
                        "engine", msg.getMetadataValue("engine") != null
                                ? msg.getMetadataValue("engine").toString() : "",
                        "senderId", msg.getSenderId() != null ? msg.getSenderId() : ""
                ))
                .build();
    }

}
