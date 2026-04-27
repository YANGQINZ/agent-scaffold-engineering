package com.ai.agent.domain.agent.service.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hybrid 双通道 — 同时暴露 GraphChannel 和 AgentScopeChannel
 *
 * 开发者可通过此通道精确控制任一引擎的原生能力。
 */
@Slf4j
@RequiredArgsConstructor
public class HybridChannel {

    private final GraphChannel graphChannel;
    private final AgentScopeChannel agentscopeChannel;

    /**
     * 获取 GraphChannel
     */
    public GraphChannel graphChannel() {
        return graphChannel;
    }

    /**
     * 获取 AgentScopeChannel
     */
    public AgentScopeChannel agentscopeChannel() {
        return agentscopeChannel;
    }

}
