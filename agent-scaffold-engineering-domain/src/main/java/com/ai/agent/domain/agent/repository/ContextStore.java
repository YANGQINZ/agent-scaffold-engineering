package com.ai.agent.domain.agent.repository;

import com.ai.agent.domain.agent.model.valobj.AgentMessage;

/**
 * 上下文存储抽象接口 — 会话记忆共享契约
 *
 * 所有策略（SIMPLE/MULTI_TURN/AGENT）通过同一个 ContextStore 实例
 * 追加对话历史和组装记忆上下文。
 */
public interface ContextStore {

    /**
     * 获取会话ID
     */
    String getSessionId();

    /**
     * 追加消息到对话历史
     *
     * 实现委托 MemoryFacade 写入冷层(DB) + 热层(Redis)，
     * MemoryFacade 内部触发异步记忆提取和压缩检查。
     * 写入失败时降级（不影响主流程）。
     */
    void appendHistory(AgentMessage msg);

    /**
     * 记忆上下文组装（供 LLM 注入使用）
     *
     * 委托 MemoryFacade → ContextAssembler，返回：
     * 1. 热层摘要（历史对话摘要）
     * 2. 冷层语义记忆（向量检索的相关事实）
     * 3. 热层近期消息
     *
     * @param query 当前查询
     * @return 拼接后的记忆上下文文本，失败时返回空字符串
     */
    String assembleMemoryContext(String query);

}
