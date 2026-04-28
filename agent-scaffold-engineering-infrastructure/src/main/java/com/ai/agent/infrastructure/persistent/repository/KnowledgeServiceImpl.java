package com.ai.agent.infrastructure.persistent.repository;

import com.ai.agent.api.IKnowledgeService;
import com.ai.agent.api.model.knowledge.KnowledgeBaseResponseDTO;
import com.ai.agent.domain.knowledge.model.aggregate.KnowledgeBase;
import com.ai.agent.domain.knowledge.model.entity.Document;
import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import com.ai.agent.domain.knowledge.repository.IDocumentChunkRepository;
import com.ai.agent.domain.knowledge.repository.IDocumentRepository;
import com.ai.agent.domain.knowledge.repository.IKnowledgeBaseRepository;
import com.ai.agent.domain.knowledge.service.DocumentProcessor;
import com.ai.agent.types.enums.DocumentStatus;
import com.ai.agent.types.enums.OwnerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 知识库服务实现 — 封装知识库创建与文档上传的业务逻辑
 * 实现 IKnowledgeService 接口，由 KnowledgeController 通过 API 层调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements IKnowledgeService {

    private final IKnowledgeBaseRepository knowledgeBaseRepository;
    private final IDocumentRepository documentRepository;
    private final IDocumentChunkRepository chunkRepository;
    private final DocumentProcessor documentProcessor;
    private final Tika tika = new Tika();

    @Override
    public KnowledgeBaseResponseDTO createKnowledgeBase(String name, String description,
                                                         String ownerType, String ownerId) {
        OwnerType parsedOwnerType = OwnerType.valueOf(ownerType);

        KnowledgeBase kb = KnowledgeBase.builder()
                .baseId(UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .ownerType(parsedOwnerType)
                .ownerId(ownerId)
                .createdAt(LocalDateTime.now())
                .build();
        knowledgeBaseRepository.save(kb);

        return KnowledgeBaseResponseDTO.builder()
                .baseId(kb.getBaseId())
                .name(kb.getName())
                .description(kb.getDescription())
                .docCount(0)
                .build();
    }

    @Override
    public String uploadDocument(String knowledgeBaseId, MultipartFile file, String userId) {
        String docId = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename();
        String fileType = fileName != null ? fileName.substring(fileName.lastIndexOf(".") + 1) : "txt";

        Document doc = Document.builder()
                .docId(docId)
                .baseId(knowledgeBaseId)
                .fileName(fileName)
                .fileType(fileType)
                .status(DocumentStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .build();
        documentRepository.save(doc);

        try {
            // 使用Apache Tika解析文档，支持PDF/Word/TXT等多种格式
            String content = tika.parseToString(file.getInputStream());
            List<DocumentChunk> chunks = documentProcessor.process(docId, knowledgeBaseId, content);
            chunkRepository.saveBatch(chunks);
            documentRepository.updateStatus(docId, DocumentStatus.COMPLETED);
            return docId;
        } catch (Exception e) {
            log.error("文档上传处理失败, docId={}, fileName={}", docId, fileName, e);
            try {
                documentRepository.updateStatus(docId, DocumentStatus.FAILED);
            } catch (Exception ex) {
                log.error("更新文档状态为FAILED失败, docId={}", docId, ex);
            }
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }
}
