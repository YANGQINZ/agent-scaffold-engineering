package com.ai.agent.api.model.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天消息DTO — 用于返回会话消息历史
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {

    /** 消息ID */
    private String messageId;

    /** 会话ID */
    private String sessionId;

    /** 消息角色 */
    private String role;

    /** 消息内容 */
    private String content;

    /** Token数量 */
    private Integer tokenCount;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
