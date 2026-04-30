package com.ai.agent.domain.agent.repository;

import com.ai.agent.domain.agent.model.aggregate.SessionContext;

import java.util.List;

/**
 * 会话仓储接口 — SessionContext 的持久化契约
 *
 * 与 IChatSessionRepository 互补：IChatSessionRepository 负责 ChatSession（chat 域），
 * ISessionRepository 负责 SessionContext（agent 域）。
 * 基础设施层实现同时实现两个接口，复用同一数据源。
 */
public interface ISessionRepository {

    /**
     * 保存会话上下文
     */
    void save(SessionContext session);

    /**
     * 根据会话ID查找会话上下文
     */
    SessionContext findSessionContextById(String sessionId);

    /**
     * 更新会话TTL
     */
    void updateTtl(String sessionId, int ttlSeconds);

    /**
     * 根据会话ID列表批量删除会话及相关数据（memory_item、chat_message、chat_session）
     */
    void deleteBySessionIds(List<String> sessionIds);

}
