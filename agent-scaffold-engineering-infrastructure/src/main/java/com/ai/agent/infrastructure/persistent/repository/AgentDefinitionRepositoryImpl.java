package com.ai.agent.infrastructure.persistent.repository;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.repository.IAgentDefinitionRepository;
import com.ai.agent.domain.agent.service.AgentRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Agent定义仓储实现 — 委托给AgentRegistry（YAML-based）
 */
@Repository
@RequiredArgsConstructor
public class AgentDefinitionRepositoryImpl implements IAgentDefinitionRepository {

    private final AgentRegistry agentRegistry;

    @Override
    public AgentDefinition findById(String agentId) {
        return agentRegistry.get(agentId);
    }

    @Override
    public void save(AgentDefinition agentDefinition) {
        agentRegistry.register(agentDefinition);
    }

}
