package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息持久化对象
 */
@Data
public class ChatMessagePO {

    /** 消息ID */
    private Long messageId;

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
