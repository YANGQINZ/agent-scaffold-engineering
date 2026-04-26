package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 记忆记录持久化对象
 */
@Data
public class MemoryItemPO {

    /** 主键ID */
    private Long id;

    /** 会话ID */
    private String sessionId;

    /** 提炼的事实文本 */
    private String content;

    /** 向量嵌入（PGvector 字符串格式） */
    private String embedding;

    /** 重要性分数 */
    private Float importance;

    /** 标签（PostgreSQL TEXT[]） */
    private String[] tags;

    /** 来源消息ID列表（PostgreSQL BIGINT[]） */
    private Long[] sourceMsgIds;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 元数据（JSONB） */
    private Map<String, Object> metadata;

    /** 检索时计算的相关度分数（非持久化） */
    private Float score;
}
