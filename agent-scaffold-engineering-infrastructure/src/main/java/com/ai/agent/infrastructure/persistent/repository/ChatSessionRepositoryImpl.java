package com.ai.agent.infrastructure.persistent.repository;

import com.ai.agent.domain.chat.model.aggregate.ChatSession;
import com.ai.agent.domain.chat.model.entity.ChatMessage;
import com.ai.agent.domain.chat.repository.IChatSessionRepository;
import com.ai.agent.domain.agent.model.aggregate.SessionContext;
import com.ai.agent.domain.agent.repository.ISessionRepository;
import com.ai.agent.infrastructure.persistent.dao.IChatMessageDao;
import com.ai.agent.infrastructure.persistent.dao.IChatSessionDao;
import com.ai.agent.infrastructure.persistent.po.ChatMessagePO;
import com.ai.agent.infrastructure.persistent.po.ChatSessionPO;
import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.enums.ChatMode;
import com.ai.agent.types.enums.MessageRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 聊天会话仓储实现 — 同时实现 IChatSessionRepository（chat域）和 ISessionRepository（session域）
 */
@Repository
@RequiredArgsConstructor
public class ChatSessionRepositoryImpl implements IChatSessionRepository, ISessionRepository {

    private final IChatSessionDao chatSessionDao;
    private final IChatMessageDao chatMessageDao;

    @Override
    public void save(ChatSession session) {
        ChatSessionPO po = new ChatSessionPO();
        po.setSessionId(session.getSessionId());
        po.setUserId(session.getUserId());
        po.setMode(session.getMode().name());
        po.setAgentMode(session.getEngine().name());
        po.setRagEnabled(session.getRagEnabled());
        po.setKnowledgeBaseId(session.getKnowledgeBaseId());
        po.setCreatedAt(session.getCreatedAt());
        po.setUpdatedAt(session.getLastActiveAt() != null ? session.getLastActiveAt() : session.getCreatedAt());
        if (session.getTtlSeconds() != null && session.getCreatedAt() != null) {
            po.setTtlExpireAt(session.getCreatedAt().plusSeconds(session.getTtlSeconds()));
        }
        chatSessionDao.insert(po);
    }

    @Override
    public Optional<ChatSession> findById(String sessionId) {
        ChatSessionPO po = chatSessionDao.selectById(sessionId);
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toSession(po));
    }

    @Override
    public void saveMessage(ChatMessage message) {
        ChatMessagePO po = new ChatMessagePO();
        po.setSessionId(message.getSessionId());
        po.setRole(message.getRole().name());
        po.setContent(message.getContent());
        po.setTokenCount(message.getTokenCount());
        po.setCreatedAt(message.getCreatedAt());
        chatMessageDao.insert(po);
    }

    @Override
    public List<ChatMessage> findMessagesBySessionId(String sessionId) {
        List<ChatMessagePO> poList = chatMessageDao.selectBySessionId(sessionId);
        return poList.stream().map(this::toMessage).toList();
    }

    @Override
    public void updateSessionTtl(String sessionId, int ttlSeconds) {
        chatSessionDao.updateTtl(sessionId);
    }

    @Override
    public List<ChatSession> findByUserId(String userId) {
        List<ChatSessionPO> poList = chatSessionDao.selectByUserId(userId);
        return poList.stream().map(this::toSession).toList();
    }

    /**
     * ChatSessionPO -> ChatSession 领域对象
     */
    private ChatSession toSession(ChatSessionPO po) {
        return ChatSession.builder()
                .sessionId(po.getSessionId())
                .userId(po.getUserId())
                .mode(ChatMode.valueOf(po.getMode()))
                .engine(EngineType.valueOf(po.getAgentMode()))
                .ragEnabled(po.getRagEnabled())
                .knowledgeBaseId(po.getKnowledgeBaseId())
                .createdAt(po.getCreatedAt())
                .lastActiveAt(po.getUpdatedAt())
                .build();
    }

    /**
     * ChatMessagePO -> ChatMessage 领域对象
     */
    private ChatMessage toMessage(ChatMessagePO po) {
        return ChatMessage.builder()
                .messageId(String.valueOf(po.getMessageId()))
                .sessionId(po.getSessionId())
                .role(MessageRole.valueOf(po.getRole()))
                .content(po.getContent())
                .tokenCount(po.getTokenCount())
                .createdAt(po.getCreatedAt())
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // ISessionRepository 接口实现（session域）
    // ═══════════════════════════════════════════════════════

    @Override
    public void save(SessionContext session) {
        ChatSessionPO po = new ChatSessionPO();
        po.setSessionId(session.getSessionId());
        po.setUserId(session.getUserId());
        po.setMode(ChatMode.AGENT.name());
        po.setAgentMode(session.getEngineType().name());
        po.setRagEnabled(session.isRagEnabled());
        po.setKnowledgeBaseId(session.getKnowledgeBaseId());
        po.setCreatedAt(session.getCreatedAt());
        po.setUpdatedAt(session.getLastActiveAt() != null ? session.getLastActiveAt() : session.getCreatedAt());
        if (session.getTtlSeconds() > 0 && session.getCreatedAt() != null) {
            po.setTtlExpireAt(session.getCreatedAt().plusSeconds(session.getTtlSeconds()));
        }
        chatSessionDao.insert(po);
    }

    @Override
    public SessionContext findSessionContextById(String sessionId) {
        ChatSessionPO po = chatSessionDao.selectById(sessionId);
        if (po == null) {
            return null;
        }
        return toSessionContext(po);
    }

    @Override
    public void updateTtl(String sessionId, int ttlSeconds) {
        chatSessionDao.updateTtl(sessionId);
    }

    /**
     * ChatSessionPO -> SessionContext 领域对象（session域）
     */
    private SessionContext toSessionContext(ChatSessionPO po) {
        return SessionContext.builder()
                .sessionId(po.getSessionId())
                .userId(po.getUserId())
                .engineType(EngineType.valueOf(po.getAgentMode()))
                .ragEnabled(po.getRagEnabled() != null ? po.getRagEnabled() : false)
                .knowledgeBaseId(po.getKnowledgeBaseId())
                .createdAt(po.getCreatedAt())
                .lastActiveAt(po.getUpdatedAt())
                .build();
    }

}
