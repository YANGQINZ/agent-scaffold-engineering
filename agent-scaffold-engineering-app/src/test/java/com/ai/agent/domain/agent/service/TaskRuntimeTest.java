package com.ai.agent.domain.agent.service;

import com.ai.agent.domain.agent.service.adapter.EngineAdapter;
import com.ai.agent.types.enums.EngineType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

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
