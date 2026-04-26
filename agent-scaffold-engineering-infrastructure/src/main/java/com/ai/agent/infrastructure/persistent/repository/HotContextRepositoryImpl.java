package com.ai.agent.infrastructure.persistent.repository;

import com.ai.agent.domain.chat.model.entity.ChatMessage;
import com.ai.agent.domain.chat.repository.IChatSessionRepository;
import com.ai.agent.domain.memory.model.entity.MemoryItem;
import com.ai.agent.domain.memory.model.valobj.HotContext;
import com.ai.agent.domain.memory.repository.IHotContextRepository;
import com.ai.agent.domain.memory.repository.IMemoryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 热层上下文仓储实现（Redis + DB fallback）
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class HotContextRepositoryImpl implements IHotContextRepository {

    private final RedissonClient redissonClient;
    private final IChatSessionRepository chatSessionRepo;
    private final IMemoryItemRepository memoryItemRepo;

    private static final String KEY_PREFIX = "ctx:";

    @Value("${memory.hot.ttl-hours:2}")
    private int ttlHours;

    @Value("${memory.compression.keep-recent:10}")
    private int keepRecentN;

    @Override
    public HotContext load(String sessionId) {
        try {
            RBucket<HotContext> bucket = redissonClient.getBucket(KEY_PREFIX + sessionId);
            HotContext ctx = bucket.get();
            if (ctx != null) {
                return ctx;
            }
        } catch (Exception e) {
            log.warn("Redis 读取失败 sessionId={}，降级为 DB 查询: {}", sessionId, e.getMessage());
        }

        // Cache-Aside: Redis miss → DB 重建
        HotContext ctx = rebuildFromDB(sessionId);
        if (ctx != null) {
            try {
                RBucket<HotContext> bucket = redissonClient.getBucket(KEY_PREFIX + sessionId);
                bucket.set(ctx, Duration.ofHours(ttlHours));
            } catch (Exception e) {
                log.warn("Redis 写回失败 sessionId={}: {}", sessionId, e.getMessage());
            }
        }
        return ctx;
    }

    @Override
    public void save(HotContext context) {
        try {
            RBucket<HotContext> bucket = redissonClient.getBucket(KEY_PREFIX + context.getSessionId());
            bucket.set(context, Duration.ofHours(ttlHours));
        } catch (Exception e) {
            log.error("Redis 保存热层上下文失败 sessionId={}: {}", context.getSessionId(), e.getMessage());
        }
    }

    @Override
    public void delete(String sessionId) {
        try {
            RBucket<HotContext> bucket = redissonClient.getBucket(KEY_PREFIX + sessionId);
            bucket.delete();
        } catch (Exception e) {
            log.warn("Redis 删除热层上下文失败 sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    @Override
    public boolean exists(String sessionId) {
        try {
            RBucket<HotContext> bucket = redissonClient.getBucket(KEY_PREFIX + sessionId);
            return bucket.isExists();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 DB 重建热层上下文
     */
    private HotContext rebuildFromDB(String sessionId) {
        // 1. 从 chat_message 读最近 N 条消息
        List<ChatMessage> messages = chatSessionRepo.findMessagesBySessionId(sessionId);
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        List<HotContext.MessageEntry> recentEntries = new ArrayList<>();
        int fromIndex = Math.max(0, messages.size() - keepRecentN);
        for (int i = fromIndex; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            recentEntries.add(HotContext.MessageEntry.builder()
                    .role(msg.getRole().name().toLowerCase())
                    .content(msg.getContent())
                    .build());
        }

        // 2. 从 memory_item 读最新摘要
        String summary = null;
        try {
            MemoryItem latestSummary = memoryItemRepo.findLatestSummary(sessionId);
            if (latestSummary != null) {
                summary = latestSummary.getContent();
            }
        } catch (Exception e) {
            log.warn("查询最新摘要失败 sessionId={}: {}", sessionId, e.getMessage());
        }

        // 3. 组装 HotContext
        int tokenCount = recentEntries.stream()
                .mapToInt(m -> m.getContent() != null ? m.getContent().length() / 4 : 0)
                .sum();
        if (summary != null) {
            tokenCount += summary.length() / 4;
        }

        return HotContext.builder()
                .sessionId(sessionId)
                .recentMessages(recentEntries)
                .contextSummary(summary)
                .recentToolResults(new ArrayList<>())
                .tokenCount(tokenCount)
                .lastUpdated(LocalDateTime.now())
                .build();
    }
}
