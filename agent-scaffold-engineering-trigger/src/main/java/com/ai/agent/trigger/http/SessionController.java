package com.ai.agent.trigger.http;

import com.ai.agent.api.model.chat.ChatMessageDTO;
import com.ai.agent.api.model.chat.ChatSessionDTO;
import com.ai.agent.domain.chat.model.aggregate.ChatSession;
import com.ai.agent.domain.chat.model.entity.ChatMessage;
import com.ai.agent.domain.chat.repository.IChatSessionRepository;
import com.ai.agent.types.model.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话历史 HTTP 控制器 — 提供会话列表和消息历史查询
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final IChatSessionRepository chatSessionRepository;

    /**
     * 根据Agent ID查询会话列表
     */
    @GetMapping
    public Response<List<ChatSessionDTO>> listSessions(
            @RequestParam(value = "agentId", required = false) String agentId) {
        log.info("查询会话列表: agentId={}", agentId);
        List<ChatSession> sessions;
        if (agentId != null && !agentId.isBlank()) {
            sessions = chatSessionRepository.findByAgentId(agentId);
        } else {
            // 未指定agentId时返回空列表，避免全表扫描
            sessions = List.of();
        }
        List<ChatSessionDTO> dtoList = sessions.stream()
                .map(this::toSessionDTO)
                .toList();
        return Response.buildSuccess(dtoList);
    }

    /**
     * 根据会话ID查询消息历史
     */
    @GetMapping("/{sessionId}/messages")
    public Response<List<ChatMessageDTO>> listMessages(@PathVariable String sessionId) {
        log.info("查询会话消息: sessionId={}", sessionId);
        List<ChatMessage> messages = chatSessionRepository.findMessagesBySessionId(sessionId);
        List<ChatMessageDTO> dtoList = messages.stream()
                .map(this::toMessageDTO)
                .toList();
        return Response.buildSuccess(dtoList);
    }

    /**
     * ChatSession -> ChatSessionDTO
     */
    private ChatSessionDTO toSessionDTO(ChatSession session) {
        return ChatSessionDTO.builder()
                .sessionId(session.getSessionId())
                .userId(session.getUserId())
                .agentId(session.getAgentId())
                .mode(session.getMode() != null ? session.getMode().name() : null)
                .engine(session.getEngine() != null ? session.getEngine().name() : null)
                .ragEnabled(session.getRagEnabled())
                .knowledgeBaseId(session.getKnowledgeBaseId())
                .createdAt(session.getCreatedAt())
                .lastActiveAt(session.getLastActiveAt())
                .build();
    }

    /**
     * ChatMessage -> ChatMessageDTO
     */
    private ChatMessageDTO toMessageDTO(ChatMessage message) {
        return ChatMessageDTO.builder()
                .messageId(message.getMessageId())
                .sessionId(message.getSessionId())
                .role(message.getRole() != null ? message.getRole().name() : null)
                .content(message.getContent())
                .tokenCount(message.getTokenCount())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
