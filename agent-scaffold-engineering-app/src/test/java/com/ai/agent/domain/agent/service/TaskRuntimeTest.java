package com.ai.agent.domain.agent.service;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.aggregate.GraphAgentDefinition;
import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.agent.service.adapter.EngineAdapter;
import com.ai.agent.domain.common.interface_.ContextStore;
import com.ai.agent.domain.common.valobj.ChatRequest;
import com.ai.agent.domain.common.valobj.ChatResponse;
import com.ai.agent.types.enums.ChatMode;
import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.exception.AgentException;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskRuntimeTest {

    /**
     * 验证 getAdapters() 不会因并发访问而多次初始化
     *
     * 当前代码存在 check-then-act 竞争条件：
     * 多线程同时看到 adapters == null，各自构建映射表。
     * 本测试通过计数 stream() 调用次数来检测这一问题。
     */
    @Test
    void getAdapters_concurrentAccess_initializesOnlyOnce() throws InterruptedException {
        AtomicInteger streamCallCount = new AtomicInteger(0);

        // 创建一个代理 List，追踪 stream() 调用次数
        EngineAdapter graphAdapter = new StubEngineAdapter(EngineType.GRAPH);
        EngineAdapter hybridAdapter = new StubEngineAdapter(EngineType.HYBRID);
        List<EngineAdapter> adapterList = new java.util.AbstractList<>() {
            private final List<EngineAdapter> delegate = List.of(graphAdapter, hybridAdapter);

            @Override
            public EngineAdapter get(int index) {
                return delegate.get(index);
            }

            @Override
            public int size() {
                return delegate.size();
            }

            @Override
            public java.util.stream.Stream<EngineAdapter> stream() {
                streamCallCount.incrementAndGet();
                return delegate.stream();
            }
        };

        TaskRuntime runtime = new TaskRuntime(null, null, adapterList);

        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    runtime.getAdapter(EngineType.GRAPH);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        doneLatch.await();

        // stream() 应仅被调用一次（初始化一次），而非多次
        assertEquals(1, streamCallCount.get(),
                "getAdapters() 应仅初始化一次，实际初始化次数: " + streamCallCount.get());
    }

    @Test
    void getAdapter_unsupportedType_throwsException() {
        EngineAdapter graphAdapter = new StubEngineAdapter(EngineType.GRAPH);
        TaskRuntime runtime = new TaskRuntime(null, null, List.of(graphAdapter));

        assertThrows(Exception.class, () -> runtime.getAdapter(EngineType.CHAT),
                "不支持的引擎类型应抛出异常");
    }

    @Test
    void getAdapter_nullType_defaultsToGraph() {
        EngineAdapter graphAdapter = new StubEngineAdapter(EngineType.GRAPH);
        TaskRuntime runtime = new TaskRuntime(null, null, List.of(graphAdapter));

        EngineAdapter result = runtime.getAdapter(null);
        assertNotNull(result, "null 引擎类型应默认路由到 GRAPH");
        assertEquals(EngineType.GRAPH, result.getType());
    }

    @Test
    void resolveAgentDefinition_withInlineDefinition_usesItDirectly() {
        GraphAgentDefinition inlineDef = GraphAgentDefinition.builder()
                .agentId("temp_123")
                .name("Temp")
                .engine(EngineType.GRAPH)
                .graphStart(List.of("start"))
                .graphNodes(List.of(WorkflowNode.builder().id("start").agentId("sub").build()))
                .build();

        ChatRequest request = ChatRequest.builder()
                .mode(ChatMode.AGENT)
                .agentDefinition(inlineDef)
                .build();

        assertNotNull(request.getAgentDefinition());
        assertEquals(EngineType.GRAPH, request.getAgentDefinition().getEngine());
    }

    @Test
    void resolveAgentDefinition_withNeither_throwsAgentNotFound() {
        ChatRequest request = ChatRequest.builder()
                .mode(ChatMode.AGENT)
                .build();

        assertNull(request.getAgentId());
        assertNull(request.getAgentDefinition());
    }

    // ═══════════════════════════════════════════════════════
    // 行为测试 — 通过 execute(ChatRequest) 测试 resolveAgentDefinition / validateInlineDefinition
    // ═══════════════════════════════════════════════════════

    /**
     * 测试1: 既没有 agentId 也没有 agentDefinition 时，execute 抛出 AGENT_NOT_FOUND
     */
    @Test
    void execute_noAgentIdNoDefinition_throwsAgentNotFound() {
        AgentRegistry mockRegistry = Mockito.mock(AgentRegistry.class);
        ContextStoreFactory mockCtxFactory = Mockito.mock(ContextStoreFactory.class);
        EngineAdapter graphAdapter = new StubEngineAdapter(EngineType.GRAPH);

        TaskRuntime runtime = new TaskRuntime(mockRegistry, mockCtxFactory, List.of(graphAdapter));

        ChatRequest request = ChatRequest.builder()
                .mode(ChatMode.AGENT)
                .userId("user1")
                .sessionId("session1")
                .query("hello")
                .build();

        AgentException ex = assertThrows(AgentException.class, () -> runtime.execute(request));
        assertEquals("1003", ex.getErrorCode(), "错误码应为 AGENT_NOT_FOUND(1003)");
    }

    /**
     * 测试2: 内联 GraphAgentDefinition（合法数据）时，execute 走到适配器并返回结果
     */
    @Test
    void execute_inlineGraphDefinition_proceedsToAdapter() {
        AgentRegistry mockRegistry = Mockito.mock(AgentRegistry.class);
        ContextStoreFactory mockCtxFactory = Mockito.mock(ContextStoreFactory.class);
        ContextStore mockCtx = Mockito.mock(ContextStore.class);

        // 构造一个返回固定内容的 EngineAdapter
        AgentMessage expectedResult = AgentMessage.builder()
                .content("graph executed")
                .metadata(Map.of("sessionId", "session1"))
                .build();
        EngineAdapter graphAdapter = new StubEngineAdapter(EngineType.GRAPH) {
            @Override
            public AgentMessage execute(AgentDefinition def, AgentMessage input, ContextStore ctx) {
                return expectedResult;
            }
        };

        when(mockCtxFactory.getOrCreate(anyString(), anyString(), anyString(),
                any(EngineType.class), anyBoolean(), any())).thenReturn(mockCtx);

        TaskRuntime runtime = new TaskRuntime(mockRegistry, mockCtxFactory, List.of(graphAdapter));

        GraphAgentDefinition inlineDef = GraphAgentDefinition.builder()
                .agentId("temp_inline")
                .name("InlineGraph")
                .engine(EngineType.GRAPH)
                .graphStart(List.of("start"))
                .graphNodes(List.of(WorkflowNode.builder().id("start").agentId("sub").build()))
                .build();

        ChatRequest request = ChatRequest.builder()
                .mode(ChatMode.AGENT)
                .userId("user1")
                .sessionId("session1")
                .query("hello")
                .engine(EngineType.GRAPH)
                .agentDefinition(inlineDef)
                .build();

        ChatResponse response = runtime.execute(request);
        assertNotNull(response, "内联定义应成功执行并返回结果");
        assertEquals("graph executed", response.getAnswer());

        // 确认 registry.get() 没有被调用（因为没有 agentId）
        verify(mockRegistry, never()).get(anyString());
        // 确认 ContextStoreFactory 被调用了
        verify(mockCtxFactory).getOrCreate(eq("session1"), eq("user1"), isNull(),
                eq(EngineType.GRAPH), eq(false), isNull());
    }

    /**
     * 测试3: 内联 GraphAgentDefinition 缺少 graphStart 时，execute 抛出异常并包含提示信息
     */
    @Test
    void execute_inlineGraphDefinitionMissingGraphStart_throwsAgentNotFound() {
        AgentRegistry mockRegistry = Mockito.mock(AgentRegistry.class);
        ContextStoreFactory mockCtxFactory = Mockito.mock(ContextStoreFactory.class);
        EngineAdapter graphAdapter = new StubEngineAdapter(EngineType.GRAPH);

        TaskRuntime runtime = new TaskRuntime(mockRegistry, mockCtxFactory, List.of(graphAdapter));

        GraphAgentDefinition inlineDef = GraphAgentDefinition.builder()
                .agentId("temp_bad")
                .name("BadGraph")
                .engine(EngineType.GRAPH)
                .graphStart(null)
                .graphNodes(List.of(WorkflowNode.builder().id("start").agentId("sub").build()))
                .build();

        ChatRequest request = ChatRequest.builder()
                .mode(ChatMode.AGENT)
                .userId("user1")
                .sessionId("session1")
                .query("hello")
                .agentDefinition(inlineDef)
                .build();

        AgentException ex = assertThrows(AgentException.class, () -> runtime.execute(request));
        assertTrue(ex.getErrorMsg().contains("GRAPH 类型 Agent 定义缺少 graphStart 字段"),
                "异常信息应包含缺少 graphStart 字段的提示");
    }

    private static class StubEngineAdapter implements EngineAdapter {
        private final EngineType type;

        StubEngineAdapter(EngineType type) {
            this.type = type;
        }

        @Override
        public EngineType getType() {
            return type;
        }

        @Override
        public com.ai.agent.domain.agent.model.valobj.AgentMessage execute(
                com.ai.agent.domain.agent.model.aggregate.AgentDefinition def,
                com.ai.agent.domain.agent.model.valobj.AgentMessage input,
                com.ai.agent.domain.common.interface_.ContextStore ctx) {
            return null;
        }

        @Override
        public reactor.core.publisher.Flux<com.ai.agent.domain.common.valobj.StreamEvent> executeStream(
                com.ai.agent.domain.agent.model.aggregate.AgentDefinition def,
                com.ai.agent.domain.agent.model.valobj.AgentMessage input,
                com.ai.agent.domain.common.interface_.ContextStore ctx) {
            return reactor.core.publisher.Flux.empty();
        }

    }
}
