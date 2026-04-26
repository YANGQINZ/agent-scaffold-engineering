package com.ai.agent.domain.agent.service.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hybrid 双通道 — 同时暴露 GraphChannel 和 AgentScopeChannel
 *
 * 开发者可通过此通道精确控制任一引擎的原生能力，
 * 或运行时动态切换子节点的引擎类型。
 */
@Slf4j
@RequiredArgsConstructor
public class HybridChannel {

    private final GraphChannel graphChannel;
    private final AgentScopeChannel agentscopeChannel;

    /**
     * 获取 GraphChannel
     *
     * @return Graph 引擎特化通道
     */
    public GraphChannel graphChannel() {
        return graphChannel;
    }

    /**
     * 获取 AgentScopeChannel
     *
     * @return AgentScope 引擎特化通道
     */
    public AgentScopeChannel agentscopeChannel() {
        return agentscopeChannel;
    }

    /**
     * 运行时动态切换子节点的引擎类型
     *
     * @param nodeId    节点ID
     * @param newEngine 新的引擎类型
     */
    // TODO [#004] 更新 AgentDefinition.subEngines 映射，下次执行时生效
    public void switchSubEngine(String nodeId, com.ai.agent.types.enums.EngineType newEngine) {
        log.info("子节点引擎切换请求: nodeId={}, newEngine={}", nodeId, newEngine);
        // TODO [#004]: 更新 AgentDefinition.subEngines 映射，下次执行时生效
    }

}
