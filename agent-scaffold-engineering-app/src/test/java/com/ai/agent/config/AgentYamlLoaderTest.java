package com.ai.agent.config;

import com.ai.agent.domain.agent.model.aggregate.*;
import com.ai.agent.domain.agent.model.entity.AgentscopeAgentConfig;
import com.ai.agent.domain.agent.model.entity.GraphEdge;
import com.ai.agent.domain.agent.model.entity.McpServerConfig;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.ModelConfig;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.types.enums.EngineType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentYamlLoader 单元测试 - 验证YAML解析逻辑
 */
class AgentYamlLoaderTest {

    private AgentYamlLoader loader;
    private AgentRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry(null);
        loader = new AgentYamlLoader(registry);
    }

    /**
     * 构建可含null值的Map (Map.of不支持null值)
     */
    private Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    // ═══════════════════════════════════════════════════════
    // CHAT 引擎
    // ═══════════════════════════════════════════════════════

    @Test
    void buildChatDefinition_parsesCorrectly() {
        Map<String, Object> agentMap = mapOf(
                "id", "simple-assistant",
                "name", "简单助手",
                "engine", "CHAT",
                "instruction", "你是一个有用的助手",
                "model", Map.of("name", "qwen-plus", "temperature", 0.7, "maxTokens", 2000),
                "mcpServers", List.of()
        );

        AgentDefinition def = loader.buildAgentDefinition(agentMap);

        assertInstanceOf(ChatAgentDefinition.class, def);
        assertEquals("simple-assistant", def.getAgentId());
        assertEquals("简单助手", def.getName());
        assertEquals(EngineType.CHAT, def.getEngine());
        assertEquals("你是一个有用的助手", def.getInstruction());
        assertNotNull(def.getModelConfig());
        assertEquals("qwen-plus", def.getModelConfig().getName());
    }

    // ═══════════════════════════════════════════════════════
    // GRAPH 引擎
    // ═══════════════════════════════════════════════════════

    @Test
    void buildGraphDefinition_parsesCorrectly() {
        Map<String, Object> node1 = mapOf(
                "id", "triage", "agentId", "tech-support",
                "ragEnabled", false, "knowledgeBaseId", null,
                "instruction", "分类器", "subEngine", null, "mcpServers", List.of()
        );
        Map<String, Object> node2 = mapOf(
                "id", "resolver", "agentId", "tech-support",
                "ragEnabled", false, "knowledgeBaseId", null,
                "instruction", "汇总", "subEngine", null, "mcpServers", List.of()
        );
        Map<String, Object> agentMap = mapOf(
                "id", "tech-support",
                "name", "技术支持系统",
                "engine", "GRAPH",
                "instruction", "你是技术支持系统",
                "model", Map.of("name", "qwq-plus", "temperature", 0.3, "maxTokens", 2000),
                "mcpServers", List.of(),
                "graph", Map.of(
                        "start", List.of("triage"),
                        "nodes", List.of(node1, node2),
                        "edges", List.of(
                                Map.of("from", "triage", "to", "resolver", "condition", "type==bug")
                        )
                )
        );

        AgentDefinition def = loader.buildAgentDefinition(agentMap);

        assertInstanceOf(GraphAgentDefinition.class, def);
        GraphAgentDefinition graphDef = (GraphAgentDefinition) def;
        assertEquals("tech-support", graphDef.getAgentId());
        assertEquals(EngineType.GRAPH, graphDef.getEngine());
        assertEquals(List.of("triage"), graphDef.getGraphStart());
        assertEquals(2, graphDef.getGraphNodes().size());
        assertEquals("triage", graphDef.getGraphNodes().get(0).getId());
        assertEquals("resolver", graphDef.getGraphNodes().get(1).getId());
        assertEquals(1, graphDef.getGraphEdges().size());
        assertEquals("triage", graphDef.getGraphEdges().get(0).getFrom());
        assertEquals("resolver", graphDef.getGraphEdges().get(0).getTo());
        assertEquals("type==bug", graphDef.getGraphEdges().get(0).getCondition());
    }

    // ═══════════════════════════════════════════════════════
    // AGENTSCOPE 引擎
    // ═══════════════════════════════════════════════════════

    @Test
    void buildAgentscopeDefinition_parsesCorrectly() {
        Map<String, Object> agent1 = mapOf(
                "agentId", "info-collector", "name", "信息收集员",
                "instruction", "收集信息",
                "mcpServers", List.of(), "enableTools", List.of(), "outputKey", "collected_info"
        );
        Map<String, Object> agent2 = mapOf(
                "agentId", "plan-generator", "name", "行程生成员",
                "instruction", null,
                "mcpServers", List.of(), "enableTools", List.of("search"), "outputKey", "travel_plan"
        );
        Map<String, Object> agentMap = mapOf(
                "id", "travel-planner",
                "name", "旅行规划团队",
                "engine", "AGENTSCOPE",
                "instruction", "旅行规划",
                "mcpServers", List.of(),
                "agentscope", Map.of(
                        "pipeline", "sequential",
                        "agents", List.of(agent1, agent2)
                )
        );

        AgentDefinition def = loader.buildAgentDefinition(agentMap);

        assertInstanceOf(AgentscopeAgentDefinition.class, def);
        AgentscopeAgentDefinition asDef = (AgentscopeAgentDefinition) def;
        assertEquals("travel-planner", asDef.getAgentId());
        assertEquals(EngineType.AGENTSCOPE, asDef.getEngine());
        assertEquals("sequential", asDef.getAgentscopePipelineType());
        assertEquals(2, asDef.getAgentscopeAgents().size());
        assertEquals("info-collector", asDef.getAgentscopeAgents().get(0).getAgentId());
        assertEquals("collected_info", asDef.getAgentscopeAgents().get(0).getOutputKey());
        assertEquals(List.of("search"), asDef.getAgentscopeAgents().get(1).getEnableTools());
    }

    // ═══════════════════════════════════════════════════════
    // HYBRID 引擎
    // ═══════════════════════════════════════════════════════

    @Test
    void buildHybridDefinition_parsesCorrectly() {
        Map<String, Object> node1 = mapOf(
                "id", "analyst", "agentId", "hybrid-agent",
                "ragEnabled", false, "knowledgeBaseId", null,
                "instruction", "分析", "subEngine", "GRAPH", "mcpServers", List.of()
        );
        Map<String, Object> agentMap = mapOf(
                "id", "hybrid-agent",
                "name", "混合引擎",
                "engine", "HYBRID",
                "instruction", "混合引擎测试",
                "mcpServers", List.of(),
                "graph", Map.of(
                        "start", List.of("analyst"),
                        "nodes", List.of(node1),
                        "edges", List.of(
                                mapOf("from", "analyst", "to", "researcher", "condition", null)
                        )
                ),
                "subEngines", Map.of(
                        "analyst", "GRAPH",
                        "researcher", "AGENTSCOPE"
                )
        );

        AgentDefinition def = loader.buildAgentDefinition(agentMap);

        assertInstanceOf(HybridAgentDefinition.class, def);
        HybridAgentDefinition hybridDef = (HybridAgentDefinition) def;
        assertEquals("hybrid-agent", hybridDef.getAgentId());
        assertEquals(EngineType.HYBRID, hybridDef.getEngine());
        assertEquals(List.of("analyst"), hybridDef.getGraphStart());
        assertEquals(1, hybridDef.getGraphNodes().size());
        assertEquals(EngineType.GRAPH, hybridDef.getSubEngines().get("analyst"));
        assertEquals(EngineType.AGENTSCOPE, hybridDef.getSubEngines().get("researcher"));
    }

    // ═══════════════════════════════════════════════════════
    // 校验
    // ═══════════════════════════════════════════════════════

    @Test
    void validate_missingAgentId_returnsFalse() {
        Map<String, Object> agentMap = mapOf("name", "test", "engine", "CHAT");
        assertFalse(loader.validateAgentDefinition(agentMap, "test.yaml"));
    }

    @Test
    void validate_missingName_returnsFalse() {
        Map<String, Object> agentMap = mapOf("id", "test-id", "engine", "CHAT");
        assertFalse(loader.validateAgentDefinition(agentMap, "test.yaml"));
    }

    @Test
    void validate_invalidEngine_returnsFalse() {
        Map<String, Object> agentMap = mapOf("id", "test-id", "name", "test", "engine", "INVALID");
        assertFalse(loader.validateAgentDefinition(agentMap, "test.yaml"));
    }

    @Test
    void validate_validDefinition_returnsTrue() {
        Map<String, Object> agentMap = mapOf("id", "test-id", "name", "test", "engine", "CHAT");
        assertTrue(loader.validateAgentDefinition(agentMap, "test.yaml"));
    }

    // ═══════════════════════════════════════════════════════
    // ModelConfig 默认值
    // ═══════════════════════════════════════════════════════

    @Test
    void buildModelConfig_nullMap_returnsDefaults() {
        ModelConfig config = loader.buildModelConfig(null);
        assertEquals("qwq-plus", config.getName());
        assertEquals(0.7, config.getTemperature());
        assertEquals(2000, config.getMaxTokens());
    }

    @Test
    void buildModelConfig_partialMap_returnsPartialDefaults() {
        Map<String, Object> modelMap = Map.of("name", "custom-model");
        ModelConfig config = loader.buildModelConfig(modelMap);
        assertEquals("custom-model", config.getName());
        assertEquals(0.7, config.getTemperature());
        assertEquals(2000, config.getMaxTokens());
    }

    // ═══════════════════════════════════════════════════════
    // McpServerConfig
    // ═══════════════════════════════════════════════════════

    @Test
    void buildMcpServers_nullList_returnsEmpty() {
        List<McpServerConfig> result = loader.buildMcpServers(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void buildMcpServers_withEntries_parsesCorrectly() {
        List<Map<String, Object>> mcpList = List.of(
                Map.of(
                        "name", "web-search",
                        "transport", "stdio",
                        "command", "npx",
                        "args", List.of("-y", "@anthropic/mcp-server-websearch")
                )
        );
        List<McpServerConfig> result = loader.buildMcpServers(mcpList);
        assertEquals(1, result.size());
        assertEquals("web-search", result.get(0).getName());
        assertEquals("stdio", result.get(0).getTransport());
        assertEquals("npx", result.get(0).getCommand());
        assertEquals(2, result.get(0).getArgs().size());
    }

    // ═══════════════════════════════════════════════════════
    // GRAPH 缺失字段处理
    // ═══════════════════════════════════════════════════════

    @Test
    void buildGraphDefinition_noGraphSection_returnsEmptyLists() {
        Map<String, Object> agentMap = mapOf(
                "id", "graph-no-structure",
                "name", "无图结构",
                "engine", "GRAPH",
                "mcpServers", List.of()
        );

        AgentDefinition def = loader.buildAgentDefinition(agentMap);

        assertInstanceOf(GraphAgentDefinition.class, def);
        GraphAgentDefinition graphDef = (GraphAgentDefinition) def;
        assertTrue(graphDef.getGraphStart().isEmpty());
        assertTrue(graphDef.getGraphNodes().isEmpty());
        assertTrue(graphDef.getGraphEdges().isEmpty());
    }

}
