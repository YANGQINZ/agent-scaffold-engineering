package com.ai.agent.domain.memory.model.aggregate;

import com.ai.agent.domain.memory.model.valobj.HotContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;

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

    /**
     * 工厂方法：从已有热层上下文创建聚合根
     */
    public static MemoryContext from(String sessionId, HotContext hotContext) {
        MemoryContext ctx = new MemoryContext();
        ctx.sessionId = sessionId;
        ctx.hotContext = hotContext;
        return ctx;
    }

    /**
     * 工厂方法：为新会话创建空聚合根
     */
    public static MemoryContext create(String sessionId) {
        MemoryContext ctx = new MemoryContext();
        ctx.sessionId = sessionId;
        ctx.hotContext = HotContext.builder()
                .sessionId(sessionId)
                .recentMessages(new ArrayList<>())
                .recentToolResults(new ArrayList<>())
                .tokenCount(0)
                .build();
        return ctx;
    }

    /**
     * 写入新消息 — 封装热层追加逻辑
     */
    public void appendMessage(String role, String content, int tokenCount) {
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
                        .build());
        hotContext.setTokenCount(hotContext.getTokenCount() + tokenCount);
        hotContext.setLastUpdated(LocalDateTime.now());
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
                .mapToInt(m -> m.getContent().length() / 4)
                .sum();
        hotContext.setTokenCount(recentTokens + summary.length() / 4);
    }

    /**
     * 获取当前token数
     */
    public int getTokenCount() {
        return hotContext != null ? hotContext.getTokenCount() : 0;
    }
}
