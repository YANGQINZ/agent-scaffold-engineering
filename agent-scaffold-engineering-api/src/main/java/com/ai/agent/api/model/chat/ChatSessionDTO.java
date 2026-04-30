package com.ai.agent.api.model.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天会话DTO — 用于返回会话列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionDTO {

    /** 会话ID */
    private String sessionId;

    /** 用户ID */
    private String userId;

    /** 关联的Agent定义ID */
    private String agentId;

    /** 对话模式 */
    private String mode;

    /** 引擎类型 */
    private String engine;

    /** 是否启用RAG */
    private Boolean ragEnabled;

    /** 知识库ID */
    private String knowledgeBaseId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后活跃时间 */
    private LocalDateTime lastActiveAt;

}
