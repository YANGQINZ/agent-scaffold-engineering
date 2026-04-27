package com.ai.agent.config;

import com.ai.agent.domain.agent.model.aggregate.*;
import com.ai.agent.domain.agent.model.entity.AgentscopeAgentConfig;
import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.McpServerConfig;
import com.ai.agent.domain.agent.model.entity.ToolConfig;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.ModelConfig;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.types.enums.EngineType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.*;

/**
 * Agent YAML配置加载器 — 启动时扫描classpath:agents/目录下所有YAML文件，
 * 解析为AgentDefinition子类并注册到AgentRegistry
 */
@Slf4j
@Configuration
public class AgentYamlLoader {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Bean
    public ApplicationRunner agentYamlLoaderRunner(AgentRegistry agentRegistry,
                                                    ApplicationContext applicationContext) {
        return args -> loadAgents(agentRegistry, applicationContext);
    }

    /**
     * 扫描并加载所有Agent YAML配置文件（递归子目录）
     */
    private void loadAgents(AgentRegistry agentRegistry, ApplicationContext applicationContext) {
        log.info("开始扫描Agent YAML配置文件...");

        List<AgentDefinition> allAgents = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:agents/**/*.yaml");

            for (Resource resource : resources) {
                try {
                    AgentDefinition agent = parseYaml(resource);
                    if (agent != null) {
                        allAgents.add(agent);
                        log.info("加载YAML文件: {}, agentId={}, engine={}",
                                resource.getFilename(), agent.getAgentId(), agent.getEngine());
                    }
                } catch (Exception e) {
                    log.error("YAML文件解析失败: {}, 错误: {}", resource.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("扫描Agent YAML文件失败: {}", e.getMessage());
        }

        for (AgentDefinition agent : allAgents) {
            validateToolClasses(agent, applicationContext);
            agentRegistry.register(agent);
        }

        validateAgentReferences(agentRegistry);

        log.info("Agent YAML加载完成: 共加载{}个Agent定义", agentRegistry.getAll().size());
        agentRegistry.getAll().forEach(agent ->
                log.info("  已注册Agent: agentId={}, name={}, engine={}",
                        agent.getAgentId(), agent.getName(), agent.getEngine()));
    }

    /**
     * 解析单个YAML文件为AgentDefinition（根键为 agent）
     */
    private AgentDefinition parseYaml(Resource resource) throws Exception {
        JsonNode root = yamlMapper.readTree(resource.getInputStream());

        if (!root.has("agent") || !root.get("agent").isObject()) {
            log.warn("YAML文件 {} 格式无效：缺少agent对象", resource.getFilename());
            return null;
        }

        JsonNode agentNode = root.get("agent");
        return parseAgentDefinition(agentNode);
    }

    /**
     * 根据 engine 字段分发到对应的解析方法
     */
    private AgentDefinition parseAgentDefinition(JsonNode node) {
        String id = node.path("id").asText();
        String name = node.path("name").asText();
        String engineStr = node.path("engine").asText("CHAT");
        EngineType engine = EngineType.valueOf(engineStr.toUpperCase());
        String instruction = node.path("instruction").asText("");

        ModelConfig modelConfig = parseModelConfig(node.path("model"));
        List<ToolConfig> tools = parseToolConfigs(node.path("tools"));
        List<McpServerConfig> mcpServers = node.has("mcpServers")
                ? parseMcpServerConfigs(node.get("mcpServers")) : List.of();

        return switch (engine) {
            case CHAT -> parseChatDefinition(id, name, instruction, modelConfig, tools, mcpServers);
            case GRAPH -> parseGraphDefinition(id, name, instruction, modelConfig, tools, mcpServers, node);
            case AGENTSCOPE -> parseAgentscopeDefinition(id, name, instruction, modelConfig, tools, mcpServers, node);
            case HYBRID -> parseHybridDefinition(id, name, instruction, modelConfig, tools, mcpServers, node);
        };
    }

    /**
     * 解析 CHAT 类型 Agent
     */
    private ChatAgentDefinition parseChatDefinition(String id, String name, String instruction,
                                                     ModelConfig modelConfig, List<ToolConfig> tools,
                                                     List<McpServerConfig> mcpServers) {
        return ChatAgentDefinition.builder()
                .agentId(id)
                .name(name)
                .engine(EngineType.CHAT)
                .instruction(instruction)
                .modelConfig(modelConfig)
                .tools(tools)
                .mcpServers(mcpServers)
                .build();
    }

    /**
     * 解析 GRAPH 类型 Agent
     */
    private GraphAgentDefinition parseGraphDefinition(String id, String name, String instruction,
                                                       ModelConfig modelConfig, List<ToolConfig> tools,
                                                       List<McpServerConfig> mcpServers, JsonNode node) {
        GraphAgentDefinition.GraphAgentDefinitionBuilder builder = GraphAgentDefinition.builder()
                .agentId(id)
                .name(name)
                .engine(EngineType.GRAPH)
                .instruction(instruction)
                .modelConfig(modelConfig)
                .tools(tools)
                .mcpServers(mcpServers);

        if (node.has("graph")) {
            JsonNode graphNode = node.get("graph");
            builder.graphStart(graphNode.path("start").asText(null));
            builder.graphNodes(parseWorkflowNodes(graphNode.path("nodes")));
            builder.graphEdges(parseGraphEdges(graphNode.path("edges")));
        }

        return builder.build();
    }

    /**
     * 解析 AGENTSCOPE 类型 Agent
     */
    private AgentscopeAgentDefinition parseAgentscopeDefinition(String id, String name, String instruction,
                                                                 ModelConfig modelConfig, List<ToolConfig> tools,
                                                                 List<McpServerConfig> mcpServers, JsonNode node) {
        AgentscopeAgentDefinition.AgentscopeAgentDefinitionBuilder builder = AgentscopeAgentDefinition.builder()
                .agentId(id)
                .name(name)
                .engine(EngineType.AGENTSCOPE)
                .instruction(instruction)
                .modelConfig(modelConfig)
                .tools(tools)
                .mcpServers(mcpServers);

        if (node.has("agentscope")) {
            JsonNode agentscopeNode = node.get("agentscope");
            builder.agentscopePipelineType(agentscopeNode.path("pipeline").asText("sequential"));
            builder.agentscopeAgents(parseAgentscopeAgentConfigs(agentscopeNode.path("agents")));
        }

        return builder.build();
    }

    /**
     * 解析 HYBRID 类型 Agent
     */
    private HybridAgentDefinition parseHybridDefinition(String id, String name, String instruction,
                                                         ModelConfig modelConfig, List<ToolConfig> tools,
                                                         List<McpServerConfig> mcpServers, JsonNode node) {
        HybridAgentDefinition.HybridAgentDefinitionBuilder builder = HybridAgentDefinition.builder()
                .agentId(id)
                .name(name)
                .engine(EngineType.HYBRID)
                .instruction(instruction)
                .modelConfig(modelConfig)
                .tools(tools)
                .mcpServers(mcpServers);

        if (node.has("graph")) {
            JsonNode graphNode = node.get("graph");
            builder.graphStart(graphNode.path("start").asText(null));
            builder.graphNodes(parseWorkflowNodes(graphNode.path("nodes")));
            builder.graphEdges(parseGraphEdges(graphNode.path("edges")));
        }

        if (node.has("subEngines")) {
            Map<String, EngineType> subEngines = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.get("subEngines").fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                subEngines.put(entry.getKey(), EngineType.valueOf(entry.getValue().asText().toUpperCase()));
            }
            builder.subEngines(subEngines);
        }

        return builder.build();
    }

    // ═══════════════════════════════════════════════════════
    // 通用解析方法
    // ═══════════════════════════════════════════════════════

    private ModelConfig parseModelConfig(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return new ModelConfig();
        }
        return ModelConfig.builder()
                .name(node.path("name").asText("qwq-plus"))
                .temperature(node.path("temperature").asDouble(0.7))
                .maxTokens(node.path("maxTokens").asInt(2000))
                .build();
    }

