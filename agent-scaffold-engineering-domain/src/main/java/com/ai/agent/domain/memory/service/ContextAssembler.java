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
 * 上下文组装服务 — 热层上下文 + 冷层语义检索 → 完整 Prompt
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

    /**
     * 组装完整上下文，返回 Spring AI Message 列表
     * 供 ChatStrategy 直接传入 LLM
     *
     * @param sessionId 会话ID
     * @param userQuery 用户当前提问（用于语义检索）
     * @return Spring AI Message 列表
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
        try {
            float[] queryEmbedding = embeddingService.embed(userQuery);
            List<MemoryItem> relevantMemories =
                    memoryItemRepo.searchBySimilarity(sessionId, queryEmbedding, 10);

            int memoryTokens = 0;
            StringBuilder memoryText = new StringBuilder("相关历史记忆：\n");
            for (MemoryItem item : relevantMemories) {
                // 过滤 summary 类型，只保留 fact 类型
                if (item.getTags() != null && item.getTags().contains("summary")) {
                    continue;
                }
                int est = item.getContent().length() / 4;
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
            // 语义检索失败时跳过冷层记忆，仅使用热层上下文
            log.warn("语义检索失败 sessionId={}: {}", sessionId, e.getMessage());
        }

        // 3. 热层：最近消息 → UserMessage/AssistantMessage
        if (ctx != null && ctx.getRecentMessages() != null) {
            for (HotContext.MessageEntry entry : ctx.getRecentMessages()) {
                switch (entry.getRole().toUpperCase()) {
                    case "USER" -> messages.add(new UserMessage(entry.getContent()));
                    case "ASSISTANT" -> messages.add(new AssistantMessage(entry.getContent()));
                    case "SYSTEM" -> messages.add(new SystemMessage(entry.getContent()));
                }
            }
        }

        return messages;
    }
}
