package com.ai.agent.domain.memory.model.aggregate;

import com.ai.agent.domain.memory.model.valobj.HotContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 记忆上下文聚合根 — 管理会话的完整记忆上下文
 * 所有对热层状态的变更必须通过此聚合根，确保业务规则一致性
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryContext {

    /** 会话ID */
    private String sessionId;

    /** 当前热层快照 */
    private HotContext hotContext;

    /** 硬上限：消息数超过此值时立即裁剪 */
    private int maxRecentMessages;

    /** 裁剪后保留最近 N 条 */
    private int keepRecentN;

    /**
     * 工厂方法：从已有热层上下文创建聚合根
     */
    public static MemoryContext from(String sessionId, HotContext hotContext,
                                     int maxRecentMessages, int keepRecentN) {
        MemoryContext ctx = new MemoryContext();
        ctx.sessionId = sessionId;
        ctx.hotContext = hotContext;
        ctx.maxRecentMessages = maxRecentMessages;
        ctx.keepRecentN = keepRecentN;
        return ctx;
    }

    /**
     * 工厂方法（向后兼容）：从已有热层上下文创建聚合根，使用默认裁剪参数
     */
    public static MemoryContext from(String sessionId, HotContext hotContext) {
        return from(sessionId, hotContext, 50, 20);
    }

    /**
     * 工厂方法：为新会话创建空聚合根
     */
    public static MemoryContext create(String sessionId,
                                       int maxRecentMessages, int keepRecentN) {
        MemoryContext ctx = new MemoryContext();
        ctx.sessionId = sessionId;
        ctx.maxRecentMessages = maxRecentMessages;
        ctx.keepRecentN = keepRecentN;
        ctx.hotContext = HotContext.builder()
                .sessionId(sessionId)
                .recentMessages(new ArrayList<>())
                .recentToolResults(new ArrayList<>())
                .tokenCount(0)
                .build();
        return ctx;
    }

    /**
     * 工厂方法（向后兼容）：为新会话创建空聚合根，使用默认裁剪参数
     */
    public static MemoryContext create(String sessionId) {
        return create(sessionId, 50, 20);
    }

    /**
     * 写入新消息 — 封装热层追加逻辑 + 硬截断安全阀
     */
    public void appendMessage(String role, String content, int tokenCount, float[] embedding) {
        if (hotContext == null) {
            hotContext = HotContext.builder()
                    .sessionId(sessionId)
                    .recentMessages(new ArrayList<>())
                    .recentToolResults(new ArrayList<>())
                    .tokenCount(0)
                    .build();
        }
        hotContext.getRecentMessages().add(
                HotContext.MessageEntry.builder()
                        .role(role)
                        .content(content)
                        .embedding(embedding)
                        .build());
        hotContext.setTokenCount(hotContext.getTokenCount() + tokenCount);
        hotContext.setLastUpdated(LocalDateTime.now());

        // 硬上限：超过 maxRecentMessages 时立即裁剪到 keepRecentN
        List<HotContext.MessageEntry> msgs = hotContext.getRecentMessages();
        if (msgs.size() > maxRecentMessages) {
            List<HotContext.MessageEntry> trimmed = new ArrayList<>(
                    msgs.subList(msgs.size() - keepRecentN, msgs.size()));
            hotContext.setRecentMessages(trimmed);
            recalculateTokenCount();
        }
    }

    /**
     * 写入新消息（向后兼容）：不含 embedding 的版本
     */
    public void appendMessage(String role, String content, int tokenCount) {
        appendMessage(role, content, tokenCount, null);
    }

    /**
     * 判断是否需要压缩
     */
    public boolean needsCompression(int contextWindowTokens, float threshold) {
        if (hotContext == null) return false;
        return hotContext.getTokenCount() >= contextWindowTokens * threshold;
    }

    /**
     * 应用压缩结果 — 保留最近N条消息 + 新摘要
     */
    public void applyCompression(String summary, int keepRecentN) {
        if (hotContext == null) return;
        int fromIndex = Math.max(0, hotContext.getRecentMessages().size() - keepRecentN);
        var recent = new ArrayList<>(
                hotContext.getRecentMessages().subList(fromIndex, hotContext.getRecentMessages().size()));
        hotContext.setRecentMessages(recent);
        hotContext.setContextSummary(summary);
        int recentTokens = recent.stream()
                .mapToInt(m -> HotContext.estimateTokens(m.getContent()))
                .sum();
        hotContext.setTokenCount(recentTokens + HotContext.estimateTokens(summary));
    }

    /**
     * 重新计算 token 总数（基于 estimateTokens）
     */
    private void recalculateTokenCount() {
        if (hotContext == null) return;
        int msgTokens = hotContext.getRecentMessages().stream()
                .mapToInt(m -> HotContext.estimateTokens(m.getContent()))
                .sum();
        int summaryTokens = hotContext.getContextSummary() != null
                ? HotContext.estimateTokens(hotContext.getContextSummary())
                : 0;
        hotContext.setTokenCount(msgTokens + summaryTokens);
    }

    /**
     * 获取当前token数
     */
    public int getTokenCount() {
        return hotContext != null ? hotContext.getTokenCount() : 0;
    }
}
