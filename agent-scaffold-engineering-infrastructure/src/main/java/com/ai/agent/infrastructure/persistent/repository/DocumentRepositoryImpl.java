package com.ai.agent.infrastructure.persistent.repository;

import com.ai.agent.domain.knowledge.model.entity.Document;
import com.ai.agent.domain.knowledge.repository.IDocumentRepository;
import com.ai.agent.infrastructure.persistent.dao.IDocumentDao;
import com.ai.agent.infrastructure.persistent.po.DocumentPO;
import com.ai.agent.types.enums.DocumentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DocumentRepositoryImpl implements IDocumentRepository {

    private final IDocumentDao documentDao;

    @Override
    public void save(Document document) {
        DocumentPO po = new DocumentPO();
        po.setDocId(document.getDocId());
        po.setBaseId(document.getBaseId());
        po.setFileName(document.getFileName());
        po.setFileType(document.getFileType());
        po.setStatus(document.getStatus().name());
        po.setChunkCount(document.getChunkCount());
        po.setCreatedAt(document.getCreatedAt());
        documentDao.insert(po);
    }

    @Override
    public Document findById(String docId) {
        DocumentPO po = documentDao.selectById(docId);
        if (po == null) {
            return null;
        }
        return toDocument(po);
    }

    @Override
    public List<Document> findByBaseId(String baseId) {
        List<DocumentPO> poList = documentDao.selectByBaseId(baseId);
        return poList.stream().map(this::toDocument).toList();
    }

    @Override
    public void updateStatus(String docId, DocumentStatus status) {
        documentDao.updateStatus(docId, status.name());
    }

    /**
     * DocumentPO -> Document 领域对象
     */
    private Document toDocument(DocumentPO po) {
        return Document.builder()
                .docId(po.getDocId())
                .baseId(po.getBaseId())
                .fileName(po.getFileName())
                .fileType(po.getFileType())
                .status(DocumentStatus.valueOf(po.getStatus()))
                .chunkCount(po.getChunkCount())
                .createdAt(po.getCreatedAt())
                .build();
    }

}
