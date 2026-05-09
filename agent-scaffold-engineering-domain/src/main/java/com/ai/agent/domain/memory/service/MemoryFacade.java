package com.ai.agent.domain.memory.service;

import com.ai.agent.domain.chat.model.entity.ChatMessage;
import com.ai.agent.domain.chat.repository.IChatSessionRepository;
import com.ai.agent.domain.common.interface_.MemoryPort;
import com.ai.agent.domain.knowledge.service.EmbeddingService;
import com.ai.agent.domain.memory.model.aggregate.MemoryContext;
import com.ai.agent.domain.memory.model.valobj.HotContext;
import com.ai.agent.domain.memory.repository.IHotContextRepository;
import com.ai.agent.types.enums.MessageRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆系统门面 — 统一入口（写入/组装/压缩）
 * 实现 MemoryPort 接口
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryFacade implements MemoryPort {

    private final IHotContextRepository hotContextRepo;
    private final IChatSessionRepository chatSessionRepo;
    private final MemoryExtractor memoryExtractor;
    private final ContextCompressor contextCompressor;
    private final ContextAssembler contextAssembler;
    private final EmbeddingService embeddingService;

    @Value("${memory.hot.max-recent-messages:10}")
    private int maxRecentMessages;

    @Value("${memory.compression.keep-recent:3}")
    private int keepRecentN;

    /** 嵌入最大字符数（DashScope text-embedding-v3 上限约 8192 token，保守截断） */
    private static final int EMBED_MAX_CHARS = 6000;

    @Override
    public void onMessageCreated(String sessionId, String content, String role) {
        if (sessionId == null || sessionId.isBlank() || content == null || content.isBlank()) {
            log.debug("onMessageCreated 跳过: sessionId={}, content为空", sessionId);
            return;
        }
        if (role == null || role.isBlank()) {
            role = "assistant";
        }
        int estimatedTokens = HotContext.estimateTokens(content);
        // 截断过长内容以避免嵌入 API 400 错误（内容用于嵌入检索，截断不影响语义召回）
        String embedContent = content.length() > EMBED_MAX_CHARS
                ? content.substring(0, EMBED_MAX_CHARS) : content;
        float[] embedding = embeddingService.embed(embedContent);

        // 1. 冷层写入 chat_message
        ChatMessage msg = ChatMessage.builder()
                .sessionId(sessionId)
                .role(MessageRole.valueOf(role.toUpperCase()))
                .content(content)
                .tokenCount(estimatedTokens)
                .createdAt(LocalDateTime.now())
                .build();
        chatSessionRepo.saveMessage(msg);

        // 2. 热层操作
        HotContext hotCtx = hotContextRepo.load(sessionId);
        MemoryContext memoryContext = hotCtx != null
                ? MemoryContext.from(sessionId, hotCtx, maxRecentMessages, keepRecentN)
                : MemoryContext.create(sessionId, maxRecentMessages, keepRecentN);
        memoryContext.appendMessage(role, content, estimatedTokens, embedding);
        hotContextRepo.save(memoryContext.getHotContext());

        // 3. 异步处理
        Long msgId = msg.getMessageId() != null ? Long.valueOf(msg.getMessageId()) : null;
        memoryExtractor.extractAsync(sessionId, msgId, role, content);
        contextCompressor.checkAndCompress(sessionId, memoryContext);
    }

    @Override
    public List<Message> assembleContext(String sessionId, String userQuery) {
        return contextAssembler.assemble(sessionId, userQuery);
    }
}
