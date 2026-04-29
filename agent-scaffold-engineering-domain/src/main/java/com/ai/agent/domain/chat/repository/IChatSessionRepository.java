package com.ai.agent.domain.chat.repository;

import com.ai.agent.domain.chat.model.aggregate.ChatSession;
import com.ai.agent.domain.chat.model.entity.ChatMessage;

import java.util.List;
import java.util.Optional;

/**
 * 聊天会话仓储接口
 */
public interface IChatSessionRepository {

    /**
     * 保存会话
     */
    void save(ChatSession session);

    /**
     * 根据会话ID查找会话
     */
    Optional<ChatSession> findById(String sessionId);

    /**
     * 保存消息
     */
    void saveMessage(ChatMessage message);

    /**
     * 根据会话ID查找消息列表
     */
    List<ChatMessage> findMessagesBySessionId(String sessionId);

    /**
     * 更新会话TTL
     */
    void updateSessionTtl(String sessionId, int ttlSeconds);

    /**
     * 根据用户ID查询会话列表
     */
    List<ChatSession> findByUserId(String userId);

    /**
     * 根据Agent ID查询会话列表
     */
    List<ChatSession> findByAgentId(String agentId);

}
