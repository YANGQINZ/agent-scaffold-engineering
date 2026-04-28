package com.ai.agent.domain.agent.service.engine;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.pipeline.MsgHub;
import io.agentscope.core.pipeline.Pipeline;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * AgentScope 引擎特化通道 — 暴露 agentscope-java 原生能力（扩展类）
 *
 * 开发者通过此通道访问 Pipeline 原生对象、Agent 实例、
 * 自定义 Hook 注入、MsgHub 广播模式等操作。
 *
 * 使用方式：
 *   AgentScopeChannel channel = adapter.getTypedChannel(AgentScopeChannel.class);
 *   AgentBase agent = channel.getAgent("my-agent", "agent-name");
 *   Pipeline<Msg> pipeline = channel.getPipeline();
 */
@Slf4j
public class AgentScopeChannel {

    /** 缓存的 Agent 实例（key=agentId, value=该 agentId 对应的 AgentBase 列表） */
    private final Map<String, List<AgentBase>> agentsCache;

    /** 最近一次执行的 Pipeline（延迟设置） */
    private volatile Pipeline<?> lastPipeline;

    /** MsgHub 实例（按需创建） */
    private volatile MsgHub msgHub;

    public AgentScopeChannel(Map<String, List<AgentBase>> agentsCache) {
        this.agentsCache = agentsCache;
    }

    /**
     * 获取最近一次执行的 Pipeline 对象
     *
     * @return Pipeline 实例
     * @throws IllegalStateException Pipeline 尚未执行
     */
    public Pipeline<?> getPipeline() {
        if (lastPipeline == null) {
            throw new IllegalStateException("Pipeline尚未执行，请先调用AgentScopeAdapter.execute()");
        }
        return lastPipeline;
    }

    /**
     * 设置 Pipeline 实例（由 AgentScopeAdapter 内部调用）
     */
    public void setPipeline(Pipeline<?> pipeline) {
        this.lastPipeline = pipeline;
        log.debug("AgentScopeChannel设置Pipeline: {}", pipeline.getDescription());
    }

    /**
     * 获取某个 Agent 原生实例
     *
     * @param agentId Agent ID（对应 AgentDefinition.agentId）
     * @return AgentBase 实例列表（Sequential Pipeline 下可能有多个）
     * @throws IllegalStateException 未找到对应 Agent
     */
    public List<AgentBase> getAgents(String agentId) {
        List<AgentBase> agents = agentsCache.get(agentId);
        if (agents == null || agents.isEmpty()) {
            throw new IllegalStateException("未找到Agent: agentId=" + agentId + "，请先执行Pipeline");
        }
        return agents;
    }

    /**
     * 获取指定名称的单个 Agent 实例
     *
     * @param agentId Agent ID
     * @param name    Agent 名称
     * @return 匹配名称的 AgentBase 实例
     * @throws IllegalStateException 未找到对应 Agent
     */
    public AgentBase getAgent(String agentId, String name) {
        List<AgentBase> agents = getAgents(agentId);
        return agents.stream()
                .filter(a -> name.equals(a.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "未找到Agent: agentId=" + agentId + ", name=" + name));
    }

    /**
     * 注入自定义 Hook — 通过全局 SystemHook 机制注册
     *
     * 注意：AgentBase.addSystemHook 是全局注册，影响所有 Agent 实例。
     * 如果需要针对特定 Agent 注册 Hook，应在构建 ReActAgent 时
     * 通过 .hook(hook) 方法注入（推荐方式）。
     *
     * @param hook Hook 实例
     */
    public void addSystemHook(Hook hook) {
        AgentBase.addSystemHook(hook);
        log.info("全局SystemHook已注册: hook={}", hook.getClass().getSimpleName());
    }

    /**
     * 启用 MsgHub 广播模式
     *
     * MsgHub 让参与的所有 Agent 可以广播消息，
     * 实现多 Agent 间的协作通信。
     *
     * @param agentId 参与 MsgHub 的 Agent ID
     * @return MsgHub 实例
     * @throws IllegalStateException 未找到对应 Agent
     */
    public MsgHub enableMsgHub(String agentId) {
        List<AgentBase> agents = agentsCache.get(agentId);
        if (agents == null || agents.isEmpty()) {
            throw new IllegalStateException("未找到Agent: agentId=" + agentId);
        }

        if (msgHub == null) {
            synchronized (this) {
                if (msgHub == null) {
                    msgHub = MsgHub.builder()
                            .name("msghub-" + agentId)
                            .participants(agents)
                            .enableAutoBroadcast(true)
                            .build();
                    log.info("MsgHub广播模式已启用: agentId={}, participantCount={}",
                            agentId, agents.size());
                }
            }
        }
        return msgHub;
    }

    /**
     * 获取 MsgHub 实例
     *
     * @return MsgHub 实例，未启用时返回 null
     */
    public MsgHub getMsgHub() {
        return msgHub;
    }

}
