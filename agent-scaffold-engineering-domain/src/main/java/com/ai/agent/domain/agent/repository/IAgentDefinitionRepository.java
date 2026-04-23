package com.ai.agent.domain.agent.repository;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;

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
    AgentDefinition findById(String agentId);

    /**
     * 保存Agent定义
     *
     * @param agentDefinition Agent定义
     */
    void save(AgentDefinition agentDefinition);

}
