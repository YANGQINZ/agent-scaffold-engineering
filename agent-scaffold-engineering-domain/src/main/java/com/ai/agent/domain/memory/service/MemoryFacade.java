package com.ai.agent.domain.memory.service;

import com.ai.agent.domain.chat.model.entity.ChatMessage;
import com.ai.agent.domain.chat.repository.IChatSessionRepository;
import com.ai.agent.domain.memory.model.aggregate.MemoryContext;
import com.ai.agent.domain.memory.model.valobj.HotContext;
import com.ai.agent.domain.memory.repository.IHotContextRepository;
import com.ai.agent.types.enums.MessageRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆系统门面 — 统一入口（写入/组装/压缩）
 * 替代 ChatMemoryManager
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryFacade {

    private final IHotContextRepository hotContextRepo;
    private final IChatSessionRepository chatSessionRepo;
    private final MemoryExtractor memoryExtractor;
    private final ContextCompressor contextCompressor;
    private final ContextAssembler contextAssembler;

    /**
     * 写入消息（替代 ChatMemoryManager.save）
     *
     * @param sessionId 会话ID
     * @param role      消息角色（user/assistant/system）
     * @param content   消息内容
     * @param tokenCount 消息token数
     */
    public void appendMessage(String sessionId, String role,
                              String content, int tokenCount) {
        // 1. 冷层写入 chat_message（同步，现有接口）
        ChatMessage msg = ChatMessage.builder()
                .sessionId(sessionId)
                .role(MessageRole.valueOf(role.toUpperCase()))
                .content(content)
                .tokenCount(tokenCount)
                .createdAt(LocalDateTime.now())
                .build();
        chatSessionRepo.saveMessage(msg);

        // 2. 通过聚合根操作热层
        HotContext hotCtx = hotContextRepo.load(sessionId);
        MemoryContext memoryContext = hotCtx != null
                ? MemoryContext.from(sessionId, hotCtx)
                : MemoryContext.create(sessionId);
        memoryContext.appendMessage(role, content, tokenCount);
        hotContextRepo.save(memoryContext.getHotContext());

        // 3. 后台异步：记忆提炼 + 压缩检查
        Long msgId = msg.getMessageId() != null ? Long.valueOf(msg.getMessageId()) : null;
        memoryExtractor.extractAsync(sessionId, msgId, role, content);
        contextCompressor.checkAndCompress(sessionId, memoryContext);
    }

    /**
     * 组装上下文（替代 ChatMemoryManager.load）
     *
     * @param sessionId 会话ID
     * @param userQuery 用户当前提问（用于语义检索）
     * @return Spring AI Message 列表
     */
    public List<Message> assembleContext(String sessionId, String userQuery) {
        return contextAssembler.assemble(sessionId, userQuery);
    }
}
