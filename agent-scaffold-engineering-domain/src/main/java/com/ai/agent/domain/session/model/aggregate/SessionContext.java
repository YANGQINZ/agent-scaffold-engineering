package com.ai.agent.domain.session.model.aggregate;

import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.agent.repository.ContextStore;
import com.ai.agent.domain.memory.service.MemoryFacade;
import com.ai.agent.types.enums.EngineType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

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

    /** 引擎类型 */
    @Builder.Default
    private EngineType engineType = EngineType.GRAPH;

    /** 是否启用RAG */
    @Builder.Default
    private boolean ragEnabled = false;

    /** 知识库ID */
    private String knowledgeBaseId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后活跃时间 */
    private LocalDateTime lastActiveAt;

    /** 过期时间（秒） */
    @Builder.Default
    private int ttlSeconds = 3600;

    /** 最后访问时间（epoch毫秒），用于TTL淘汰 */
    @Builder.Default
    private long lastAccessTime = System.currentTimeMillis();

    /** MemoryFacade 引用（由 ContextStoreFactory 注入，不参与序列化） */
    private transient MemoryFacade memoryFacade;

    // ═══════════════════════════════════════════════════════
    // 工厂方法
    // ═══════════════════════════════════════════════════════

    /**
     * 工厂方法：为新会话创建 SessionContext
     */
    public static SessionContext create(String sessionId, String userId,
                                        EngineType engineType, boolean ragEnabled,
                                        String knowledgeBaseId,
                                        MemoryFacade memoryFacade) {
        SessionContext ctx = new SessionContext();
        ctx.sessionId = sessionId;
        ctx.userId = userId;
        ctx.engineType = engineType != null ? engineType : EngineType.GRAPH;
        ctx.ragEnabled = ragEnabled;
        ctx.knowledgeBaseId = knowledgeBaseId;
        ctx.createdAt = LocalDateTime.now();
        ctx.lastActiveAt = ctx.createdAt;
        ctx.lastAccessTime = System.currentTimeMillis();
        ctx.memoryFacade = memoryFacade;
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
    public void appendHistory(AgentMessage msg) {
        this.lastAccessTime = System.currentTimeMillis();
        this.lastActiveAt = LocalDateTime.now();
        if (msg == null) {
            return;
        }

        // 委托 MemoryFacade 写入长期记忆（冷层 chat_message + 热层 HotContext）
        if (memoryFacade != null) {
            try {
                String role = "assistant";
                if ("user".equals(msg.getMetadataValue("role"))) {
                    role = "user";
                }
                int estimatedTokens = msg.getContent() != null ? msg.getContent().length() / 4 : 0;
                memoryFacade.appendMessage(sessionId, role, msg.getContent(), estimatedTokens);
            } catch (Exception e) {
                log.warn("记忆写入降级: sessionId={}, error={}", sessionId, e.getMessage());
            }
        }

        log.debug("SessionContext.appendHistory: sessionId={}, senderId={}",
                sessionId, msg.getSenderId());
    }

    @Override
    public String assembleMemoryContext(String query) {
        if (memoryFacade == null) {
            return "";
        }
        try {
            var messages = memoryFacade.assembleContext(sessionId, query);
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
