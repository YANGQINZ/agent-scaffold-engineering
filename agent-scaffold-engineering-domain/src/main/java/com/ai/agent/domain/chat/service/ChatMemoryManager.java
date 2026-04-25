package com.ai.agent.domain.chat.service;

import com.ai.agent.domain.chat.model.entity.ChatMessage;
import com.ai.agent.domain.chat.repository.IChatSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天记忆管理器 — 管理对话历史的内存缓存与持久化
 */
@Slf4j
@Service
public class ChatMemoryManager {

    /** 每个会话最大保存的对话轮数 */
    private static final int MAX_ROUNDS_PER_SESSION = 20;

    private final IChatSessionRepository chatSessionRepository;

    /** 内存缓存：sessionId -> 消息列表 */
    private final ConcurrentHashMap<String, LinkedList<ChatMessage>> cache = new ConcurrentHashMap<>();

    public ChatMemoryManager(IChatSessionRepository chatSessionRepository) {
        this.chatSessionRepository = chatSessionRepository;
    }

    /**
     * 加载会话历史消息 — 先查缓存，未命中则从DB加载并回填缓存
     */
    public List<ChatMessage> load(String sessionId) {
        LinkedList<ChatMessage> cached = cache.get(sessionId);
        if (cached != null) {
            return Collections.unmodifiableList(cached);
        }

        // 缓存未命中，从DB加载
        List<ChatMessage> messages = chatSessionRepository.findMessagesBySessionId(sessionId);
        LinkedList<ChatMessage> linkedList = new LinkedList<>(messages);
        cache.put(sessionId, linkedList);
        return Collections.unmodifiableList(linkedList);
    }

    /**
     * 保存消息 — 写入缓存 + 异步持久化
     */
    public void save(ChatMessage message) {
        LinkedList<ChatMessage> messages = cache.computeIfAbsent(
                message.getSessionId(), k -> new LinkedList<>()
        );

        messages.addLast(message);

        // 超过最大轮数的2倍时淘汰最旧的消息（2倍是因为每轮包含USER+ASSISTANT两条消息）
        int maxMessages = MAX_ROUNDS_PER_SESSION * 2;
        while (messages.size() > maxMessages) {
            // LRU淘汰时先将最旧消息写回数据库再移除，防止数据丢失
            ChatMessage evicted = messages.removeFirst();
            asyncPersistMessage(evicted);
        }

        // 异步持久化
        asyncPersistMessage(message);
    }

    /**
     * 异步持久化消息
     */
    @Async
    public void asyncPersistMessage(ChatMessage message) {
        try {
            chatSessionRepository.saveMessage(message);
        } catch (Exception e) {
            log.error("异步持久化消息失败, sessionId={}, messageId={}: {}",
                    message.getSessionId(), message.getMessageId(), e.getMessage(), e);
        }
    }

}
