package com.ai.agent.domain.common.interface_;

import java.util.Map;

/**
 * 运行时上下文存储接口
 * 由 agent 上下文的 SessionContext 实现，被 chat 和 agent 的策略使用
 */
public interface ContextStore {

    String getSessionId();

    /**
     * 追加消息到对话历史
     *
     * @param senderId 发送者ID
     * @param content  消息内容
     * @param metadata 元数据（含 role 等）
     */
    void appendHistory(String senderId, String content, Map<String, Object> metadata);

    /**
     * 组装记忆上下文（供 LLM 注入使用）
     *
     * @param query 当前查询
     * @return 拼接后的记忆上下文文本
     */
    String assembleMemoryContext(String query);
}
