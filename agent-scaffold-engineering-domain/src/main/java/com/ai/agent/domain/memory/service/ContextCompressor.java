package com.ai.agent.domain.memory.service;

import com.ai.agent.domain.knowledge.service.EmbeddingService;
import com.ai.agent.domain.memory.model.aggregate.MemoryContext;
import com.ai.agent.domain.memory.model.entity.MemoryItem;
import com.ai.agent.domain.memory.repository.IHotContextRepository;
import com.ai.agent.domain.memory.repository.IMemoryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 上下文压缩服务 — token 超限时 LLM 生成摘要 + 保留最近N条
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextCompressor {

    private final ChatModel chatModel;
    private final EmbeddingService embeddingService;
    private final IHotContextRepository hotContextRepo;
    private final IMemoryItemRepository memoryItemRepo;

    /** 压缩触发阈值（占上下文窗口比例） */
    @Value("${memory.compression.threshold:0.8}")
    private float compressionThreshold;

    /** LLM 上下文窗口大小（tokens） */
    @Value("${memory.compression.context-window:32768}")
    private int contextWindowTokens;

    /** 压缩后保留最近N条消息 */
    @Value("${memory.compression.keep-recent:10}")
    private int keepRecentN;

    /**
     * 检查并执行压缩 — 通过聚合根判断和执行
     *
     * @param sessionId     会话ID
     * @param memoryContext 记忆上下文聚合根
     */
    public void checkAndCompress(String sessionId, MemoryContext memoryContext) {
        // 1. 通过聚合根判断是否需要压缩
        if (!memoryContext.needsCompression(contextWindowTokens, compressionThreshold)) {
            return;
        }

        log.info("触发上下文压缩 sessionId={} tokenCount={}/{}",
                sessionId, memoryContext.getTokenCount(), contextWindowTokens);

        try {
            // 2. 收集所有消息文本
            String allMessages = memoryContext.getHotContext().getRecentMessages().stream()
                    .map(m -> m.getRole() + ": " + m.getContent())
                    .collect(Collectors.joining("\n"));

            // 3. LLM 生成全局摘要
            String summaryPrompt = """
                    请将以下对话历史压缩为一段简洁的全局摘要。
                    保留所有关键信息、决策、用户偏好和当前任务进展。
                    不要丢失任何重要事实。

                    对话历史：
                    %s
                    """.formatted(allMessages);

            String newSummary = chatModel.call(summaryPrompt);

            // 4. 通过聚合根应用压缩结果
            memoryContext.applyCompression(newSummary, keepRecentN);
            hotContextRepo.save(memoryContext.getHotContext());

            // 5. 冷层写入摘要作为 memory 记录
            float[] summaryEmbedding = embeddingService.embed(newSummary);
            memoryItemRepo.save(MemoryItem.builder()
                    .sessionId(sessionId)
                    .content(newSummary)
                    .embedding(summaryEmbedding)
                    .importance(0.9f)
                    .tags(List.of("summary"))
                    .timestamp(LocalDateTime.now())
                    .metadata(Map.of("compressedTokenCount", memoryContext.getTokenCount()))
                    .build());
        } catch (Exception e) {
            log.error("上下文压缩失败 sessionId={}: {}", sessionId, e.getMessage(), e);
            // 压缩失败时热层继续保留全量消息，不中断主流程
        }
    }
}
