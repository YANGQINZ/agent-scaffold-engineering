package com.ai.agent.domain.knowledge.service.business;

import com.ai.agent.domain.knowledge.model.aggregate.KnowledgeBase;
import com.ai.agent.domain.knowledge.model.entity.Document;
import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import com.ai.agent.domain.knowledge.model.entity.KnowledgeBaseResponse;
import com.ai.agent.domain.knowledge.repository.IDocumentChunkRepository;
import com.ai.agent.domain.knowledge.repository.IDocumentRepository;
import com.ai.agent.domain.knowledge.repository.IKnowledgeBaseRepository;
import com.ai.agent.domain.knowledge.DocumentProcessor;
import com.ai.agent.domain.knowledge.service.IKnowledgeService;
import com.ai.agent.types.enums.DocumentStatus;
import com.ai.agent.types.enums.OwnerType;
import com.ai.agent.types.exception.KnowledgeException;
import com.ai.agent.types.exception.enums.ErrorCodeEnum;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 知识库服务实现 — 封装知识库创建与文档上传的业务逻辑
 * 实现 IKnowledgeService 接口，由 KnowledgeController 通过 API 层调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements IKnowledgeService {

    private final TokenTextSplitter tokenTextSplitter;
    private final VectorStore vectorStore;
    private final IKnowledgeBaseRepository knowledgeBaseRepository;
    private final IDocumentRepository documentRepository;
    private final IDocumentChunkRepository chunkRepository;
    private final DocumentProcessor documentProcessor;
    private final Tika tika = new Tika();
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    @Override
    public KnowledgeBaseResponse createKnowledgeBase(String name, String description,
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

        return KnowledgeBaseResponse.builder()
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

    @Override
    public Map<String, Object> uploadKnowledgeBase(MultipartFile file, String name, String category) {
        // 1. 验证文件
        validateFile(file, MAX_FILE_SIZE, "知识库");

        TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
        List<org.springframework.ai.document.Document> documentList = tokenTextSplitter.apply(documentReader.get());

        // TODO

        // 添加知识库标签
        documentList.forEach(chunk -> chunk.getMetadata().put("kb_id", "knowledgeBaseId.toString()"));
        // 存储知识库文件
        vectorStore.accept(documentList);
        return Map.of();
    }

    /**
     * 验证文件基本属性（是否为空、文件大小）
     *
     * @param file 上传的文件
     * @param maxSizeBytes 最大文件大小（字节）
     * @param fileTypeName 文件类型名称（用于错误消息，如"简历"、"知识库"）
     */
    public void validateFile(MultipartFile file, long maxSizeBytes, String fileTypeName) {
        if (file.isEmpty()) {
            throw new KnowledgeException(ErrorCodeEnum.BAD_REQUEST,
                String.format("请选择要上传的%s文件", fileTypeName));
        }

        if (file.getSize() > maxSizeBytes) {
            throw new KnowledgeException(ErrorCodeEnum.BAD_REQUEST, "文件大小超过限制");
        }
    }
}
