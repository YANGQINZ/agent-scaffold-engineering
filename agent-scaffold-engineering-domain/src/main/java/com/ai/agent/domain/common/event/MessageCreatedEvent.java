package com.ai.agent.domain.common.event;

import java.time.Instant;

/**
 * 消息创建事件 — 由 chat 上下文发布，memory 上下文监听
 * 只包含基本类型，不引用任何上下文的实体类
 */
public record MessageCreatedEvent(
        String sessionId,
        String content,
        String role,
        Instant timestamp
) implements DomainEvent {
}
