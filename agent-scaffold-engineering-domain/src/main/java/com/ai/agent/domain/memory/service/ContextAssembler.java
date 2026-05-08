package com.ai.agent.domain.memory.service;

import com.ai.agent.domain.knowledge.service.EmbeddingService;
import com.ai.agent.domain.memory.model.entity.MemoryItem;
import com.ai.agent.domain.memory.model.valobj.HotContext;
import com.ai.agent.domain.memory.repository.IHotContextRepository;
import com.ai.agent.domain.memory.repository.IMemoryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文组装服务 — 热层上下文（近期保留 + 旧消息过滤）+ 冷层语义检索 → 完整 Prompt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextAssembler {

    private final IHotContextRepository hotContextRepo;
    private final IMemoryItemRepository memoryItemRepo;
    private final EmbeddingService embeddingService;

    @Value("${memory.assembly.max-memory-tokens:2000}")
    private int maxMemoryTokens;

    @Value("${memory.compression.keep-recent:3}")
    private int keepRecentN;

    @Value("${memory.hot.similarity-threshold:0.5}")
    private float similarityThreshold;

    /**
     * 组装完整上下文，返回 Spring AI Message 列表
     */
    public List<Message> assemble(String sessionId, String userQuery) {
        List<Message> messages = new ArrayList<>();

        // 1. 热层：全局摘要（如果有）→ SystemMessage
        HotContext ctx = hotContextRepo.load(sessionId);
        if (ctx != null && ctx.getContextSummary() != null
                && !ctx.getContextSummary().isBlank()) {
            messages.add(new SystemMessage("历史对话摘要：\n" + ctx.getContextSummary()));
        }

        // 2. 冷层：语义检索相关记忆（≤maxMemoryTokens，过滤 summary）→ SystemMessage
        float[] queryEmbedding = null;
        try {
            queryEmbedding = embeddingService.embed(userQuery);
            List<MemoryItem> relevantMemories =
                    memoryItemRepo.searchBySimilarity(sessionId, queryEmbedding, 10);

            int memoryTokens = 0;
            StringBuilder memoryText = new StringBuilder("相关历史记忆：\n");
            for (MemoryItem item : relevantMemories) {
                if (item.getTags() != null && item.getTags().contains("summary")) {
                    continue;
                }
                int est = HotContext.estimateTokens(item.getContent());
                if (memoryTokens + est > maxMemoryTokens) {
                    break;
                }
                memoryText.append("- ").append(item.getContent()).append("\n");
                memoryTokens += est;
            }
            if (memoryTokens > 0) {
                messages.add(new SystemMessage(memoryText.toString()));
            }
        } catch (Exception e) {
            log.warn("语义检索失败 sessionId={}: {}", sessionId, e.getMessage());
        }

        // 3. 热层：近期消息无条件注入 + 旧消息 embedding 相似度过滤
        if (ctx != null && ctx.getRecentMessages() != null) {
            List<HotContext.MessageEntry> allMsgs = ctx.getRecentMessages();
            int splitIndex = Math.max(0, allMsgs.size() - keepRecentN);

            for (int i = 0; i < allMsgs.size(); i++) {
                HotContext.MessageEntry entry = allMsgs.get(i);

                // 旧消息：embedding 相似度过滤
                if (i < splitIndex) {
                    if (queryEmbedding == null || entry.getEmbedding() == null) {
                        // embedding 不可用时跳过旧消息
                        continue;
                    }
                    double similarity = cosineSimilarity(queryEmbedding, entry.getEmbedding());
                    if (similarity < similarityThreshold) {
                        log.debug("跳过不相关的旧消息: index={}, similarity={}", i, String.format("%.2f", similarity));
                        continue;
                    }
                }

                // 注入消息
                switch (entry.getRole().toUpperCase()) {
                    case "USER" -> messages.add(new UserMessage(entry.getContent()));
                    case "ASSISTANT" -> messages.add(new AssistantMessage(entry.getContent()));
                    case "SYSTEM" -> messages.add(new SystemMessage(entry.getContent()));
                }
            }
        }

        return messages;
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
