package com.ai.agent.infrastructure.persistent.repository;

import com.ai.agent.domain.agent.model.aggregate.*;
import com.ai.agent.domain.agent.model.entity.AgentscopeAgentConfig;
import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.McpServerConfig;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.ModelConfig;
import com.ai.agent.domain.agent.repository.IAgentDefinitionRepository;
import com.ai.agent.infrastructure.persistent.dao.IAgentAgentscopeConfigDao;
import com.ai.agent.infrastructure.persistent.dao.IAgentDefinitionDao;
import com.ai.agent.infrastructure.persistent.dao.IAgentGraphEdgeDao;
import com.ai.agent.infrastructure.persistent.dao.IAgentWorkflowNodeDao;
import com.ai.agent.infrastructure.persistent.po.*;
import com.ai.agent.types.enums.EngineType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Agent定义仓储实现 — 数据库持久化
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AgentDefinitionRepositoryImpl implements IAgentDefinitionRepository {

    private final IAgentDefinitionDao agentDefinitionDao;
    private final IAgentWorkflowNodeDao agentWorkflowNodeDao;
    private final IAgentGraphEdgeDao agentGraphEdgeDao;
    private final IAgentAgentscopeConfigDao agentAgentscopeConfigDao;
    private final ObjectMapper objectMapper;

    // ═══════════════════════════════════════════════════════
    // 保存
    // ═══════════════════════════════════════════════════════

    @Override
    @Transactional
    public void save(AgentDefinition definition) {
        AgentDefinitionPO po = toMainPO(definition);
        LocalDateTime now = LocalDateTime.now();
        po.setCreatedAt(now);
        po.setUpdatedAt(now);
        agentDefinitionDao.insert(po);
        Long definitionId = po.getId();

        insertChildren(definition, definitionId);
        log.info("保存Agent定义: agentId={}, engine={}", definition.getAgentId(), definition.getEngine());
    }

    // ═══════════════════════════════════════════════════════
    // 更新
    // ═══════════════════════════════════════════════════════

    @Override
    @Transactional
    public void update(AgentDefinition definition) {
        AgentDefinitionPO existing = agentDefinitionDao.selectByAgentId(definition.getAgentId());
        if (existing == null) {
            throw new IllegalArgumentException("Agent定义不存在: " + definition.getAgentId());
        }
        Long definitionId = existing.getId();

        // 先删除子表
        deleteChildren(definitionId, definition.getEngine());

        // 更新主表
        AgentDefinitionPO po = toMainPO(definition);
        po.setUpdatedAt(LocalDateTime.now());
        agentDefinitionDao.updateByAgentId(po);

        // 重新插入子表
        insertChildren(definition, definitionId);
        log.info("更新Agent定义: agentId={}, engine={}", definition.getAgentId(), definition.getEngine());
    }

    // ═══════════════════════════════════════════════════════
    // 根据agentId查询
    // ═══════════════════════════════════════════════════════

    @Override
    public Optional<AgentDefinition> findByAgentId(String agentId) {
        AgentDefinitionPO po = agentDefinitionDao.selectByAgentId(agentId);
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(assembleDefinition(po));
    }

    // ═══════════════════════════════════════════════════════
    // 查询全部
    // ═══════════════════════════════════════════════════════

    @Override
    public List<AgentDefinition> findAll() {
        List<AgentDefinitionPO> pos = agentDefinitionDao.selectAll();
        return pos.stream().map(this::assembleDefinition).toList();
    }

    // ═══════════════════════════════════════════════════════
    // 根据agentId删除
    // ═══════════════════════════════════════════════════════

    @Override
    @Transactional
    public void deleteByAgentId(String agentId) {
        AgentDefinitionPO existing = agentDefinitionDao.selectByAgentId(agentId);
        if (existing != null) {
            deleteChildren(existing.getId(), EngineType.valueOf(existing.getEngine()));
        }
        agentDefinitionDao.deleteByAgentId(agentId);
        log.info("删除Agent定义: agentId={}", agentId);
    }

    // ═══════════════════════════════════════════════════════
    // PO → Domain 组装
    // ═══════════════════════════════════════════════════════

    /**
     * 根据主表PO + 子表数据组装为正确的AgentDefinition子类
     */
    private AgentDefinition assembleDefinition(AgentDefinitionPO po) {
        EngineType engine = EngineType.valueOf(po.getEngine());
        ModelConfig modelConfig = parseJson(po.getModelConfig(), ModelConfig.class, new ModelConfig());
        List<McpServerConfig> mcpServers = parseJsonList(po.getMcpServers(), new TypeReference<>() {}, List.of());

        return switch (engine) {
            case CHAT -> assembleChatDefinition(po, modelConfig, mcpServers);
            case GRAPH -> assembleGraphDefinition(po, modelConfig, mcpServers);
            case AGENTSCOPE -> assembleAgentscopeDefinition(po, modelConfig, mcpServers);
            case HYBRID -> assembleHybridDefinition(po, modelConfig, mcpServers);
        };
    }

    private ChatAgentDefinition assembleChatDefinition(AgentDefinitionPO po,
                                                        ModelConfig modelConfig,
                                                        List<McpServerConfig> mcpServers) {
        return ChatAgentDefinition.builder()
                .agentId(po.getAgentId())
                .name(po.getName())
                .engine(EngineType.CHAT)
                .instruction(po.getInstruction())
                .modelConfig(modelConfig)
                .mcpServers(mcpServers)
                .build();
    }

    private GraphAgentDefinition assembleGraphDefinition(AgentDefinitionPO po,
                                                          ModelConfig modelConfig,
                                                          List<McpServerConfig> mcpServers) {
        List<WorkflowNode> nodes = queryWorkflowNodes(po.getId());
        List<GraphEdge> edges = queryGraphEdges(po.getId());

        List<String> graphStartList = parseGraphStart(po.getGraphStart());

        return GraphAgentDefinition.builder()
                .agentId(po.getAgentId())
                .name(po.getName())
                .engine(EngineType.GRAPH)
                .instruction(po.getInstruction())
                .modelConfig(modelConfig)
                .mcpServers(mcpServers)
                .graphStart(graphStartList)
                .graphNodes(nodes)
                .graphEdges(edges)
                .build();
    }

    private AgentscopeAgentDefinition assembleAgentscopeDefinition(AgentDefinitionPO po,
                                                                     ModelConfig modelConfig,
                                                                     List<McpServerConfig> mcpServers) {
        List<AgentscopeAgentConfig> agents = queryAgentscopeConfigs(po.getId());

        return AgentscopeAgentDefinition.builder()
                .agentId(po.getAgentId())
                .name(po.getName())
                .engine(EngineType.AGENTSCOPE)
                .instruction(po.getInstruction())
                .modelConfig(modelConfig)
                .mcpServers(mcpServers)
                .agentscopePipelineType(po.getAgentscopePipelineType())
                .agentscopeAgents(agents)
                .build();
    }

    private HybridAgentDefinition assembleHybridDefinition(AgentDefinitionPO po,
                                                             ModelConfig modelConfig,
                                                             List<McpServerConfig> mcpServers) {
        List<WorkflowNode> nodes = queryWorkflowNodes(po.getId());
        List<GraphEdge> edges = queryGraphEdges(po.getId());
        Map<String, EngineType> subEngines = parseJson(po.getSubEngines(),
                new TypeReference<>() {}, Map.of());
        List<String> graphStartList = parseGraphStart(po.getGraphStart());

        return HybridAgentDefinition.builder()
                .agentId(po.getAgentId())
                .name(po.getName())
                .engine(EngineType.HYBRID)
                .instruction(po.getInstruction())
                .modelConfig(modelConfig)
                .mcpServers(mcpServers)
                .graphStart(graphStartList)
                .graphNodes(nodes)
                .graphEdges(edges)
                .subEngines(subEngines)
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // Domain → PO 转换
    // ═══════════════════════════════════════════════════════

    private AgentDefinitionPO toMainPO(AgentDefinition definition) {
        AgentDefinitionPO po = new AgentDefinitionPO();
        po.setAgentId(definition.getAgentId());
        po.setName(definition.getName());
        po.setEngine(definition.getEngine().name());
        po.setInstruction(definition.getInstruction());
        po.setModelConfig(toJson(definition.getModelConfig()));
        po.setMcpServers(toJson(definition.getMcpServers()));

        // 根据子类设置特有字段
        if (definition instanceof GraphAgentDefinition graphDef) {
            po.setGraphStart(toJson(graphDef.getGraphStart()));
        } else if (definition instanceof AgentscopeAgentDefinition asDef) {
            po.setAgentscopePipelineType(asDef.getAgentscopePipelineType());
        } else if (definition instanceof HybridAgentDefinition hybridDef) {
            po.setGraphStart(toJson(hybridDef.getGraphStart()));
            po.setAgentscopePipelineType(null);
            po.setSubEngines(toJson(hybridDef.getSubEngines()));
        }

        return po;
    }

    // ═══════════════════════════════════════════════════════
    // 子表操作
    // ═══════════════════════════════════════════════════════

    private void insertChildren(AgentDefinition definition, Long definitionId) {
        EngineType engine = definition.getEngine();

        if (engine == EngineType.GRAPH || engine == EngineType.HYBRID) {
            insertWorkflowNodes(definition, definitionId);
            insertGraphEdges(definition, definitionId);
        }

        if (engine == EngineType.AGENTSCOPE) {
            insertAgentscopeConfigs(definition, definitionId);
        }
    }

    private void deleteChildren(Long definitionId, EngineType engine) {
        if (engine == EngineType.GRAPH || engine == EngineType.HYBRID) {
            agentWorkflowNodeDao.deleteByAgentDefinitionId(definitionId);
            agentGraphEdgeDao.deleteByAgentDefinitionId(definitionId);
        }

        if (engine == EngineType.AGENTSCOPE) {
            agentAgentscopeConfigDao.deleteByAgentDefinitionId(definitionId);
        }
    }

    private void insertWorkflowNodes(AgentDefinition definition, Long definitionId) {
        List<WorkflowNode> nodes = getWorkflowNodes(definition);
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        List<AgentWorkflowNodePO> pos = IntStream.range(0, nodes.size())
                .mapToObj(i -> {
                    WorkflowNode node = nodes.get(i);
                    AgentWorkflowNodePO po = new AgentWorkflowNodePO();
                    po.setAgentDefinitionId(definitionId);
                    po.setNodeId(node.getId());
                    po.setAgentId(node.getAgentId());
                    po.setReactAgentId(node.getReactAgentId());
                    po.setNextNodeId(node.getNext());
                    po.setRagEnabled(node.getRagEnabled());
                    po.setKnowledgeBaseId(node.getKnowledgeBaseId());
                    po.setInstruction(node.getInstruction());
                    po.setSubEngine(node.getSubEngine());
                    po.setMcpServers(node.getMcpServers() != null ? toJson(node.getMcpServers()) : null);
                    po.setSortOrder(i);
                    return po;
                })
                .toList();
        agentWorkflowNodeDao.batchInsert(pos);
    }

    private void insertGraphEdges(AgentDefinition definition, Long definitionId) {
        List<GraphEdge> edges = getGraphEdges(definition);
        if (edges == null || edges.isEmpty()) {
            return;
        }

        List<AgentGraphEdgePO> pos = IntStream.range(0, edges.size())
                .mapToObj(i -> {
                    GraphEdge edge = edges.get(i);
                    AgentGraphEdgePO po = new AgentGraphEdgePO();
                    po.setAgentDefinitionId(definitionId);
                    po.setFromNode(edge.getFrom());
                    po.setToNode(edge.getTo());
                    po.setConditionExpr(edge.getCondition());
                    po.setSortOrder(i);
                    return po;
                })
                .toList();
        agentGraphEdgeDao.batchInsert(pos);
    }

    private void insertAgentscopeConfigs(AgentDefinition definition, Long definitionId) {
        if (!(definition instanceof AgentscopeAgentDefinition asDef)) {
            return;
        }
        List<AgentscopeAgentConfig> agents = asDef.getAgentscopeAgents();
        if (agents == null || agents.isEmpty()) {
            return;
        }

        List<AgentAgentscopeConfigPO> pos = IntStream.range(0, agents.size())
                .mapToObj(i -> {
                    AgentscopeAgentConfig config = agents.get(i);
                    AgentAgentscopeConfigPO po = new AgentAgentscopeConfigPO();
                    po.setAgentDefinitionId(definitionId);
                    po.setConfigAgentId(config.getAgentId());
                    po.setName(config.getName());
                    po.setInstruction(config.getInstruction());
                    po.setMcpServers(toJson(config.getMcpServers()));
                    po.setEnableTools(toJson(config.getEnableTools()));
                    po.setOutputKey(config.getOutputKey());
                    po.setSortOrder(i);
                    return po;
                })
                .toList();
        agentAgentscopeConfigDao.batchInsert(pos);
    }

    // ═══════════════════════════════════════════════════════
    // 子表查询
    // ═══════════════════════════════════════════════════════

    private List<WorkflowNode> queryWorkflowNodes(Long definitionId) {
        List<AgentWorkflowNodePO> pos = agentWorkflowNodeDao.selectByAgentDefinitionId(definitionId);
        return pos.stream()
                .map(po -> WorkflowNode.builder()
                        .id(po.getNodeId())
                        .agentId(po.getAgentId())
                        .reactAgentId(po.getReactAgentId())
                        .next(po.getNextNodeId())
                        .ragEnabled(po.getRagEnabled())
                        .knowledgeBaseId(po.getKnowledgeBaseId())
                        .instruction(po.getInstruction())
                        .subEngine(po.getSubEngine())
                        .mcpServers(parseJsonList(po.getMcpServers(), new TypeReference<>() {}, List.of()))
                        .build())
                .toList();
    }

    private List<GraphEdge> queryGraphEdges(Long definitionId) {
        List<AgentGraphEdgePO> pos = agentGraphEdgeDao.selectByAgentDefinitionId(definitionId);
        return pos.stream()
                .map(po -> GraphEdge.builder()
                        .from(po.getFromNode())
                        .to(po.getToNode())
                        .condition(po.getConditionExpr())
                        .build())
                .toList();
    }

    private List<AgentscopeAgentConfig> queryAgentscopeConfigs(Long definitionId) {
        List<AgentAgentscopeConfigPO> pos = agentAgentscopeConfigDao.selectByAgentDefinitionId(definitionId);
        return pos.stream()
                .map(po -> AgentscopeAgentConfig.builder()
                        .agentId(po.getConfigAgentId())
                        .name(po.getName())
                        .instruction(po.getInstruction())
                        .mcpServers(parseJsonList(po.getMcpServers(), new TypeReference<>() {}, List.of()))
                        .enableTools(parseJsonList(po.getEnableTools(), new TypeReference<>() {}, List.of()))
                        .outputKey(po.getOutputKey())
                        .build())
                .toList();
    }

    // ═══════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════

    private List<WorkflowNode> getWorkflowNodes(AgentDefinition definition) {
        if (definition instanceof GraphAgentDefinition graphDef) {
            return graphDef.getGraphNodes();
        } else if (definition instanceof HybridAgentDefinition hybridDef) {
            return hybridDef.getGraphNodes();
        }
        return List.of();
    }

    private List<GraphEdge> getGraphEdges(AgentDefinition definition) {
        if (definition instanceof GraphAgentDefinition graphDef) {
            return graphDef.getGraphEdges();
        } else if (definition instanceof HybridAgentDefinition hybridDef) {
            return hybridDef.getGraphEdges();
        }
        return List.of();
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private <T> T parseJson(String json, Class<T> clazz, T defaultValue) {
        if (json == null || json.isBlank()) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化失败: {}", e.getMessage());
            return defaultValue;
        }
    }

    private <T> T parseJson(String json, TypeReference<T> typeRef, T defaultValue) {
        if (json == null || json.isBlank()) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            // 兼容裸字符串（如 graph_start 存了 "research" 而非 ["research"]）
            if (json.startsWith("[") || json.startsWith("{")) {
                log.error("JSON反序列化失败: {}", e.getMessage());
            }
            return defaultValue;
        }
    }

    /**
     * 解析 graphStart 字段，兼容 JSON 数组和裸字符串
     * 旧数据可能存了 "research" 而非 ["research"]
     */
    private List<String> parseGraphStart(String graphStart) {
        if (graphStart == null || graphStart.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(graphStart, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            // 裸字符串，包装为单元素列表
            return List.of(graphStart.trim());
        }
    }

    private <T> List<T> parseJsonList(String json, TypeReference<List<T>> typeRef, List<T> defaultValue) {
        if (json == null || json.isBlank()) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.error("JSON列表反序列化失败: {}", e.getMessage());
            return defaultValue;
        }
    }

}
