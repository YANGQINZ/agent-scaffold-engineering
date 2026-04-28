package com.ai.agent.domain.agent.service.adapter;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.common.interface_.ContextStore;
import com.ai.agent.domain.common.valobj.StreamEvent;
import com.ai.agent.types.enums.EngineType;
import reactor.core.publisher.Flux;

/**
 * 引擎适配器接口 — 双引擎架构的核心抽象
 *
 * TaskRuntime 通过此接口与具体引擎解耦，
 * 三个适配器（Graph/AgentScope/Hybrid）各自实现。
 */
public interface EngineAdapter {

    /**
     * 返回引擎类型
     *
     * @return 引擎类型枚举
     */
    EngineType getType();

    /**
     * 同步执行引擎任务
     *
     * @param def   Agent 定义（含引擎配置、节点、边等）
     * @param input 输入消息
     * @param ctx   上下文存储（跨引擎共享）
     * @return 执行结果消息
     */
    AgentMessage execute(AgentDefinition def, AgentMessage input, ContextStore ctx);

    /**
     * 流式执行引擎任务
     *
     * @param def   Agent 定义
     * @param input 输入消息
     * @param ctx   上下文存储
     * @return 流式事件流
     */
    Flux<StreamEvent> executeStream(AgentDefinition def, AgentMessage input, ContextStore ctx);

    /**
     * 获取引擎特化通道
     *
     * @param channelType 通道类型（GraphChannel/AgentScopeChannel/HybridChannel）
     * @return 通道实例
     */
    <T> T getTypedChannel(Class<T> channelType);

}
