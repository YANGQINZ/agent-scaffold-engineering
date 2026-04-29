package com.ai.agent.domain.agent.repository;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Agent定义仓储接口
 */
public interface IAgentDefinitionRepository {

    /**
     * 根据agentId查找Agent定义
     *
     * @param agentId Agent唯一标识
     * @return Agent定义
     */
    Optional<AgentDefinition> findByAgentId(String agentId);

    /**
     * 保存Agent定义
     *
     * @param definition Agent定义
     */
    void save(AgentDefinition definition);

    /**
     * 更新Agent定义
     *
     * @param definition Agent定义
     */
    void update(AgentDefinition definition);

    /**
     * 根据agentId删除Agent定义
     *
     * @param agentId Agent唯一标识
     */
    void deleteByAgentId(String agentId);

    /**
     * 查询所有Agent定义
     *
     * @return Agent定义列表
     */
    List<AgentDefinition> findAll();

}
