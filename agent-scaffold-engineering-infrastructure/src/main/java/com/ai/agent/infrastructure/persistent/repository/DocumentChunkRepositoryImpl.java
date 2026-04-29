package com.ai.agent.infrastructure.persistent.repository;

import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import com.ai.agent.domain.knowledge.repository.IDocumentChunkRepository;
import com.ai.agent.infrastructure.persistent.dao.IDocumentChunkDao;
import com.ai.agent.infrastructure.persistent.po.DocumentChunkPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档分块仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DocumentChunkRepositoryImpl implements IDocumentChunkRepository {

    private final IDocumentChunkDao documentChunkDao;

    @Override
    public void saveBatch(List<DocumentChunk> chunks) {
        List<DocumentChunkPO> poList = chunks.stream().map(this::toPO).toList();
        documentChunkDao.insertBatch(poList);
    }

    @Override
    public List<DocumentChunk> vectorSearch(String baseId, float[] queryEmbedding, int topK) {
        String embeddingStr = embeddingToString(queryEmbedding);
        List<DocumentChunkPO> poList = documentChunkDao.vectorSearch(baseId, embeddingStr, topK);
        return poList.stream().map(this::toChunk).toList();
    }

    @Override
    public List<DocumentChunk> bm25Search(String baseId, String query, int topK) {
        List<DocumentChunkPO> poList = documentChunkDao.bm25Search(baseId, query, topK);
        return poList.stream().map(this::toChunk).toList();
    }

    @Override
    public List<DocumentChunk> findByBaseId(String baseId) {
        List<DocumentChunkPO> poList = documentChunkDao.selectByBaseId(baseId);
        return poList.stream().map(this::toChunk).toList();
    }

    @Override
    public void deleteByBaseId(String baseId) {
        documentChunkDao.deleteByBaseId(baseId);
    }

    /**
     * DocumentChunk 领域对象 -> DocumentChunkPO
     */
    private DocumentChunkPO toPO(DocumentChunk chunk) {
        DocumentChunkPO po = new DocumentChunkPO();
        po.setId(chunk.getId());
        po.setContent(chunk.getContent());
        if (chunk.getEmbedding() != null) {
            po.setEmbedding(embeddingToString(chunk.getEmbedding()));
        }
        po.setMetadata(chunk.getMetadata());
        return po;
    }

    /**
     * DocumentChunkPO -> DocumentChunk 领域对象
     */
    private DocumentChunk toChunk(DocumentChunkPO po) {
        return DocumentChunk.builder()
                .id(po.getId())
                .content(po.getContent())
                .metadata(po.getMetadata())
                .score(po.getScore())
                .build();
    }

    /**
     * float[] 向量嵌入 -> PGvector 字符串格式
     */
    private String embeddingToString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

}
