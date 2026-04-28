package com.ai.agent.domain.knowledge.service;

import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MmrServiceTest {

    private final MmrService mmrService = new MmrService();

    @Test
    void rerankByMmr_nullChunks_returnsEmptyList() {
        List<DocumentChunk> result = mmrService.rerankByMmr(new float[]{1f, 0f}, null, 0.7, 5);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void rerankByMmr_emptyChunks_returnsEmptyList() {
        List<DocumentChunk> result = mmrService.rerankByMmr(new float[]{1f, 0f}, Collections.emptyList(), 0.7, 5);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void rerankByMmr_zeroVectorEmbedding_returnsExpectedCount() {
        // 零向量 embedding 不应导致 NaN 传播使算法提前中断
        float[] queryEmb = new float[]{1.0f, 0.0f};
        DocumentChunk chunk = DocumentChunk.builder()
                .chunkId(1L)
                .content("测试")
                .embedding(new float[]{0.0f, 0.0f})
                .build();

        List<DocumentChunk> result = mmrService.rerankByMmr(queryEmb, List.of(chunk), 0.7, 5);
        assertNotNull(result);
        // 至少应返回传入的 chunk
        assertEquals(1, result.size());
    }

    @Test
    void rerankByMmr_allZeroVectors_returnsExpectedCount() {
        // 所有向量（包括 query）都是零向量时，不应因 NaN 导致结果被截断
        float[] queryEmb = new float[]{0.0f, 0.0f};
        DocumentChunk c1 = DocumentChunk.builder().chunkId(1L).content("a").embedding(new float[]{0.0f, 0.0f}).build();
        DocumentChunk c2 = DocumentChunk.builder().chunkId(2L).content("b").embedding(new float[]{0.0f, 0.0f}).build();

        List<DocumentChunk> result = mmrService.rerankByMmr(queryEmb, List.of(c1, c2), 0.7, 2);
        assertNotNull(result);
        // 2 个有效 chunk 请求 topK=2，应返回 2 个（不因 NaN 提前中断为 1 个）
        assertEquals(2, result.size(), "零向量不应导致 MMR 提前中断，应返回请求的 topK 数量");
    }

    @Test
    void rerankByMmr_mixedZeroAndNormalVectors_returnsExpectedCount() {
        // 混合零向量与正常向量不应导致结果截断
        float[] queryEmb = new float[]{1.0f, 0.0f};
        DocumentChunk normalChunk = DocumentChunk.builder()
                .chunkId(1L)
                .content("正常")
                .embedding(new float[]{1.0f, 0.0f})
                .build();
        DocumentChunk zeroChunk = DocumentChunk.builder()
                .chunkId(2L)
                .content("零向量")
                .embedding(new float[]{0.0f, 0.0f})
                .build();

        List<DocumentChunk> result = mmrService.rerankByMmr(queryEmb, List.of(normalChunk, zeroChunk), 0.7, 2);
        assertNotNull(result);
        assertEquals(2, result.size(), "混合零向量不应导致结果截断");
    }

    @Test
    void rerankByMmr_normalVectors_returnsRanked() {
        float[] queryEmb = new float[]{1.0f, 0.0f};
        DocumentChunk chunk1 = DocumentChunk.builder()
                .chunkId(1L)
                .content("最相似")
                .embedding(new float[]{0.9f, 0.1f})
                .build();
        DocumentChunk chunk2 = DocumentChunk.builder()
                .chunkId(2L)
                .content("次相似")
                .embedding(new float[]{0.5f, 0.5f})
                .build();

        List<DocumentChunk> result = mmrService.rerankByMmr(queryEmb, List.of(chunk1, chunk2), 0.7, 2);
        assertNotNull(result);
        assertEquals(2, result.size());
        // 最相似的应排在第一位
        assertEquals(1L, result.get(0).getChunkId());
    }
}
