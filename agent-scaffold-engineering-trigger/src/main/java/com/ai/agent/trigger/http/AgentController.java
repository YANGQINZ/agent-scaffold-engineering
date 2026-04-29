package com.ai.agent.trigger.http;

import com.ai.agent.api.model.agent.*;
import com.ai.agent.domain.agent.model.aggregate.*;
import com.ai.agent.domain.agent.model.entity.AgentscopeAgentConfig;
import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.McpServerConfig;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.ModelConfig;
import com.ai.agent.domain.agent.repository.IAgentDefinitionRepository;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.model.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent定义管理接口 — CRUD操作
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final IAgentDefinitionRepository agentDefinitionRepository;
    private final AgentRegistry agentRegistry;

    /**
     * 查询所有Agent定义
     */
    @GetMapping
    public Response<List<AgentDefinitionDTO>> list() {
        List<AgentDefinition> definitions = agentDefinitionRepository.findAll();
        List<AgentDefinitionDTO> dtos = definitions.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return Response.buildSuccess(dtos);
    }

    /**
     * 根据agentId查询Agent定义
     */
    @GetMapping("/{agentId}")
    public Response<AgentDefinitionDTO> getByAgentId(@PathVariable String agentId) {
        return agentDefinitionRepository.findByAgentId(agentId)
                .map(definition -> Response.buildSuccess(convertToDTO(definition)))
                .orElse(Response.buildError("Agent定义不存在: " + agentId));
    }

    /**
     * 创建Agent定义
     */
    @PostMapping
    public Response<AgentDefinitionDTO> create(@Validated @RequestBody AgentDefinitionDTO dto) {
        AgentDefinition definition = convertToDomain(dto);
        agentDefinitionRepository.save(definition);
        agentRegistry.register(definition);
        log.info("创建Agent定义: agentId={}, engine={}", definition.getAgentId(), definition.getEngine());
        return Response.buildSuccess(convertToDTO(definition));
    }

    /**
     * 更新Agent定义
     */
    @PutMapping("/{agentId}")
    public Response<AgentDefinitionDTO> update(@PathVariable String agentId, @Validated @RequestBody AgentDefinitionDTO dto) {
        dto.setAgentId(agentId);
        AgentDefinition definition = convertToDomain(dto);
        agentDefinitionRepository.update(definition);
        agentRegistry.register(definition);
        log.info("更新Agent定义: agentId={}, engine={}", definition.getAgentId(), definition.getEngine());
        return Response.buildSuccess(convertToDTO(definition));
    }

    /**
     * 删除Agent定义
     */
    @DeleteMapping("/{agentId}")
    public Response<Void> delete(@PathVariable String agentId) {
        agentDefinitionRepository.deleteByAgentId(agentId);
        agentRegistry.unregister(agentId);
        log.info("删除Agent定义: agentId={}", agentId);
        return Response.buildSuccess();
    }

    // ═══════════════════════════════════════════════════════
    // Domain → DTO 转换
    // ═══════════════════════════════════════════════════════

    private AgentDefinitionDTO convertToDTO(AgentDefinition definition) {
        AgentDefinitionDTO.AgentDefinitionDTOBuilder builder = AgentDefinitionDTO.builder()
                .agentId(definition.getAgentId())
                .name(definition.getName())
                .engine(definition.getEngine())
                .instruction(definition.getInstruction())
                .modelConfig(convertModelConfigToDTO(definition.getModelConfig()))
                .mcpServers(convertMcpServersToDTO(definition.getMcpServers()));

        if (definition instanceof GraphAgentDefinition graphDef) {
            builder.graphStart(graphDef.getGraphStart());
            builder.graphNodes(convertWorkflowNodesToDTO(graphDef.getGraphNodes()));
            builder.graphEdges(convertGraphEdgesToDTO(graphDef.getGraphEdges()));
        } else if (definition instanceof AgentscopeAgentDefinition asDef) {
            builder.agentscopePipelineType(asDef.getAgentscopePipelineType());
            builder.agentscopeAgents(convertAgentscopeAgentsToDTO(asDef.getAgentscopeAgents()));
        } else if (definition instanceof HybridAgentDefinition hybridDef) {
            builder.graphStart(hybridDef.getGraphStart());
            builder.graphNodes(convertWorkflowNodesToDTO(hybridDef.getGraphNodes()));
            builder.graphEdges(convertGraphEdgesToDTO(hybridDef.getGraphEdges()));
            builder.subEngines(hybridDef.getSubEngines());
        }

        return builder.build();
    }

    private ModelConfigDTO convertModelConfigToDTO(ModelConfig modelConfig) {
        if (modelConfig == null) {
            return null;
        }
        return ModelConfigDTO.builder()
                .name(modelConfig.getName())
                .temperature(modelConfig.getTemperature())
                .maxTokens(modelConfig.getMaxTokens())
                .build();
    }

    private List<McpServerConfigDTO> convertMcpServersToDTO(List<McpServerConfig> servers) {
        if (servers == null) {
            return null;
        }
        return servers.stream()
                .map(s -> McpServerConfigDTO.builder()
                        .name(s.getName())
                        .transport(s.getTransport())
                        .command(s.getCommand())
                        .args(s.getArgs())
                        .url(s.getUrl())
                        .headers(s.getHeaders())
                        .build())
                .collect(Collectors.toList());
    }

    private List<WorkflowNodeDTO> convertWorkflowNodesToDTO(List<WorkflowNode> nodes) {
        if (nodes == null) {
            return null;
        }
        return nodes.stream()
                .map(n -> WorkflowNodeDTO.builder()
                        .id(n.getId())
                        .agentId(n.getAgentId())
                        .reactAgentId(n.getReactAgentId())
                        .next(n.getNext())
                        .ragEnabled(n.getRagEnabled())
                        .knowledgeBaseId(n.getKnowledgeBaseId())
                        .build())
                .collect(Collectors.toList());
    }

    private List<GraphEdgeDTO> convertGraphEdgesToDTO(List<GraphEdge> edges) {
        if (edges == null) {
            return null;
        }
        return edges.stream()
                .map(e -> GraphEdgeDTO.builder()
                        .from(e.getFrom())
                        .to(e.getTo())
                        .condition(e.getCondition())
                        .build())
                .collect(Collectors.toList());
    }

    private List<AgentscopeAgentConfigDTO> convertAgentscopeAgentsToDTO(List<AgentscopeAgentConfig> agents) {
        if (agents == null) {
            return null;
        }
        return agents.stream()
                .map(a -> AgentscopeAgentConfigDTO.builder()
                        .agentId(a.getAgentId())
                        .mcpServers(convertMcpServersToDTO(a.getMcpServers()))
                        .enableTools(a.getEnableTools())
                        .build())
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════
    // DTO → Domain 转换
    // ═══════════════════════════════════════════════════════

    private AgentDefinition convertToDomain(AgentDefinitionDTO dto) {
        ModelConfig modelConfig = convertModelConfigToDomain(dto.getModelConfig());
        List<McpServerConfig> mcpServers = convertMcpServersToDomain(dto.getMcpServers());
        EngineType engine = dto.getEngine() != null ? dto.getEngine() : EngineType.CHAT;

        return switch (engine) {
            case CHAT -> ChatAgentDefinition.builder()
                    .agentId(dto.getAgentId())
                    .name(dto.getName())
                    .engine(EngineType.CHAT)
                    .instruction(dto.getInstruction())
                    .modelConfig(modelConfig)
                    .mcpServers(mcpServers)
                    .build();

            case GRAPH -> GraphAgentDefinition.builder()
                    .agentId(dto.getAgentId())
                    .name(dto.getName())
                    .engine(EngineType.GRAPH)
                    .instruction(dto.getInstruction())
                    .modelConfig(modelConfig)
                    .mcpServers(mcpServers)
                    .graphStart(dto.getGraphStart())
                    .graphNodes(convertWorkflowNodesToDomain(dto.getGraphNodes()))
                    .graphEdges(convertGraphEdgesToDomain(dto.getGraphEdges()))
                    .build();

            case AGENTSCOPE -> AgentscopeAgentDefinition.builder()
                    .agentId(dto.getAgentId())
                    .name(dto.getName())
                    .engine(EngineType.AGENTSCOPE)
                    .instruction(dto.getInstruction())
                    .modelConfig(modelConfig)
                    .mcpServers(mcpServers)
                    .agentscopePipelineType(dto.getAgentscopePipelineType())
                    .agentscopeAgents(convertAgentscopeAgentsToDomain(dto.getAgentscopeAgents()))
                    .build();

            case HYBRID -> HybridAgentDefinition.builder()
                    .agentId(dto.getAgentId())
                    .name(dto.getName())
                    .engine(EngineType.HYBRID)
                    .instruction(dto.getInstruction())
                    .modelConfig(modelConfig)
                    .mcpServers(mcpServers)
                    .graphStart(dto.getGraphStart())
                    .graphNodes(convertWorkflowNodesToDomain(dto.getGraphNodes()))
                    .graphEdges(convertGraphEdgesToDomain(dto.getGraphEdges()))
                    .subEngines(dto.getSubEngines() != null ? dto.getSubEngines() : Map.of())
                    .build();
        };
    }

    private ModelConfig convertModelConfigToDomain(ModelConfigDTO dto) {
        if (dto == null) {
            return new ModelConfig();
        }
        return ModelConfig.builder()
                .name(dto.getName())
                .temperature(dto.getTemperature())
                .maxTokens(dto.getMaxTokens())
                .build();
    }

    private List<McpServerConfig> convertMcpServersToDomain(List<McpServerConfigDTO> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream()
                .map(d -> McpServerConfig.builder()
                        .name(d.getName())
                        .transport(d.getTransport())
                        .command(d.getCommand())
                        .args(d.getArgs())
                        .url(d.getUrl())
                        .headers(d.getHeaders())
                        .build())
                .collect(Collectors.toList());
    }

    private List<WorkflowNode> convertWorkflowNodesToDomain(List<WorkflowNodeDTO> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream()
                .map(d -> WorkflowNode.builder()
                        .id(d.getId())
                        .agentId(d.getAgentId())
                        .reactAgentId(d.getReactAgentId())
                        .next(d.getNext())
                        .ragEnabled(d.getRagEnabled())
                        .knowledgeBaseId(d.getKnowledgeBaseId())
                        .build())
                .collect(Collectors.toList());
    }

    private List<GraphEdge> convertGraphEdgesToDomain(List<GraphEdgeDTO> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream()
                .map(d -> GraphEdge.builder()
                        .from(d.getFrom())
                        .to(d.getTo())
                        .condition(d.getCondition())
                        .build())
                .collect(Collectors.toList());
    }

    private List<AgentscopeAgentConfig> convertAgentscopeAgentsToDomain(List<AgentscopeAgentConfigDTO> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream()
                .map(d -> AgentscopeAgentConfig.builder()
                        .agentId(d.getAgentId())
                        .mcpServers(convertMcpServersToDomain(d.getMcpServers()))
                        .enableTools(d.getEnableTools())
                        .build())
                .collect(Collectors.toList());
    }

}
