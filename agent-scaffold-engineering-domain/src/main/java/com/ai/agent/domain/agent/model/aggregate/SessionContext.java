package com.ai.agent.domain.agent.model.aggregate;

import com.ai.agent.domain.common.interface_.ContextStore;
import com.ai.agent.domain.common.interface_.MemoryPort;
import com.ai.agent.types.enums.EngineType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会话上下文聚合根 — 运行时记忆共享的唯一真相源
 *
 * 实现 ContextStore 接口，所有策略通过 SessionContext
 * 追加对话历史和组装记忆上下文。
 * 持久化由 ChatSession（chat 域）负责，SessionContext 通过 sessionId 关联。
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionContext implements ContextStore {

    /** 会话ID */
    private String sessionId;

    /** 用户ID */
    private String userId;

    /** 关联的Agent定义ID */
    private String agentId;

    /** 引擎类型 */
    private EngineType engineType = EngineType.GRAPH;

    /** 是否启用RAG */
    private boolean ragEnabled = false;

    /** 知识库ID */
    private String knowledgeBaseId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后活跃时间 */
    private LocalDateTime lastActiveAt;

    /** 过期时间（秒） */
    private int ttlSeconds = 3600;

    /** 最后访问时间（epoch毫秒），用于TTL淘汰 */
    private long lastAccessTime = System.currentTimeMillis();

    /** MemoryPort 引用（由 ContextStoreFactory 注入，不参与序列化） */
    private transient MemoryPort memoryPort;

    // ═══════════════════════════════════════════════════════
    // 工厂方法
    // ═══════════════════════════════════════════════════════

    /**
     * 工厂方法：为新会话创建 SessionContext
     */
    public static SessionContext create(String sessionId, String userId,
                                        EngineType engineType, boolean ragEnabled,
                                        String knowledgeBaseId,
                                        MemoryPort memoryPort) {
        return create(sessionId, userId, null, engineType, ragEnabled, knowledgeBaseId, memoryPort);
    }

    /**
     * 工厂方法：为新会话创建 SessionContext（含 agentId）
     */
    public static SessionContext create(String sessionId, String userId, String agentId,
                                        EngineType engineType, boolean ragEnabled,
                                        String knowledgeBaseId,
                                        MemoryPort memoryPort) {
        SessionContext ctx = new SessionContext();
        ctx.sessionId = sessionId;
        ctx.userId = userId;
        ctx.agentId = agentId;
        ctx.engineType = engineType != null ? engineType : EngineType.GRAPH;
        ctx.ragEnabled = ragEnabled;
        ctx.knowledgeBaseId = knowledgeBaseId;
        ctx.createdAt = LocalDateTime.now();
        ctx.lastActiveAt = ctx.createdAt;
        ctx.lastAccessTime = System.currentTimeMillis();
        ctx.memoryPort = memoryPort;
        return ctx;
    }

    // ═══════════════════════════════════════════════════════
    // ContextStore 接口实现
    // ═══════════════════════════════════════════════════════

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public void appendHistory(String senderId, String content, Map<String, Object> metadata) {
        this.lastAccessTime = System.currentTimeMillis();
        this.lastActiveAt = LocalDateTime.now();
        if (content == null) {
            return;
        }

        if (memoryPort != null) {
            try {
                String role = "assistant";
                if (metadata != null && "user".equals(metadata.get("role"))) {
                    role = "user";
                }
                memoryPort.onMessageCreated(sessionId, content, role);
            } catch (Exception e) {
                log.warn("记忆写入降级: sessionId={}, error={}", sessionId, e.getMessage());
            }
        }

        log.debug("SessionContext.appendHistory: sessionId={}, senderId={}", sessionId, senderId);
    }

    @Override
    public String assembleMemoryContext(String query) {
        if (memoryPort == null) {
            return "";
        }
        try {
            var messages = memoryPort.assembleContext(sessionId, query);
            StringBuilder sb = new StringBuilder();
            for (var msg : messages) {
                sb.append(msg.getMessageType().getValue())
                  .append(": ")
                  .append(msg.getText())
                  .append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("记忆上下文组装降级: sessionId={}, error={}", sessionId, e.getMessage());
            return "";
        }
    }

    /**
     * 更新最后访问时间（TTL淘汰用）
     */
    public void touch() {
        this.lastAccessTime = System.currentTimeMillis();
        this.lastActiveAt = LocalDateTime.now();
    }

}