    private List<ToolConfig> parseToolConfigs(JsonNode node) {
        if (node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<ToolConfig> tools = new ArrayList<>();
        for (JsonNode toolNode : node) {
            tools.add(ToolConfig.builder()
                    .name(toolNode.path("name").asText())
                    .type(toolNode.path("type").asText("FUNCTION"))
                    .description(toolNode.path("description").asText(""))
                    .className(toolNode.path("className").asText(null))
                    .build());
        }
        return tools;
    }

    private List<WorkflowNode> parseWorkflowNodes(JsonNode node) {
        if (node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<WorkflowNode> nodes = new ArrayList<>();
        for (JsonNode nodeItem : node) {
            nodes.add(WorkflowNode.builder()
                    .id(nodeItem.path("id").asText())
                    .agentId(nodeItem.path("agentId").asText(null))
                    .reactAgentId(nodeItem.path("reactAgentId").asText(null))
                    .next(nodeItem.path("next").asText(null))
                    .build());
        }
        return nodes;
    }

    private List<GraphEdge> parseGraphEdges(JsonNode node) {
        if (node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<GraphEdge> edges = new ArrayList<>();
        for (JsonNode edgeNode : node) {
            edges.add(GraphEdge.builder()
                    .from(edgeNode.path("from").asText())
                    .to(edgeNode.path("to").asText())
                    .condition(edgeNode.path("condition").asText(null))
                    .build());
        }
        return edges;
    }

    private List<AgentscopeAgentConfig> parseAgentscopeAgentConfigs(JsonNode node) {
        if (node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<AgentscopeAgentConfig> agents = new ArrayList<>();
        for (JsonNode agentNode : node) {
            AgentscopeAgentConfig config = AgentscopeAgentConfig.builder()
                    .agentId(agentNode.path("agentId").asText())
                    .mcpServers(parseMcpServerConfigs(agentNode.path("mcpServers")))
                    .build();
            if (agentNode.has("enableTools") && agentNode.get("enableTools").isArray()) {
                List<String> enableTools = new ArrayList<>();
                for (JsonNode toolNode : agentNode.get("enableTools")) {
                    enableTools.add(toolNode.asText());
                }
                config.setEnableTools(enableTools);
            }
            agents.add(config);
        }
        return agents;
    }

    private List<McpServerConfig> parseMcpServerConfigs(JsonNode node) {
        if (node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<McpServerConfig> servers = new ArrayList<>();
        for (JsonNode serverNode : node) {
            McpServerConfig.McpServerConfigBuilder builder = McpServerConfig.builder()
                    .name(serverNode.path("name").asText())
                    .transport(serverNode.path("transport").asText("stdio"));

            if (serverNode.has("command")) {
                builder.command(serverNode.path("command").asText());
            }
            if (serverNode.has("args") && serverNode.get("args").isArray()) {
                List<String> args = new ArrayList<>();
                for (JsonNode argNode : serverNode.get("args")) {
                    args.add(argNode.asText());
                }
                builder.args(args);
            }
            if (serverNode.has("url")) {
                builder.url(serverNode.path("url").asText());
            }
            if (serverNode.has("headers") && serverNode.get("headers").isObject()) {
                Map<String, String> headers = new HashMap<>();
                Iterator<Map.Entry<String, JsonNode>> fields = serverNode.get("headers").fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    headers.put(entry.getKey(), entry.getValue().asText());
                }
                builder.headers(headers);
            }
            servers.add(builder.build());
        }
        return servers;
    }

    /**
     * 验证FUNCTION类型工具类名是否存在
     */
    private void validateToolClasses(AgentDefinition agent, ApplicationContext applicationContext) {
        if (agent.getTools() == null || agent.getTools().isEmpty()) {
            return;
        }

        List<ToolConfig> validTools = new ArrayList<>();
        for (ToolConfig tool : agent.getTools()) {
            if (!"FUNCTION".equals(tool.getType())) {
                validTools.add(tool);
                continue;
            }
            if (tool.getClassName() == null || tool.getClassName().isBlank()) {
                log.warn("Agent '{}' 工具 '{}' 缺少className，跳过该工具", agent.getAgentId(), tool.getName());
                continue;
            }
            try {
                Class.forName(tool.getClassName());
                validTools.add(tool);
            } catch (ClassNotFoundException e) {
                log.error("Agent '{}' 工具 '{}' 的className '{}' 不存在，跳过该工具",
                        agent.getAgentId(), tool.getName(), tool.getClassName());
            }
        }
        agent.setTools(validTools);
    }

    /**
     * 验证Agent定义中的agentId引用是否存在
     */
    private void validateAgentReferences(AgentRegistry agentRegistry) {
        List<String> invalidAgentIds = new ArrayList<>();

        for (AgentDefinition agent : agentRegistry.getAll()) {
            Set<String> referencedIds = new HashSet<>();

            if (agent instanceof GraphAgentDefinition graphDef) {
                collectNodeReferences(graphDef.getGraphNodes(), referencedIds);
            } else if (agent instanceof HybridAgentDefinition hybridDef) {
                collectNodeReferences(hybridDef.getGraphNodes(), referencedIds);
            } else if (agent instanceof AgentscopeAgentDefinition asDef) {
                if (asDef.getAgentscopeAgents() != null) {
                    for (AgentscopeAgentConfig asConfig : asDef.getAgentscopeAgents()) {
                        if (asConfig.getAgentId() != null) {
                            referencedIds.add(asConfig.getAgentId());
                        }
                    }
                }
            }

            for (String refId : referencedIds) {
                if (refId.equals(agent.getAgentId())) {
                    continue;
                }
                if (agentRegistry.get(refId) == null) {
                    log.error("Agent '{}' 引用了不存在的子Agent: {}，跳过该Agent定义",
                            agent.getAgentId(), refId);
                    invalidAgentIds.add(agent.getAgentId());
                    break;
                }
            }
        }

        for (String invalidId : invalidAgentIds) {
            log.warn("Agent '{}' 存在无效的子Agent引用，建议检查配置", invalidId);
        }
    }

    /**
     * 收集节点中引用的子Agent ID
     */
    private void collectNodeReferences(List<WorkflowNode> nodes, Set<String> referencedIds) {
        if (nodes == null) {
            return;
        }
        for (WorkflowNode node : nodes) {
            if (node.getAgentId() != null && !node.getAgentId().isBlank()) {
                referencedIds.add(node.getAgentId());
            }
            if (node.getReactAgentId() != null && !node.getReactAgentId().isBlank()) {
                referencedIds.add(node.getReactAgentId());
            }
        }
    }

}
