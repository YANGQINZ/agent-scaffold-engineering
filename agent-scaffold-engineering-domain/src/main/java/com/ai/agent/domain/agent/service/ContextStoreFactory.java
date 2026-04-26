package com.ai.agent.domain.agent.service;

import com.ai.agent.domain.agent.model.aggregate.SessionContext;
import com.ai.agent.domain.agent.repository.ContextStore;
import com.ai.agent.domain.memory.service.MemoryFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * SessionContext 工厂 — 创建和复用跨引擎共享的上下文实例
 *
 * 维护 sessionId → SessionContext 的映射，确保同一会话使用同一个实例。
 * SessionContext 中的 MemoryFacade 引用由此工厂注入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextStoreFactory {

    private final MemoryFacade memoryFacade;

    /** 会话上下文缓存（sessionId → SessionContext） */
    private final ConcurrentHashMap<String, SessionContext> store = new ConcurrentHashMap<>();

    /** 会话TTL：30分钟无访问自动淘汰 */
    private static final long SESSION_TTL_MS = 30 * 60 * 1000L;

    /**
     * 获取或创建指定会话的 ContextStore
     *
     * @param sessionId 会话ID
     * @return 对应的 SessionContext 实例（实现 ContextStore）
     */
    public ContextStore getOrCreate(String sessionId) {
        return store.computeIfAbsent(sessionId, this::createDefault);
    }

    /**
     * 获取或创建指定会话的 ContextStore（带完整参数）
     *
     * @param sessionId      会话ID
     * @param userId         用户ID
     * @param memoryEnabled  是否启用长期记忆
     * @param ragEnabled     是否启用RAG
     * @param knowledgeBaseId 知识库ID
     * @return 对应的 SessionContext 实例
     */
    public ContextStore getOrCreate(String sessionId, String userId,
                                     boolean memoryEnabled, boolean ragEnabled,
                                     String knowledgeBaseId) {
        return store.computeIfAbsent(sessionId,
                id -> SessionContext.create(id, userId, memoryEnabled, ragEnabled,
                        knowledgeBaseId, memoryFacade));
    }

    /**
     * 移除指定会话的上下文（会话结束时调用）
     *
     * @param sessionId 会话ID
     */
    public void remove(String sessionId) {
        SessionContext ctx = store.remove(sessionId);
        if (ctx != null) {
            ctx.clear();
            log.info("会话上下文已移除: sessionId={}", sessionId);
        }
    }

    /**
     * 创建默认 SessionContext
     */
    private SessionContext createDefault(String sessionId) {
        log.debug("创建新SessionContext: sessionId={}", sessionId);
        return SessionContext.create(sessionId, null, false, false, null, memoryFacade);
    }

    /**
     * 定时淘汰超时会话 — 每5分钟检查一次，移除超过30分钟无访问的会话
     */
    @Scheduled(fixedRate = 300000)
    public void evictExpiredSessions() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(entry -> {
            long idleMs = now - entry.getValue().getLastAccessTime();
            if (idleMs > SESSION_TTL_MS) {
                log.info("会话上下文TTL淘汰: sessionId={}, 空闲时长={}ms", entry.getKey(), idleMs);
                entry.getValue().clear();
                return true;
            }
            return false;
        });
    }

}
