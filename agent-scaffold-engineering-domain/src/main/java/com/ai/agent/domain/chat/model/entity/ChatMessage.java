package com.ai.agent.domain.chat.model.entity;

import com.ai.agent.types.enums.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /** 消息ID */
    private String messageId;

    /** 会话ID */
    private String sessionId;

    /** 消息角色 */
    private MessageRole role;

    /** 消息内容 */
    private String content;

    /** Token数量 */
    private Integer tokenCount;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
