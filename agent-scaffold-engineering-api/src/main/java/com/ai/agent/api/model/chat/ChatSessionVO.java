package com.ai.agent.api.model.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话管理VO — 用于创建和列表查询
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionVO {

    /** 会话ID */
    private String sessionId;

    /** 会话名称 */
    private String name;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后一条消息摘要 */
    private String lastMessage;

}
