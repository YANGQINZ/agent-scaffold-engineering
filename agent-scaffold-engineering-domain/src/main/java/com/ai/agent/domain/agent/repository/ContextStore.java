package com.ai.agent.domain.agent.repository;

import com.ai.agent.domain.agent.model.valobj.AgentMessage;

import java.util.List;

/**
 * 上下文存储抽象接口 — 跨引擎状态共享契约
 *
 * Graph 节点和 AgentScope Agent 通过同一个 ContextStore 实例
 * 读写对话历史和键值状态，解决跨引擎状态丢失的问题。
 */
public interface ContextStore {

    /**
     * 存储键值状态（跨引擎共享中间结果）
     *
     * @param key   键名
     * @param value 值
     */
    void put(String key, Object value);

    /**
     * 获取键值状态（类型安全）
     *
     * @param key  键名
     * @param type 期望类型
     * @return 对应的值，不存在或类型不匹配时返回 null
     */
    <T> T get(String key, Class<T> type);

    /**
     * 清除所有状态和历史
     */
    void clear();

    /**
     * 获取会话ID
     *
     * @return 会话ID
     */
    String getSessionId();

    /**
     * 获取跨引擎对话历史（只读视图）
     *
     * @return 不可修改的消息列表
     */
    List<AgentMessage> getHistory();

    /**
     * 追加消息到对话历史
     *
     * 实现必须同时：写入内存历史 + 委托长期记忆（如 MemoryFacade）
     * 长期记忆写入失败时降级（不影响主流程）
     *
     * @param msg 要追加的消息
     */
    void appendHistory(AgentMessage msg);

    /**
     * 构建对话历史的文本摘要（供 LLM 上下文注入）
     *
     * @return 格式为 "role: content\n" 的历史文本
     */
    default String buildHistoryText() {
        List<AgentMessage> history = getHistory();
        if (history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentMessage msg : history) {
            Object roleObj = msg.getMetadataValue("role");
            String role = roleObj != null ? roleObj.toString() : "unknown";
            sb.append(role).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 记忆上下文组装（供 Agent 使用）
     *
     * 默认返回空字符串，SessionContext 等具备记忆能力的实现可覆写此方法，
     * 委托 MemoryFacade 返回摘要 + 语义记忆 + 近期消息。
     * 适配器通过接口调用即可，无需 instanceof 检查。
     *
     * @param query 当前查询
     * @return 拼接后的记忆上下文文本，不支持时返回空字符串
     */
    default String assembleMemoryContext(String query) {
        return "";
    }

}
