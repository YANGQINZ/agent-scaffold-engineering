package com.ai.agent.domain.common.interface_;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 记忆系统暴露的端口接口
 * 由 memory 上下文的 MemoryFacade 实现
 */
public interface MemoryPort {

    void onMessageCreated(String sessionId, String content, String role);

    List<Message> assembleContext(String sessionId, String query);
}
