package com.ai.agent.config;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.entity.AgentscopeAgentConfig;
import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.McpServerConfig;
import com.ai.agent.domain.agent.model.entity.ToolConfig;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.ModelConfig;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.types.enums.AgentMode;
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
 * 解析为AgentDefinition并注册到AgentRegistry
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
     * 扫描并加载所有Agent YAML配置文件
     */
    private void loadAgents(AgentRegistry agentRegistry, ApplicationContext applicationContext) {
        log.info("开始扫描Agent YAML配置文件...");

        // 第一阶段：加载所有YAML文件为AgentDefinition列表
        List<AgentDefinition> allAgents = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:agents/*.yaml");

            for (Resource resource : resources) {
                try {
                    List<AgentDefinition> agents = parseYaml(resource);
                    allAgents.addAll(agents);
                    log.info("加载YAML文件: {}, 包含{}个Agent定义", resource.getFilename(), agents.size());
                } catch (Exception e) {
                    // YAML解析失败记录错误日志跳过该文件，不阻止应用启动
                    log.error("YAML文件解析失败: {}, 错误: {}", resource.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("扫描Agent YAML文件失败: {}", e.getMessage());
        }

        // 第二阶段：注册所有Agent定义到Registry
        for (AgentDefinition agent : allAgents) {
            // 验证FUNCTION类型工具类名是否存在
            validateToolClasses(agent, applicationContext);
            agentRegistry.register(agent);
        }

        // 第三阶段：验证agentId引用（workflowNodes和graphNodes中引用的子Agent）
        validateAgentReferences(agentRegistry);

        // 打印加载结果
        log.info("Agent YAML加载完成: 共加载{}个Agent定义", agentRegistry.getAll().size());
        agentRegistry.getAll().forEach(agent ->
                log.info("  已注册Agent: agentId={}, name={}, engine={}", agent.getAgentId(), agent.getName(), agent.getEngine()));
    }

    /**
     * 解析单个YAML文件为AgentDefinition列表
     */
    private List<AgentDefinition> parseYaml(Resource resource) throws Exception {
        JsonNode root = yamlMapper.readTree(resource.getInputStream());

        if (!root.has("agents") || !root.get("agents").isArray()) {
            log.warn("YAML文件 {} 格式无效：缺少agents数组", resource.getFilename());
            return Collections.emptyList();
        }

        List<AgentDefinition> agents = new ArrayList<>();
        for (JsonNode agentNode : root.get("agents")) {
            try {
                agents.add(parseAgentDefinition(agentNode));
            } catch (Exception e) {
                log.error("解析Agent定义失败: {}, 错误: {}", agentNode.path("id").asText("unknown"), e.getMessage());
            }
        }
        return agents;
    }

    /**
     * 解析单个Agent定义
     */
    private AgentDefinition parseAgentDefinition(JsonNode node) {
        String id = node.path("id").asText();
        String name = node.path("name").asText();
        String modeStr = node.path("mode").asText("REACT");
        AgentMode mode = AgentMode.valueOf(modeStr.toUpperCase());
        String instruction = node.path("instruction").asText("");

        // 解析引擎类型（新增，缺省时根据 mode 推断，无 mode 时默认 GRAPH）
        String engineStr = node.path("engine").asText(null);
        EngineType engine;
        if (engineStr != null && !engineStr.isBlank()) {
            engine = EngineType.valueOf(engineStr.toUpperCase());
        } else {
            // 兼容旧 YAML：根据 mode 推断 engine
            engine = switch (mode) {
                case GRAPH, HYBRID -> EngineType.valueOf(modeStr.toUpperCase());
                case REACT, WORKFLOW -> EngineType.GRAPH;
            };
        }

        // 解析模型配置
        ModelConfig modelConfig = parseModelConfig(node.path("model"));

        // 解析工具配置
        List<ToolConfig> tools = parseToolConfigs(node.path("tools"));

        AgentDefinition.AgentDefinitionBuilder builder = AgentDefinition.builder()
                .agentId(id)
                .name(name)
                .mode(mode)
                .engine(engine)
                .instruction(instruction)
                .modelConfig(modelConfig)
                .tools(tools);

        // 解析Workflow配置
        if (node.has("workflow")) {
            JsonNode workflowNode = node.get("workflow");
            builder.workflowEntry(workflowNode.path("entry").asText(null));
            builder.workflowNodes(parseWorkflowNodes(workflowNode.path("nodes")));
        }

        // 解析Graph配置
        if (node.has("graph")) {
            JsonNode graphNode = node.get("graph");
            builder.graphStart(graphNode.path("start").asText(null));
            builder.graphNodes(parseWorkflowNodes(graphNode.path("nodes")));
            builder.graphEdges(parseGraphEdges(graphNode.path("edges")));
        }

        // 解析AgentScope配置（engine=agentscope 时生效）
        if (node.has("agentscope")) {
            JsonNode agentscopeNode = node.get("agentscope");
            builder.agentscopePipelineType(agentscopeNode.path("pipeline").asText("sequential"));
            builder.agentscopeAgents(parseAgentscopeAgentConfigs(agentscopeNode.path("agents")));
        }

        // 解析Hybrid子引擎映射（engine=hybrid 时生效）
        if (node.has("hybrid")) {
            JsonNode hybridNode = node.get("hybrid");
            // hybrid.graph 复用 graph 解析逻辑
            if (hybridNode.has("graph")) {
                JsonNode graphNode = hybridNode.get("graph");
                builder.graphStart(graphNode.path("start").asText(null));
                builder.graphNodes(parseWorkflowNodes(graphNode.path("nodes")));
                builder.graphEdges(parseGraphEdges(graphNode.path("edges")));
            }
            // subEngines 映射：nodeId → EngineType
            if (hybridNode.has("subEngines")) {
                Map<String, EngineType> subEngines = new HashMap<>();
                Iterator<Map.Entry<String, JsonNode>> fields = hybridNode.get("subEngines").fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    subEngines.put(entry.getKey(), EngineType.valueOf(entry.getValue().asText().toUpperCase()));
                }
                builder.subEngines(subEngines);
            }
        }

        // 解析MCP Server配置（跨引擎通用）
        if (node.has("mcpServers")) {
            builder.mcpServers(parseMcpServerConfigs(node.get("mcpServers")));
        }

        return builder.build();
    }

    /**
     * 解析模型配置
     */
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

    /**
     * 解析工具配置列表
     */
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

    /**
     * 解析Workflow节点列表
     */
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

    /**
     * 解析Graph边列表
     */
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

    /**
     * 解析AgentScope Agent配置列表
     */
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
            // 解析 enableTools 列表
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

    /**
     * 解析MCP Server配置列表
     */
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
     * 验证FUNCTION类型工具类名是否存在，不存在则记录错误并从tools列表移除该工具
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
     * 验证Agent定义中的agentId引用是否存在，未找到的引用记录错误日志并跳过该Agent定义
     */
    private void validateAgentReferences(AgentRegistry agentRegistry) {
        List<String> invalidAgentIds = new ArrayList<>();

        for (AgentDefinition agent : agentRegistry.getAll()) {
            // 收集该Agent引用的所有子Agent ID
            Set<String> referencedIds = new HashSet<>();

            if (agent.getWorkflowNodes() != null) {
                for (WorkflowNode node : agent.getWorkflowNodes()) {
                    if (node.getAgentId() != null && !node.getAgentId().isBlank()) {
                        referencedIds.add(node.getAgentId());
                    }
                    if (node.getReactAgentId() != null && !node.getReactAgentId().isBlank()) {
                        referencedIds.add(node.getReactAgentId());
                    }
                }
            }

            if (agent.getGraphNodes() != null) {
                for (WorkflowNode node : agent.getGraphNodes()) {
                    if (node.getAgentId() != null && !node.getAgentId().isBlank()) {
                        referencedIds.add(node.getAgentId());
                    }
                }
            }

            // 检查引用是否存在（跳过自身引用）
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

        // 从Registry中移除无效Agent
        for (String invalidId : invalidAgentIds) {
            // 注意：AgentRegistry没有remove方法，此处记录警告，后续可增加remove方法
            log.warn("Agent '{}' 存在无效的子Agent引用，建议检查配置", invalidId);
        }
    }

}
