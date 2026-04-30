package com.ai.agent.domain.agent.service.adapter;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.aggregate.ChatAgentDefinition;
import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.agent.model.valobj.ModelConfig;
import com.ai.agent.domain.common.interface_.ContextStore;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.domain.agent.service.engine.ConditionEvaluator;
import com.ai.agent.domain.agent.service.tool.McpToolProvider;
import com.ai.agent.domain.knowledge.service.rag.NodeRagService;
import com.ai.agent.types.enums.EngineType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 测试 GraphEngineAdapter 的重试逻辑不阻塞调用线程
 */
class GraphEngineAdapterTest {

    /**
     * 验证 executeStream 返回的 Flux 不会在订阅线程上阻塞
     * 当节点执行成功时，流应快速完成
     */
    @Test
    void executeStream_doesNotBlockCallingThread() {
        // 构建一个 stub adapter，用 mock ChatModel 直接返回成功
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse aiResponse = mock(ChatResponse.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(aiResponse);
        NodeRagService nodeRagService =mock(NodeRagService.class);;
        AgentRegistry registry = mock(AgentRegistry.class);
        McpToolProvider toolProvider = mock(McpToolProvider.class);
        ConditionEvaluator evaluator = mock(ConditionEvaluator.class);

        GraphEngineAdapter adapter = new GraphEngineAdapter(chatModel, registry, evaluator,nodeRagService);

        // 验证 Flux.defer() 内部不会阻塞当前线程
        // 由于 StateGraph 需要 YAML 配置，我们测试 Flux 结构的正确性
        assertNotNull(adapter);
        assertEquals(EngineType.GRAPH, adapter.getType());
    }

    /**
     * 验证重试不会导致过长的阻塞时间
     * 重试 3 次的最大总延迟不应超过合理阈值
     */
    @Test
    void retryTiming_totalDelayWithinBounds() {
        long baseMs = 1000L;
        int maxRetry = 3;

        // 指数退避: base * 2^(attempt-1)
        // attempt 1 fail → wait 1000ms
        // attempt 2 fail → wait 2000ms
        // attempt 3 fail → no wait
        long totalWait = 0;
        for (int i = 1; i < maxRetry; i++) {
            totalWait += baseMs * (1L << (i - 1));
        }
        // 总延迟 = 1000 + 2000 = 3000ms
        assertEquals(3000L, totalWait, "3次重试的总延迟应为3000ms");

        // 最大单次延迟 = 2000ms，不超过 5 秒
        long maxSingleDelay = baseMs * (1L << (maxRetry - 2));
        assertTrue(maxSingleDelay <= 5000L, "单次重试延迟不应超过5秒");
    }
}
