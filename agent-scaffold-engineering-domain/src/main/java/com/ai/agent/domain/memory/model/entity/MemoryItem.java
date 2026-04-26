package com.ai.agent.domain.memory.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 提炼记忆记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryItem {

    /** 主键ID */
    private Long id;

    /** 会话ID */
    private String sessionId;

    /** 提炼的事实文本 */
    private String content;

    /** 1536维向量 */
    private float[] embedding;

    /** 重要性分数 0-1 */
    private Float importance;

    /** 标签（如 ["summary"]、["fact"]） */
    private List<String> tags;

    /** 来源 chat_message.id 列表 */
    private List<Long> sourceMsgIds;

    /** 时间戳 */
    private LocalDateTime timestamp;

    /** 扩展元数据 */
    private Map<String, Object> metadata;

    /** 检索时计算的相关度分数（非持久化） */
    private Float score;
}
