package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天会话持久化对象
 */
@Data
public class ChatSessionPO {

    /** 会话ID */
    private String sessionId;

    /** 会话名称 */
    private String name;

    /** 用户ID */
    private String userId;

    /** 关联的Agent定义ID */
    private String agentId;

    /** 对话模式 */
    private String mode;

    /** 智能体模式 */
    private String agentMode;

    /** 是否启用RAG */
    private Boolean ragEnabled;

    /** 知识库ID */
    private String knowledgeBaseId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** TTL过期时间 */
    private LocalDateTime ttlExpireAt;

}
