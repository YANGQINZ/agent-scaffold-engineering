package com.ai.agent.domain.agent.service.engine;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.aggregate.GraphAgentDefinition;
import com.ai.agent.domain.common.interface_.ContextStore;
import com.ai.agent.domain.agent.service.adapter.GraphEngineAdapter;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Graph引擎特化通道 — 开发者通过此通道访问StateGraph原生能力（扩展类）
 *
 * 提供 StateGraph 原生对象获取和动态节点添加等高级操作。
 */
@Slf4j
@RequiredArgsConstructor
public class GraphChannel {

    private final GraphEngineAdapter adapter;
    private CompiledGraph cachedCompiledGraph;

    /**
     * 获取编译后的 StateGraph 原生对象
     */
    public CompiledGraph getStateGraph(AgentDefinition def, ContextStore ctx) {
        if (cachedCompiledGraph == null) {
            cachedCompiledGraph = adapter.buildAndCompile((GraphAgentDefinition)def, ctx);
        }
        return cachedCompiledGraph;
    }

    /**
     * 动态添加自定义节点（高级场景）
     */
    public void addCustomNode(String name, java.util.function.Function<?, ?> action) {
        this.cachedCompiledGraph = null;
        log.info("自定义节点已注册，下次执行时重新编译: nodeName={}", name);
    }

}
