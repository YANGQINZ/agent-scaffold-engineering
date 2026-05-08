package com.ai.agent.domain.memory.model.aggregate;

import com.ai.agent.domain.memory.model.valobj.HotContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MemoryContextTest {

    @Test
    public void testAppendMessage_underLimit_noTruncation() {
        MemoryContext ctx = MemoryContext.create("test-session", 5, 3);
        ctx.appendMessage("user", "hello", 2, new float[]{0.1f, 0.2f});
        ctx.appendMessage("assistant", "world", 2, new float[]{0.3f, 0.4f});
        assertEquals(2, ctx.getHotContext().getRecentMessages().size());
        assertEquals(4, ctx.getHotContext().getTokenCount());
    }

    @Test
    public void testAppendMessage_exceedsMaxRecent_truncatesToKeepRecent() {
        MemoryContext ctx = MemoryContext.create("test-session", 5, 3);
        for (int i = 0; i < 6; i++) {
            ctx.appendMessage("user", "msg" + i, 1, new float[]{0.1f});
        }
        assertEquals(3, ctx.getHotContext().getRecentMessages().size());
        assertEquals("msg3", ctx.getHotContext().getRecentMessages().get(0).getContent());
        assertEquals("msg5", ctx.getHotContext().getRecentMessages().get(2).getContent());
    }

    @Test
    public void testAppendMessage_tokenCountRecalculatedAfterTruncation() {
        MemoryContext ctx = MemoryContext.create("test-session", 5, 2);
        for (int i = 0; i < 6; i++) {
            ctx.appendMessage("user", "hello", 5, new float[]{0.1f});
        }
        assertEquals(2, ctx.getHotContext().getRecentMessages().size());
        assertTrue(ctx.getHotContext().getTokenCount() <= 10);
    }

    @Test
    public void testAppendMessage_exactlyAtMax_noTruncation() {
        MemoryContext ctx = MemoryContext.create("test-session", 5, 3);
        for (int i = 0; i < 5; i++) {
            ctx.appendMessage("user", "msg" + i, 1, new float[]{0.1f});
        }
        assertEquals(5, ctx.getHotContext().getRecentMessages().size());
    }

    @Test
    public void testAppendMessage_embeddingStored() {
        MemoryContext ctx = MemoryContext.create("test-session", 10, 3);
        float[] emb = new float[]{0.1f, 0.2f, 0.3f};
        ctx.appendMessage("user", "test message", 2, emb);
        assertArrayEquals(emb, ctx.getHotContext().getRecentMessages().get(0).getEmbedding(), 0.001f);
    }

    @Test
    public void testApplyCompression_usesEstimateTokens() {
        MemoryContext ctx = MemoryContext.create("test-session", 10, 3);
        ctx.appendMessage("user", "这是一条中文测试消息", 20, new float[]{0.1f});
        ctx.appendMessage("assistant", "这是回复内容", 15, new float[]{0.2f});
        ctx.appendMessage("user", "第三个问题", 10, new float[]{0.3f});
        ctx.applyCompression("这是压缩后的摘要", 1);
        assertEquals(1, ctx.getHotContext().getRecentMessages().size());
        assertNotNull(ctx.getHotContext().getContextSummary());
    }

    @Test
    public void testNeedsCompression() {
        MemoryContext ctx = MemoryContext.create("test-session", 200, 100);
        ctx.appendMessage("user", "a", 1, new float[]{0.1f});
        assertFalse(ctx.needsCompression(100, 0.8f));
        for (int i = 0; i < 99; i++) {
            ctx.appendMessage("user", "a", 1, new float[]{0.1f});
        }
        assertTrue(ctx.needsCompression(100, 0.8f));
    }

    @Test
    public void testCreate_emptyHotContext() {
        MemoryContext ctx = MemoryContext.create("session-1", 10, 3);
        assertEquals("session-1", ctx.getSessionId());
        assertNotNull(ctx.getHotContext());
        assertEquals(0, ctx.getHotContext().getRecentMessages().size());
        assertEquals(0, ctx.getHotContext().getTokenCount());
    }
}
