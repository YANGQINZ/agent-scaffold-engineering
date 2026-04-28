package com.ai.agent.domain.memory.service;

import com.ai.agent.domain.common.event.MessageCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 记忆事件监听器 — 监听 chat 上下文发布的消息创建事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryEventListener {

    private final MemoryFacade memoryFacade;

    @EventListener
    public void onMessageCreated(MessageCreatedEvent event) {
        try {
            memoryFacade.onMessageCreated(event.sessionId(), event.content(), event.role());
        } catch (Exception e) {
            log.warn("记忆事件处理降级: sessionId={}, error={}", event.sessionId(), e.getMessage());
        }
    }
}
