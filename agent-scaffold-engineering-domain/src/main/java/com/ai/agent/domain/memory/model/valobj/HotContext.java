package com.ai.agent.domain.memory.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 热层上下文快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话ID */
    private String sessionId;

    /** 最近N条消息 */
    private List<MessageEntry> recentMessages;

    /** 全局摘要 */
    private String contextSummary;

    /** 最近M个工具结果 */
    private List<ToolResult> recentToolResults;

    /** 当前总token数 */
    private int tokenCount;

    /** 最后更新时间 */
    private LocalDateTime lastUpdated;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        private String role;
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private String toolName;
        private String result;
    }
}
