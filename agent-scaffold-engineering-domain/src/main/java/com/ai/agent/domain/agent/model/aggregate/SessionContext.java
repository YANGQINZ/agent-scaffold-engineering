package com.ai.agent.domain.agent.model.aggregate;

import com.ai.agent.domain.agent.model.valobj.AgentMessage;
import com.ai.agent.domain.agent.repository.ContextStore;
import com.ai.agent.domain.memory.model.valobj.HotContext;
import com.ai.agent.domain.memory.service.MemoryFacade;
import com.ai.agent.types.enums.EngineType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话上下文聚合根 — 跨引擎状态共享的唯一真相源
 *
 * 实现 ContextStore 接口，Graph 节点和 AgentScope Agent
 * 通过同一个 SessionContext 实例读写对话历史和键值状态，
 * 解决跨引擎状态丢失的问题。
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

    /** 当前活跃引擎 */
    @Builder.Default
    private EngineType currentEngine = EngineType.GRAPH;

    /** 键值状态（内存，跨引擎共享中间结果） */
    @Builder.Default
    private Map<String, Object> kvState = new ConcurrentHashMap<>();

    /** 跨引擎对话历史 */
    @Builder.Default
    private List<AgentMessage> history = Collections.synchronizedList(new ArrayList<>());

    /** 是否启用长期记忆 */
    @Builder.Default
    private boolean memoryEnabled = false;

    /** 复用现有热层上下文 */
    private HotContext hotContext;

    /** 是否启用RAG */
    @Builder.Default
    private boolean ragEnabled = false;

    /** 知识库ID */
    private String knowledgeBaseId;

    /** 最后访问时间（epoch毫秒），用于TTL淘汰 */
    @Builder.Default
    private long lastAccessTime = System.currentTimeMillis();

    /** MemoryFacade 引用（由 ContextStoreFactory 注入，不参与序列化） */
    private transient MemoryFacade memoryFacade;

    // ═══════════════════════════════════════════════════════
    // 工厂方法
    // ═══════════════════════════════════════════════════════

    /**
     * 工厂方法：为新会话创建空 SessionContext
     *
     * @param sessionId      会话ID
     * @param userId         用户ID
     * @param memoryEnabled  是否启用长期记忆
     * @param ragEnabled     是否启用RAG
     * @param knowledgeBaseId 知识库ID
     * @param memoryFacade   记忆门面（可为null）
     * @return 新创建的 SessionContext 实例
     */
    public static SessionContext create(String sessionId, String userId,
                                        boolean memoryEnabled, boolean ragEnabled,
                                        String knowledgeBaseId,
                                        MemoryFacade memoryFacade) {
        SessionContext ctx = new SessionContext();
        ctx.sessionId = sessionId;
        ctx.userId = userId;
        ctx.memoryEnabled = memoryEnabled;
        ctx.ragEnabled = ragEnabled;
        ctx.knowledgeBaseId = knowledgeBaseId;
        ctx.kvState = new ConcurrentHashMap<>();
        ctx.history = Collections.synchronizedList(new ArrayList<>());
        ctx.lastAccessTime = System.currentTimeMillis();
        ctx.memoryFacade = memoryFacade;
        return ctx;
    }

    /**
     * 工厂方法：从已有热层上下文恢复 SessionContext
     *
     * @param sessionId      会话ID
     * @param userId         用户ID
     * @param hotContext     热层上下文
     * @param memoryEnabled  是否启用长期记忆
     * @param ragEnabled     是否启用RAG
     * @param knowledgeBaseId 知识库ID
     * @param memoryFacade   记忆门面
     * @return 恢复的 SessionContext 实例
     */
    public static SessionContext from(String sessionId, String userId,
                                      HotContext hotContext,
                                      boolean memoryEnabled, boolean ragEnabled,
                                      String knowledgeBaseId,
                                      MemoryFacade memoryFacade) {
        SessionContext ctx = create(sessionId, userId, memoryEnabled,
                ragEnabled, knowledgeBaseId, memoryFacade);
        ctx.hotContext = hotContext;
        return ctx;
    }

    // ═══════════════════════════════════════════════════════
    // ContextStore 接口实现
    // ═══════════════════════════════════════════════════════

    @Override
    public void put(String key, Object value) {
        this.lastAccessTime = System.currentTimeMillis();
        kvState.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        this.lastAccessTime = System.currentTimeMillis();
        Object value = kvState.get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            log.warn("ContextStore类型不匹配: key={}, 期望={}, 实际={}",
                    key, type.getSimpleName(), value.getClass().getSimpleName());
            return null;
        }
        return (T) value;
    }

    @Override
    public void clear() {
        kvState.clear();
        history.clear();
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public List<AgentMessage> getHistory() {
        return Collections.unmodifiableList(history);
    }

    @Override
    public void appendHistory(AgentMessage msg) {
        this.lastAccessTime = System.currentTimeMillis();
        if (msg == null) {
            return;
        }
        // 1. 写入内存对话历史（跨引擎可见）
        history.add(msg);

        // 2. 委托 MemoryFacade 写入长期记忆（冷层 chat_message + 热层 HotContext）
        if (memoryFacade != null && memoryEnabled) {
            try {
                String role = msg.getSenderId() != null ? "assistant" : "user";
                if ("user".equals(msg.getMetadataValue("role"))) {
                    role = "user";
                }
                int estimatedTokens = msg.getContent() != null ? msg.getContent().length() / 4 : 0;
                memoryFacade.appendMessage(sessionId, role, msg.getContent(), estimatedTokens);
            } catch (Exception e) {
                log.warn("记忆写入降级: sessionId={}, error={}", sessionId, e.getMessage());
                // 记忆写入失败不影响主流程，仅记录日志
            }
        }

        log.debug("SessionContext.appendHistory: sessionId={}, senderId={}, historySize={}",
                sessionId, msg.getSenderId(), history.size());
    }

    // ═══════════════════════════════════════════════════════
    // 引擎切换
    // ═══════════════════════════════════════════════════════

    /**
     * 引擎切换时状态同步
     *
     * Hybrid 模式下子节点切换引擎时调用，触发日志、状态校验等附加逻辑，
     * 确保 kvState 和 history 完整传递。
     *
     * @param target 目标引擎类型
     */
    public void switchEngine(EngineType target) {
        EngineType previous = this.currentEngine;
        this.currentEngine = target;

        log.info("引擎切换: sessionId={}, {} → {}, historySize={}, kvStateSize={}",
                sessionId,
                previous != null ? previous.name() : "null",
                target.name(),
                history.size(),
                kvState.size());

        // 状态校验：切换后确保核心数据完整
        if (sessionId == null || sessionId.isBlank()) {
            log.error("引擎切换后状态校验失败: sessionId为空");
        }
    }

    // ═══════════════════════════════════════════════════════
    // 记忆上下文组装
    // ═══════════════════════════════════════════════════════

    /**
     * 记忆上下文组装（供 Agent 使用）
     *
     * 委托 MemoryFacade → ContextAssembler，返回：摘要 + 语义记忆 + 近期消息
     *
     * @param query 当前查询
     * @return 拼接后的记忆上下文文本，失败时返回空字符串
     */
    public String assembleMemoryContext(String query) {
        if (memoryFacade == null || !memoryEnabled) {
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

    // ═══════════════════════════════════════════════════════
    // 便捷方法
    // ═══════════════════════════════════════════════════════

    /**
     * 获取最后一条助手消息的内容（供下游节点使用）
     *
     * @return 最后一条助手消息内容，不存在时返回空字符串
     */
    public String getLastAssistantContent() {
        for (int i = history.size() - 1; i >= 0; i--) {
            AgentMessage msg = history.get(i);
            if ("assistant".equals(msg.getMetadataValue("role"))) {
                return msg.getContent();
            }
        }
        return "";
    }

    /**
     * 构建对话历史的文本摘要（供 LLM 上下文注入）
     *
     * @return 格式为 "role: content\n" 的历史文本
     */
    public String buildHistoryText() {
        if (history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentMessage msg : history) {
            String role = Optional.ofNullable((String) msg.getMetadataValue("role")).orElse("unknown");
            sb.append(role).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }

}
