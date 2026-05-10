package com.ai.agent.config;

import com.ai.agent.domain.agent.model.aggregate.*;
import com.ai.agent.domain.agent.model.entity.AgentscopeAgentConfig;
import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.McpServerConfig;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.ModelConfig;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.types.enums.EngineType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.util.*;

/**
 * Agent配置加载器 - 从 classpath:agents 目录下加载YAML Agent定义并注册到AgentRegistry
 * <p>
 * 加载顺序: YAML加载(ApplicationRunner order=1) -> 数据库加载(ApplicationRunner order=2).
 * 同一agentId数据库定义覆盖YAML定义(数据库优先).
 * <p>
 * 支持四种引擎类型: CHAT, GRAPH, AGENTSCOPE, HYBRID.
 * 解析错误记录WARN日志并跳过, 不阻止应用启动.
 */
@Slf4j
@Configuration
public class AgentYamlLoader implements ApplicationRunner, Ordered {

    private final AgentRegistry agentRegistry;
    private final ObjectMapper yamlObjectMapper;

    public AgentYamlLoader(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
        this.yamlObjectMapper = new ObjectMapper(new YAMLFactory());
    }

    @Override
    public int getOrder() {
        return 1;
    }

    /**
     * 应用启动后从YAML文件加载Agent定义
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            loadAgentDefinitions();
        } catch (Exception e) {
            log.warn("AgentYamlLoader加载失败,不影响应用启动: {}", e.getMessage());
        }
    }

    /**
     * 扫描classpath:agents目录下的YAML文件, 解析并注册Agent定义
     */
    void loadAgentDefinitions() {
        log.info("AgentYamlLoader开始扫描YAML Agent定义...");
        Resource[] resources = scanYamlResources();
        if (resources.length == 0) {
            log.info("未发现YAML Agent定义文件");
            return;
        }

        int loaded = 0;
        int skipped = 0;
        for (Resource resource : resources) {
            try {
                Map<String, Object> yamlMap = parseYaml(resource);
                if (yamlMap == null || !yamlMap.containsKey("agent")) {
                    log.warn("跳过YAML文件(缺少agent节点): {}", resource.getFilename());
                    skipped++;
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> agentMap = (Map<String, Object>) yamlMap.get("agent");

                if (!validateAgentDefinition(agentMap, resource.getFilename())) {
                    skipped++;
                    continue;
                }

                AgentDefinition definition = buildAgentDefinition(agentMap);
                agentRegistry.register(definition);
                log.info("从YAML加载Agent定义: agentId={}, name={}, engine={}",
                        definition.getAgentId(), definition.getName(), definition.getEngine());
                loaded++;
            } catch (Exception e) {
                log.warn("解析YAML文件失败,跳过: {} - {}", resource.getFilename(), e.getMessage());
                skipped++;
            }
        }
        log.info("AgentYamlLoader完成: 加载{}个,跳过{}个,共扫描{}个文件", loaded, skipped, resources.length);
    }

    /**
     * 扫描classpath:agents/下的YAML资源文件
     */
    Resource[] scanYamlResources() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            return resolver.getResources("classpath:agents/**/*.yaml");
        } catch (Exception e) {
            log.warn("扫描YAML资源文件失败: {}", e.getMessage());
            return new Resource[0];
        }
    }

    /**
     * 解析单个YAML文件为Map结构
     *
     * @param resource YAML资源文件
     * @return 解析后的Map,解析失败返回null
     */
    Map<String, Object> parseYaml(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            return yamlObjectMapper.readValue(is, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("YAML文件解析失败: {} - {}", resource.getFilename(), e.getMessage());
            return null;
        }
    }

    /**
     * 校验Agent定义必填字段
     *
     * @param agentMap Agent定义Map
     * @param filename 文件名(用于日志)
     * @return 校验通过返回true
     */
    boolean validateAgentDefinition(Map<String, Object> agentMap, String filename) {
        String agentId = getStringValue(agentMap, "id");
        if (agentId == null || agentId.isBlank()) {
            log.warn("YAML文件缺少必填字段agent.id: {}", filename);
            return false;
        }

        String name = getStringValue(agentMap, "name");
        if (name == null || name.isBlank()) {
            log.warn("YAML文件缺少必填字段agent.name: {}", filename);
            return false;
        }

        String engineStr = getStringValue(agentMap, "engine");
        if (engineStr == null || engineStr.isBlank()) {
            log.warn("YAML文件缺少必填字段agent.engine: {}", filename);
            return false;
        }

        try {
            EngineType.valueOf(engineStr);
        } catch (IllegalArgumentException e) {
            log.warn("YAML文件engine值非法: {} (文件: {})", engineStr, filename);
            return false;
        }

        return true;
    }

    /**
     * 根据engine类型分发构建AgentDefinition子类
     */
    AgentDefinition buildAgentDefinition(Map<String, Object> agentMap) {
        String agentId = getStringValue(agentMap, "id");
        String name = getStringValue(agentMap, "name");
        String instruction = getStringValue(agentMap, "instruction");
        EngineType engine = EngineType.valueOf(getStringValue(agentMap, "engine"));

        @SuppressWarnings("unchecked")
        Map<String, Object> modelMap = (Map<String, Object>) agentMap.get("model");
        ModelConfig modelConfig = buildModelConfig(modelMap);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mcpList = (List<Map<String, Object>>) agentMap.get("mcpServers");
        List<McpServerConfig> mcpServers = buildMcpServers(mcpList);

        return switch (engine) {
            case CHAT -> buildChatDefinition(agentId, name, instruction, modelConfig, mcpServers);
            case GRAPH -> buildGraphDefinition(agentId, name, instruction, modelConfig, mcpServers, agentMap);
            case AGENTSCOPE -> buildAgentscopeDefinition(agentId, name, instruction, modelConfig, mcpServers, agentMap);
            case HYBRID -> buildHybridDefinition(agentId, name, instruction, modelConfig, mcpServers, agentMap);
        };
    }

    // ═══════════════════════════════════════════════════════
    // 通用字段构建
    // ═══════════════════════════════════════════════════════

    /**
     * 构建 ModelConfig 值对象
     */
    ModelConfig buildModelConfig(Map<String, Object> modelMap) {
        if (modelMap == null) {
            return new ModelConfig();
        }
        return ModelConfig.builder()
                .name(getStringValue(modelMap, "name", "qwq-plus"))
                .temperature(getDoubleValue(modelMap, "temperature", 0.7))
                .maxTokens(getIntValue(modelMap, "maxTokens", 2000))
                .build();
    }

    /**
     * 构建 MCP Server 配置列表
     */
    List<McpServerConfig> buildMcpServers(List<Map<String, Object>> mcpList) {
        if (mcpList == null || mcpList.isEmpty()) {
            return List.of();
        }
        return mcpList.stream().map(this::buildMcpServerConfig).toList();
    }

    /**
     * 构建单个 MCP Server 配置
     */
    private McpServerConfig buildMcpServerConfig(Map<String, Object> map) {
        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) map.get("args");
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) map.get("headers");

        return McpServerConfig.builder()
                .name(getStringValue(map, "name"))
                .transport(getStringValue(map, "transport"))
                .command(getStringValue(map, "command"))
                .args(args != null ? args : List.of())
                .url(getStringValue(map, "url"))
                .headers(headers != null ? headers : Map.of())
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // CHAT 引擎
    // ═══════════════════════════════════════════════════════

    /**
     * 构建 ChatAgentDefinition
     */
    ChatAgentDefinition buildChatDefinition(String agentId, String name, String instruction,
                                             ModelConfig modelConfig, List<McpServerConfig> mcpServers) {
        return ChatAgentDefinition.builder()
                .agentId(agentId)
                .name(name)
                .instruction(instruction)
                .modelConfig(modelConfig)
                .mcpServers(mcpServers)
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // GRAPH 引擎
    // ═══════════════════════════════════════════════════════

    /**
     * 构建 GraphAgentDefinition
     */
    GraphAgentDefinition buildGraphDefinition(String agentId, String name, String instruction,
                                               ModelConfig modelConfig, List<McpServerConfig> mcpServers,
                                               Map<String, Object> agentMap) {
        @SuppressWarnings("unchecked")
        Map<String, Object> graphMap = (Map<String, Object>) agentMap.get("graph");

        List<String> graphStart = List.of();
        List<WorkflowNode> nodes = List.of();
        List<GraphEdge> edges = List.of();

        if (graphMap != null) {
            @SuppressWarnings("unchecked")
            List<String> startList = (List<String>) graphMap.get("start");
            graphStart = startList != null ? startList : List.of();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodeList = (List<Map<String, Object>>) graphMap.get("nodes");
            nodes = buildWorkflowNodes(nodeList);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> edgeList = (List<Map<String, Object>>) graphMap.get("edges");
            edges = buildGraphEdges(edgeList);
        }

        return GraphAgentDefinition.builder()
                .agentId(agentId)
                .name(name)
                .instruction(instruction)
                .modelConfig(modelConfig)
                .mcpServers(mcpServers)
                .graphStart(graphStart)
                .graphNodes(nodes)
                .graphEdges(edges)
                .build();
    }

    /**
     * 构建 WorkflowNode 列表
     */
    List<WorkflowNode> buildWorkflowNodes(List<Map<String, Object>> nodeList) {
        if (nodeList == null || nodeList.isEmpty()) {
            return List.of();
        }
        return nodeList.stream().map(this::buildWorkflowNode).toList();
    }

    /**
     * 构建单个 WorkflowNode
     */
    private WorkflowNode buildWorkflowNode(Map<String, Object> map) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mcpList = (List<Map<String, Object>>) map.get("mcpServers");

        return WorkflowNode.builder()
                .id(getStringValue(map, "id"))
                .agentId(getStringValue(map, "agentId"))
                .ragEnabled(getBooleanValue(map, "ragEnabled", false))
                .knowledgeBaseId(getStringValue(map, "knowledgeBaseId"))
                .instruction(getStringValue(map, "instruction"))
                .subEngine(getStringValue(map, "subEngine"))
                .mcpServers(buildMcpServers(mcpList))
                .build();
    }

    /**
     * 构建 GraphEdge 列表
     */
    List<GraphEdge> buildGraphEdges(List<Map<String, Object>> edgeList) {
        if (edgeList == null || edgeList.isEmpty()) {
            return List.of();
        }
        return edgeList.stream().map(this::buildGraphEdge).toList();
    }

    /**
     * 构建单个 GraphEdge
     */
    private GraphEdge buildGraphEdge(Map<String, Object> map) {
        return GraphEdge.builder()
                .from(getStringValue(map, "from"))
                .to(getStringValue(map, "to"))
                .condition(getStringValue(map, "condition"))
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // AGENTSCOPE 引擎
    // ═══════════════════════════════════════════════════════

    /**
     * 构建 AgentscopeAgentDefinition
     */
    AgentscopeAgentDefinition buildAgentscopeDefinition(String agentId, String name, String instruction,
                                                         ModelConfig modelConfig, List<McpServerConfig> mcpServers,
                                                         Map<String, Object> agentMap) {
        @SuppressWarnings("unchecked")
        Map<String, Object> agentscopeMap = (Map<String, Object>) agentMap.get("agentscope");

        String pipelineType = "sequential";
        List<AgentscopeAgentConfig> agents = List.of();

        if (agentscopeMap != null) {
            pipelineType = getStringValue(agentscopeMap, "pipeline", "sequential");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> agentList = (List<Map<String, Object>>) agentscopeMap.get("agents");
            agents = buildAgentscopeAgents(agentList);
        }

        return AgentscopeAgentDefinition.builder()
                .agentId(agentId)
                .name(name)
                .instruction(instruction)
                .modelConfig(modelConfig)
                .mcpServers(mcpServers)
                .agentscopePipelineType(pipelineType)
                .agentscopeAgents(agents)
                .build();
    }

    /**
     * 构建 AgentscopeAgentConfig 列表
     */
    List<AgentscopeAgentConfig> buildAgentscopeAgents(List<Map<String, Object>> agentList) {
        if (agentList == null || agentList.isEmpty()) {
            return List.of();
        }
        return agentList.stream().map(this::buildAgentscopeAgentConfig).toList();
    }

    /**
     * 构建单个 AgentscopeAgentConfig
     */
    private AgentscopeAgentConfig buildAgentscopeAgentConfig(Map<String, Object> map) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mcpList = (List<Map<String, Object>>) map.get("mcpServers");
        @SuppressWarnings("unchecked")
        List<String> enableTools = (List<String>) map.get("enableTools");

        return AgentscopeAgentConfig.builder()
                .agentId(getStringValue(map, "agentId"))
                .name(getStringValue(map, "name"))
                .instruction(getStringValue(map, "instruction"))
                .mcpServers(buildMcpServers(mcpList))
                .enableTools(enableTools != null ? enableTools : List.of())
                .outputKey(getStringValue(map, "outputKey"))
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // HYBRID 引擎
    // ═══════════════════════════════════════════════════════

    /**
     * 构建 HybridAgentDefinition
     */
    HybridAgentDefinition buildHybridDefinition(String agentId, String name, String instruction,
                                                 ModelConfig modelConfig, List<McpServerConfig> mcpServers,
                                                 Map<String, Object> agentMap) {
        // 复用GRAPH的graph结构解析
        @SuppressWarnings("unchecked")
        Map<String, Object> graphMap = (Map<String, Object>) agentMap.get("graph");

        List<String> graphStart = List.of();
        List<WorkflowNode> nodes = List.of();
        List<GraphEdge> edges = List.of();

        if (graphMap != null) {
            @SuppressWarnings("unchecked")
            List<String> startList = (List<String>) graphMap.get("start");
            graphStart = startList != null ? startList : List.of();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodeList = (List<Map<String, Object>>) graphMap.get("nodes");
            nodes = buildWorkflowNodes(nodeList);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> edgeList = (List<Map<String, Object>>) graphMap.get("edges");
            edges = buildGraphEdges(edgeList);
        }

        // 解析subEngines映射
        @SuppressWarnings("unchecked")
        Map<String, String> subEnginesRaw = (Map<String, String>) agentMap.get("subEngines");
        Map<String, EngineType> subEngines = new LinkedHashMap<>();
        if (subEnginesRaw != null) {
            subEnginesRaw.forEach((nodeId, engineStr) -> {
                try {
                    subEngines.put(nodeId, EngineType.valueOf(engineStr));
                } catch (IllegalArgumentException e) {
                    log.warn("subEngines中engine值非法: nodeId={}, engine={}", nodeId, engineStr);
                }
            });
        }

        return HybridAgentDefinition.builder()
                .agentId(agentId)
                .name(name)
                .instruction(instruction)
                .modelConfig(modelConfig)
                .mcpServers(mcpServers)
                .graphStart(graphStart)
                .graphNodes(nodes)
                .graphEdges(edges)
                .subEngines(subEngines)
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════

    /**
     * 从Map中获取字符串值
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 从Map中获取字符串值,带默认值
     */
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        String value = getStringValue(map, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 从Map中获取double值,带默认值
     */
    private Double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        return defaultValue;
    }

    /**
     * 从Map中获取int值,带默认值
     */
    private Integer getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number num) {
            return num.intValue();
        }
        return defaultValue;
    }

    /**
     * 从Map中获取boolean值,带默认值
     */
    private Boolean getBooleanValue(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return defaultValue;
    }

}
