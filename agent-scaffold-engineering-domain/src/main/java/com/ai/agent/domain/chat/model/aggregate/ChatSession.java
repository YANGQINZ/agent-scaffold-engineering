package com.ai.agent.domain.chat.model.aggregate;

import com.ai.agent.domain.chat.model.entity.ChatMessage;
import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.enums.ChatMode;
import com.ai.agent.types.enums.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    /** 会话ID */
    private String sessionId;

    /** 用户ID */
    private String userId;

    /** 对话模式 */
    private ChatMode mode;

    /** 引擎类型 */
    private EngineType engine;

    /** 是否启用RAG */
    private Boolean ragEnabled;

    /** 知识库ID */
    private String knowledgeBaseId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后活跃时间 */
    private LocalDateTime lastActiveAt;

    /** 过期时间（秒） */
    private Integer ttlSeconds;

    /** 消息列表 */
    private List<ChatMessage> messages = new ArrayList<>();

    /**
     * 添加消息到会话
     */
    public void addMessage(ChatMessage message) {
        message.setSessionId(this.sessionId);
        this.messages.add(message);
        this.lastActiveAt = LocalDateTime.now();
    }

    /**
     * 添加用户消息
     */
    public ChatMessage addUserMessage(String content) {
        ChatMessage message = ChatMessage.builder()
                .sessionId(this.sessionId)
                .role(MessageRole.USER)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
        addMessage(message);
        return message;
    }

    /**
     * 添加助手消息
     */
    public ChatMessage addAssistantMessage(String content) {
        ChatMessage message = ChatMessage.builder()
                .sessionId(this.sessionId)
                .role(MessageRole.ASSISTANT)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
        addMessage(message);
        return message;
    }

}
