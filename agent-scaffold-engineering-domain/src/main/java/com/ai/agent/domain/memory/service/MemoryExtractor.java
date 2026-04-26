package com.ai.agent.domain.memory.service;

import com.ai.agent.domain.knowledge.service.EmbeddingService;
import com.ai.agent.domain.memory.model.entity.MemoryItem;
import com.ai.agent.domain.memory.repository.IMemoryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 记忆提炼服务 — 每条消息异步提炼关键事实
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryExtractor {

    private final ChatModel chatModel;
    private final EmbeddingService embeddingService;
    private final IMemoryItemRepository memoryItemRepo;

    /**
     * 异步提炼消息中的关键事实
     *
     * @param sessionId 会话ID
     * @param msgId     来源消息ID（可为null）
     * @param role      消息角色
     * @param content   消息内容
     */
    @Async
    public void extractAsync(String sessionId, Long msgId, String role, String content) {
        try {
            // 1. LLM 提炼关键事实
            String prompt = """
                    从以下对话消息中提炼关键事实和信息。
                    规则：
                    - 每行一条事实，用简洁的陈述句
                    - 忽略寒暄、语气词、无实质内容的消息
                    - 如果消息没有可提炼的事实，输出 EMPTY
                    - 标注重要性 0-1（1=用户偏好/身份，0.5=任务相关，0=琐碎）

                    消息角色：%s
                    消息内容：%s

                    输出格式（每行一条）：
                    事实内容 | 重要性
                    """.formatted(role, content);

            String response = chatModel.call(prompt);
            if ("EMPTY".equalsIgnoreCase(response.trim())) {
                return;
            }

            // 2. 逐行解析 + 生成向量 + 存储
            List<MemoryItem> items = new ArrayList<>();
            for (String line : response.split("\n")) {
                String[] parts = line.strip().split("\\|");
                if (parts.length < 2) continue;

                String fact = parts[0].strip();
                float importance;
                try {
                    importance = Float.parseFloat(parts[1].strip());
                } catch (NumberFormatException e) {
                    importance = 0.5f;
                }
                float[] embedding = embeddingService.embed(fact);

                items.add(MemoryItem.builder()
                        .sessionId(sessionId)
                        .content(fact)
                        .embedding(embedding)
                        .importance(importance)
                        .tags(List.of("fact"))
                        .sourceMsgIds(msgId != null ? List.of(msgId) : List.of())
                        .timestamp(LocalDateTime.now())
                        .build());
            }
            if (!items.isEmpty()) {
                memoryItemRepo.saveBatch(items);
            }
        } catch (Exception e) {
            // 提炼失败静默降级，不影响主流程
            log.warn("记忆提炼失败 sessionId={} msgId={}: {}",
                    sessionId, msgId, e.getMessage());
        }
    }
}
