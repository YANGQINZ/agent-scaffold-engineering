package com.ai.agent.domain.memory.model.valobj;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HotContextTest {

    @Test
    public void testEstimateTokens_pureChinese() {
        String text = "你好世界";
        assertEquals(8, HotContext.estimateTokens(text)); // 4字 * 2 = 8
    }

    @Test
    public void testEstimateTokens_pureEnglish() {
        String text = "Hello World!";
        assertEquals(3, HotContext.estimateTokens(text)); // 12 / 4 = 3
    }

    @Test
    public void testEstimateTokens_mixed() {
        String text = "你好World";
        long chineseCount = text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        long otherCount = text.length() - chineseCount;
        int expected = (int)(chineseCount * 2 + otherCount / 4);
        assertEquals(expected, HotContext.estimateTokens(text));
    }

    @Test
    public void testEstimateTokens_emptyOrNull() {
        assertEquals(0, HotContext.estimateTokens(null));
        assertEquals(0, HotContext.estimateTokens(""));
    }

    @Test
    public void testEstimateTokens_longerMixed() {
        String text = "这是一个测试消息，包含multiple languages混合内容";
        long chineseCount = text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        long otherCount = text.length() - chineseCount;
        int expected = (int)(chineseCount * 2 + otherCount / 4);
        assertEquals(expected, HotContext.estimateTokens(text));
    }
}
