package com.ai.agent.domain.agent.service;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.repository.IAgentDefinitionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent注册中心 — 管理AgentDefinition的注册与查找
 * <p>
 * 运行时支持动态注册/注销（CRUD操作触发）。
 * 数据库加载由AgentDbLoader（ApplicationRunner order=2）完成，YAML加载由AgentYamlLoader（order=1）完成。
 */
@Slf4j
@Service
public class AgentRegistry {

    private final ConcurrentHashMap<String, AgentDefinition> registry = new ConcurrentHashMap<>();
    private final IAgentDefinitionRepository agentDefinitionRepository;

    public AgentRegistry(IAgentDefinitionRepository agentDefinitionRepository) {
        this.agentDefinitionRepository = agentDefinitionRepository;
    }

    /**
     * 从数据库加载所有Agent定义（由AgentDbLoader调用，在YAML加载之后执行）
     * 数据库定义覆盖相同agentId的YAML定义（数据库优先）
     */
    public void loadFromRepository() {
        log.info("AgentRegistry - 从数据库加载Agent定义（覆盖YAML同agentId定义）...");
        try {
            var definitions = agentDefinitionRepository.findAll();
            for (AgentDefinition definition : definitions) {
                registry.put(definition.getAgentId(), definition);
                log.info("加载数据库Agent定义: agentId={}, name={}, engine={}",
                        definition.getAgentId(), definition.getName(), definition.getEngine());
            }
            log.info("AgentRegistry数据库加载完成: 共加载{}个Agent定义", definitions.size());
        } catch (Exception e) {
            log.error("AgentRegistry数据库加载失败: {}", e.getMessage(), e);
        }
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
     * 注销Agent定义
     *
     * @param agentId Agent唯一标识
     */
    public void unregister(String agentId) {
        AgentDefinition removed = registry.remove(agentId);
        if (removed != null) {
            log.info("注销Agent定义: agentId={}", agentId);
        }
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
