package com.ai.agent.domain.chat.service;

import com.ai.agent.domain.agent.service.ContextStoreFactory;
import com.ai.agent.domain.chat.model.aggregate.ChatSession;
import com.ai.agent.domain.chat.model.entity.ChatMessage;
import com.ai.agent.domain.chat.repository.IChatSessionRepository;
import com.ai.agent.domain.common.event.MessageCreatedEvent;
import com.ai.agent.domain.common.interface_.ChatStrategy;
import com.ai.agent.domain.common.valobj.ChatRequest;
import com.ai.agent.domain.common.valobj.ChatResponse;
import com.ai.agent.domain.common.valobj.StreamEvent;
import com.ai.agent.domain.chat.service.strategy.RagDecorator;
import com.ai.agent.domain.knowledge.service.rag.RagService;
import com.ai.agent.types.enums.ChatMode;
import com.ai.agent.types.enums.EngineType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 聊天门面服务 — 统一入口，路由到对应策略，按需装饰RAG
 */
@Slf4j
@Service
public class ChatFacade {

    private final ModeRouter modeRouter;
    private final RagService ragService;
    private final ContextStoreFactory contextStoreFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final IChatSessionRepository chatSessionRepository;

    public ChatFacade(ModeRouter modeRouter, RagService ragService,
                      ContextStoreFactory contextStoreFactory,
                      ApplicationEventPublisher eventPublisher,
                      IChatSessionRepository chatSessionRepository) {
        this.modeRouter = modeRouter;
        this.ragService = ragService;
        this.contextStoreFactory = contextStoreFactory;
        this.eventPublisher = eventPublisher;
        this.chatSessionRepository = chatSessionRepository;
    }

    /**
     * 同步对话
     */
    public ChatResponse chat(ChatRequest request) {
        ChatStrategy strategy = modeRouter.route(request.getMode());

        if (Boolean.TRUE.equals(request.getRagEnabled())) {
            strategy = new RagDecorator(strategy, ragService);
        }

        return strategy.execute(request);
    }

    /**
     * 流式对话
     */
    public Flux<StreamEvent> chatStream(ChatRequest request) {
        ChatStrategy strategy = modeRouter.route(request.getMode());

        if (Boolean.TRUE.equals(request.getRagEnabled())) {
            strategy = new RagDecorator(strategy, ragService);
        }

        return strategy.executeStream(request);
    }

    /**
     * 移除会话上下文（会话清理）
     */
    public void removeSession(String sessionId) {
        contextStoreFactory.remove(sessionId);
    }

    /**
     * 创建新会话
     */
    public String createSession(String name) {
        String sessionId = UUID.randomUUID().toString();
        ChatSession session = ChatSession.builder()
                .sessionId(sessionId)
                .name(name)
                .userId("default")
                .mode(ChatMode.MULTI_TURN)
                .agentMode(EngineType.CHAT)
                .ragEnabled(false)
                .createdAt(LocalDateTime.now())
                .lastActiveAt(LocalDateTime.now())
                .build();
        chatSessionRepository.save(session);
        log.info("创建会话: sessionId={}, name={}", sessionId, name);
        return sessionId;
    }

    /**
     * 查询所有会话列表，附带最后一条消息摘要
     */
    public List<ChatSession> listSessions() {
        List<ChatSession> sessions = chatSessionRepository.findAll();
        // 为每个会话填充最后一条消息摘要
        for (ChatSession session : sessions) {
            List<ChatMessage> messages = chatSessionRepository.findMessagesBySessionId(session.getSessionId());
            if (!messages.isEmpty()) {
                ChatMessage lastMsg = messages.get(messages.size() - 1);
                String content = lastMsg.getContent();
                if (content != null && content.length() > 100) {
                    content = content.substring(0, 100) + "...";
                }
                session.setLastMessageSummary(content);
            }
        }
        return sessions;
    }
}
