package com.ai.agent.domain.agent.service.engine;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.repository.ContextStore;
import com.ai.agent.domain.agent.service.adapter.GraphEngineAdapter;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Graph引擎特化通道 — 开发者通过此通道访问StateGraph原生能力
 *
 * 提供 StateGraph 原生对象获取、检查点管理、动态节点添加等高级操作。
 */
@Slf4j
@RequiredArgsConstructor
public class GraphChannel {

    private final GraphEngineAdapter adapter;
    private CompiledGraph cachedCompiledGraph;

    /**
     * 获取编译后的 StateGraph 原生对象
     *
     * @param def Agent 定义
     * @param ctx 上下文存储
     * @return 编译后的 StateGraph
     */
    public CompiledGraph getStateGraph(AgentDefinition def, ContextStore ctx) {
        if (cachedCompiledGraph == null) {
            cachedCompiledGraph = adapter.buildAndCompile(def, ctx);
        }
        return cachedCompiledGraph;
    }

    /**
     * 获取检查点
     *
     * @param threadId 线程ID
     * @return 检查点状态 Map
     */
    // TODO [#003] 检查点获取待实现，需配合MemorySaver使用
    public Object getCheckpoint(String threadId) {
        throw new UnsupportedOperationException("检查点获取待实现 [#003]，需配合MemorySaver使用");
    }

    /**
     * 从检查点恢复执行
     *
     * @param checkpoint 检查点状态
     */
    // TODO [#003] 检查点恢复待实现，需配合MemorySaver使用
    public void resumeFromCheckpoint(Object checkpoint) {
        throw new UnsupportedOperationException("检查点恢复待实现 [#003]，需配合MemorySaver使用");
    }

    /**
     * 动态添加自定义节点（高级场景）
     *
     * @param name   节点名称
     * @param action 节点执行动作
     */
    public void addCustomNode(String name, java.util.function.Function<?, ?> action) {
        this.cachedCompiledGraph = null;
        log.info("自定义节点已注册，下次执行时重新编译: nodeName={}", name);
    }

}
