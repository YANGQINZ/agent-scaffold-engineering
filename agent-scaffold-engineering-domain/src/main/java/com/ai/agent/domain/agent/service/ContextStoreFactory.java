package com.ai.agent.domain.agent.service;

import com.ai.agent.domain.agent.model.aggregate.SessionContext;
import com.ai.agent.domain.agent.repository.ISessionRepository;
import com.ai.agent.domain.common.interface_.ContextStore;
import com.ai.agent.domain.common.interface_.MemoryPort;
import com.ai.agent.types.enums.EngineType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * SessionContext 工厂 — 创建和复用会话上下文实例
 */
@Slf4j
@Service
public class ContextStoreFactory {

    private final MemoryPort memoryPort;
    private final ISessionRepository sessionRepository;

    private final ConcurrentHashMap<String, SessionContext> store = new ConcurrentHashMap<>();

    private static final long SESSION_TTL_MS = 30 * 60 * 1000L;

    public ContextStoreFactory(MemoryPort memoryPort, ISessionRepository sessionRepository) {
        this.memoryPort = memoryPort;
        this.sessionRepository = sessionRepository;
    }

    public ContextStore getOrCreate(String sessionId) {
        return store.computeIfAbsent(sessionId, id -> {
            SessionContext ctx = createDefault(id);
            persistIfNeeded(ctx);
            return ctx;
        });
    }

    public ContextStore getOrCreate(String sessionId, String userId,
                                     EngineType engineType, boolean ragEnabled,
                                     String knowledgeBaseId) {
        return getOrCreate(sessionId, userId, null, engineType, ragEnabled, knowledgeBaseId);
    }

    public ContextStore getOrCreate(String sessionId, String userId, String agentId,
                                     EngineType engineType, boolean ragEnabled,
                                     String knowledgeBaseId) {
        return store.computeIfAbsent(sessionId,
                id -> {
                    SessionContext ctx = SessionContext.create(id, userId, agentId, engineType,
                            ragEnabled, knowledgeBaseId, memoryPort);
                    persistIfNeeded(ctx);
                    return ctx;
                });
    }

    public void remove(String sessionId) {
        SessionContext ctx = store.remove(sessionId);
        if (ctx != null) {
            log.info("会话上下文已移除: sessionId={}", sessionId);
        }
    }

    private SessionContext createDefault(String sessionId) {
        log.debug("创建新SessionContext: sessionId={}", sessionId);
        return SessionContext.create(sessionId, null, EngineType.GRAPH, false, null, memoryPort);
    }

    /**
     * 持久化会话到数据库 — 确保在 chat_message 插入前 chat_session 行已存在
     */
    private void persistIfNeeded(SessionContext ctx) {
        try {
            if (sessionRepository.findSessionContextById(ctx.getSessionId()) == null) {
                sessionRepository.save(ctx);
                log.info("会话已持久化: sessionId={}", ctx.getSessionId());
            }
        } catch (Exception e) {
            log.warn("会话持久化降级（可能已存在）: sessionId={}, error={}", ctx.getSessionId(), e.getMessage());
        }
    }

    @Scheduled(fixedRate = 300000)
    public void evictExpiredSessions() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(entry -> {
            long idleMs = now - entry.getValue().getLastAccessTime();
            if (idleMs > SESSION_TTL_MS) {
                log.info("会话上下文TTL淘汰: sessionId={}, 空闲时长={}ms", entry.getKey(), idleMs);
                return true;
            }
            return false;
        });
    }
}
