package com.ai.agent.domain.agent.service;

import com.ai.agent.domain.agent.model.aggregate.ChatAgentDefinition;
import com.ai.agent.domain.agent.model.valobj.ModelConfig;
import com.ai.agent.types.enums.EngineType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentRegistryTest {

    private AgentRegistry registry = new AgentRegistry();

    private ChatAgentDefinition createDefinition(String id) {
        return ChatAgentDefinition.builder()
                .agentId(id)
                .name("test-" + id)
                .engine(EngineType.CHAT)
                .instruction("test")
                .modelConfig(new ModelConfig())
                .build();
    }

    @Test
    void register_andGet_worksCorrectly() {
        ChatAgentDefinition def = createDefinition("agent1");
        registry.register(def);
        assertNotNull(registry.get("agent1"));
        assertEquals("agent1", registry.get("agent1").getAgentId());
    }

    @Test
    void unregister_removesAgentFromRegistry() {
        ChatAgentDefinition def = createDefinition("agent1");
        registry.register(def);
        assertNotNull(registry.get("agent1"));

        registry.unregister("agent1");
        assertNull(registry.get("agent1"), "注销后应返回 null");
    }

    @Test
    void unregister_nonExistentId_noException() {
        assertDoesNotThrow(() -> registry.unregister("nonexistent"),
                "注销不存在的 agentId 不应抛异常");
    }

    @Test
    void getAll_returnsAllRegistered() {
        registry.register(createDefinition("a1"));
        registry.register(createDefinition("a2"));
        assertEquals(2, registry.getAll().size());
    }

    @Test
    void getAll_afterUnregister_returnsRemaining() {
        registry.register(createDefinition("a1"));
        registry.register(createDefinition("a2"));
        registry.unregister("a1");
        assertEquals(1, registry.getAll().size());
        assertNull(registry.get("a1"));
        assertNotNull(registry.get("a2"));
    }
}
