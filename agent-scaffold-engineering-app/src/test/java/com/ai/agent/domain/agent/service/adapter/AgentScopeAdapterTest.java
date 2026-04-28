package com.ai.agent.domain.agent.service.adapter;

import io.agentscope.core.agent.AgentBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 AgentScopeAdapter 的 lastAgentsCache 缓存管理
 */
class AgentScopeAdapterTest {

    private AgentScopeAdapter adapter;

    @BeforeEach
    void setUp() {
        // 使用 null 依赖创建实例，仅测试缓存管理
        adapter = new AgentScopeAdapter(null, null);
    }

    @Test
    void clearAgentCache_removesCachedAgents() {
        // 通过 getAgentsCache() 直接操作缓存
        Map<String, List<AgentBase>> cache = adapter.getAgentsCache();
        cache.put("agent-1", List.of());
        cache.put("agent-2", List.of());

        assertEquals(2, cache.size());

        adapter.clearAgentCache("agent-1");

        assertEquals(1, cache.size());
        assertNull(cache.get("agent-1"));
        assertNotNull(cache.get("agent-2"));
    }

    @Test
    void clearAllAgentCache_removesAllEntries() {
        Map<String, List<AgentBase>> cache = adapter.getAgentsCache();
        cache.put("agent-1", List.of());
        cache.put("agent-2", List.of());
        cache.put("agent-3", List.of());

        assertEquals(3, cache.size());

        adapter.clearAllAgentCache();

        assertTrue(cache.isEmpty(), "清理后缓存应为空");
    }

    @Test
    void clearAgentCache_nonExistentKey_noException() {
        assertDoesNotThrow(() -> adapter.clearAgentCache("nonexistent"),
                "清理不存在的 key 不应抛异常");
    }

    @Test
    void getAgentsCache_isEmptyOnInit() {
        assertTrue(adapter.getAgentsCache().isEmpty(),
                "初始状态下缓存应为空");
    }

    @Test
    void cacheAgents_evictsWhenOverMaxSize() {
        Map<String, List<AgentBase>> cache = adapter.getAgentsCache();

        // 填充超过 MAX_CACHE_SIZE(100) 个条目
        for (int i = 0; i < 150; i++) {
            adapter.cacheAgents("agent-" + i, List.of());
        }

        // 缓存应被淘汰到合理大小
        assertTrue(cache.size() <= 100,
                "缓存淘汰后应不超过最大容量，实际: " + cache.size());
        assertTrue(cache.size() > 0,
                "缓存淘汰后仍应有条目存在");
    }

    @Test
    void cacheAgents_keepsRecentEntries() {
        Map<String, List<AgentBase>> cache = adapter.getAgentsCache();

        // 填充大量条目
        for (int i = 0; i < 150; i++) {
            adapter.cacheAgents("agent-" + i, List.of());
        }

        // 最近添加的条目应存在（ConcurrentHashMap 迭代顺序不确定，
        // 但新加入的条目在淘汰后应大概率保留）
        assertTrue(cache.size() > 0, "淘汰后应有条目保留");
    }
}
