package com.ai.agent.infrastructure;

import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import com.ai.agent.infrastructure.persistent.dao.IDocumentChunkDao;
import com.ai.agent.infrastructure.persistent.po.DocumentChunkPO;
import com.ai.agent.infrastructure.persistent.repository.DocumentChunkRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DocumentChunkRepositoryImpl 单元测试
 * 验证领域对象与PO之间的映射（新schema：UUID id, metadata包含baseId/docId/chunkIndex）
 */
class DocumentChunkRepositoryImplTest {

    private IDocumentChunkDao dao;
    private DocumentChunkRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        dao = mock(IDocumentChunkDao.class);
        repository = new DocumentChunkRepositoryImpl(dao);
    }

    @Test
    void saveBatch_mapsDomainToPO_correctly() {
        DocumentChunk chunk = DocumentChunk.builder()
                .id("uuid-1")
                .content("测试内容")
                .embedding(new float[]{0.1f, 0.2f, 0.3f})
                .metadata("{\"baseId\":\"base-1\",\"docId\":\"doc-1\",\"chunkIndex\":0}")
                .build();

        repository.saveBatch(List.of(chunk));

        verify(dao).insertBatch(argThat(list -> {
            DocumentChunkPO po = list.get(0);
            assertEquals("uuid-1", po.getId());
            assertEquals("测试内容", po.getContent());
            assertEquals("[0.1,0.2,0.3]", po.getEmbedding());
            assertEquals("{\"baseId\":\"base-1\",\"docId\":\"doc-1\",\"chunkIndex\":0}", po.getMetadata());
            return true;
        }));
    }

    @Test
    void saveBatch_nullEmbedding_doesNotFail() {
        DocumentChunk chunk = DocumentChunk.builder()
                .id("uuid-2")
                .content("无向量内容")
                .embedding(null)
                .metadata("{\"baseId\":\"base-1\"}")
                .build();

        repository.saveBatch(List.of(chunk));

        verify(dao).insertBatch(argThat(list -> {
            assertNull(list.get(0).getEmbedding());
            return true;
        }));
    }

    @Test
    void vectorSearch_mapsPOtoDomain_correctly() {
        DocumentChunkPO po = new DocumentChunkPO();
        po.setId("uuid-3");
        po.setContent("搜索结果内容");
        po.setMetadata("{\"baseId\":\"base-1\",\"docId\":\"doc-1\"}");
        po.setScore(0.95);

        when(dao.vectorSearch(eq("base-1"), anyString(), eq(5))).thenReturn(List.of(po));

        List<DocumentChunk> result = repository.vectorSearch("base-1", new float[]{0.1f, 0.2f}, 5);

        assertEquals(1, result.size());
        assertEquals("uuid-3", result.get(0).getId());
        assertEquals("搜索结果内容", result.get(0).getContent());
        assertEquals(0.95, result.get(0).getScore());
        assertNull(result.get(0).getEmbedding()); // PO不返回embedding
    }

    @Test
    void bm25Search_mapsPOtoDomain_correctly() {
        DocumentChunkPO po = new DocumentChunkPO();
        po.setId("uuid-4");
        po.setContent("BM25搜索结果");
        po.setMetadata("{\"baseId\":\"base-2\"}");
        po.setScore(0.8);

        when(dao.bm25Search("base-2", "测试查询", 3)).thenReturn(List.of(po));

        List<DocumentChunk> result = repository.bm25Search("base-2", "测试查询", 3);

        assertEquals(1, result.size());
        assertEquals("uuid-4", result.get(0).getId());
        assertEquals("BM25搜索结果", result.get(0).getContent());
        assertEquals(0.8, result.get(0).getScore());
    }
}
