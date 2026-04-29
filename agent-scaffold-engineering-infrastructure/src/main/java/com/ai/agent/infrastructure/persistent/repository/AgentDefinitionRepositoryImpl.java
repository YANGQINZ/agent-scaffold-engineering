package com.ai.agent.infrastructure.persistent.repository;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.repository.IAgentDefinitionRepository;
import com.ai.agent.domain.agent.service.AgentRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Agent定义仓储实现 — 委托给AgentRegistry（YAML-based）
 */
@Repository
@RequiredArgsConstructor
public class AgentDefinitionRepositoryImpl implements IAgentDefinitionRepository {

    private final AgentRegistry agentRegistry;

    @Override
    public Optional<AgentDefinition> findByAgentId(String agentId) {
        return Optional.ofNullable(agentRegistry.get(agentId));
    }

    @Override
    public void save(AgentDefinition definition) {
        agentRegistry.register(definition);
    }

    @Override
    public void update(AgentDefinition definition) {
        agentRegistry.register(definition);
    }

    @Override
    public void deleteByAgentId(String agentId) {
        agentRegistry.unregister(agentId);
    }

    @Override
    public List<AgentDefinition> findAll() {
        return List.copyOf(agentRegistry.getAll());
    }

}
