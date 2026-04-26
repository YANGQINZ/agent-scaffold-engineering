package com.ai.agent.domain.agent.service;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent注册中心 — 管理AgentDefinition的注册与查找
 */
@Slf4j
@Service
public class AgentRegistry {

    private final ConcurrentHashMap<String, AgentDefinition> registry = new ConcurrentHashMap<>();

    public AgentRegistry() {
        loadAgentDefinitions();
    }

    /**
     * 加载Agent定义（当前仅日志输出，实际YAML加载由Infrastructure层实现）
     */
    private void loadAgentDefinitions() {
        log.info("AgentRegistry初始化 - 等待Infrastructure层加载Agent YAML定义");
    }

    /**
     * 注册Agent定义
     *
     * @param agentDefinition Agent定义
     */
    public void register(AgentDefinition agentDefinition) {
        registry.put(agentDefinition.getAgentId(), agentDefinition);
        log.info("注册Agent定义: agentId={}, name={}, engine={}",
                agentDefinition.getAgentId(), agentDefinition.getName(), agentDefinition.getEngine());
    }

    /**
     * 根据agentId获取Agent定义
     *
     * @param agentId Agent唯一标识
     * @return Agent定义，不存在则返回null
     */
    public AgentDefinition get(String agentId) {
        return registry.get(agentId);
    }

    /**
     * 获取所有已注册的Agent定义
     *
     * @return Agent定义集合
     */
    public Collection<AgentDefinition> getAll() {
        return registry.values();
    }

}
