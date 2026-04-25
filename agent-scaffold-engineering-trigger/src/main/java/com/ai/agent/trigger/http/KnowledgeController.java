package com.ai.agent.trigger.http;

import com.ai.agent.api.model.knowledge.KnowledgeBaseResponseDTO;
import com.ai.agent.domain.knowledge.model.aggregate.KnowledgeBase;
import com.ai.agent.domain.knowledge.model.entity.Document;
import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import com.ai.agent.domain.knowledge.repository.IDocumentChunkRepository;
import com.ai.agent.domain.knowledge.repository.IDocumentRepository;
import com.ai.agent.domain.knowledge.repository.IKnowledgeBaseRepository;
import com.ai.agent.domain.knowledge.service.DocumentProcessor;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.enums.DocumentStatus;
import com.ai.agent.types.enums.OwnerType;
import com.ai.agent.types.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final IKnowledgeBaseRepository knowledgeBaseRepository;
    private final IDocumentRepository documentRepository;
    private final IDocumentChunkRepository chunkRepository;
    private final DocumentProcessor documentProcessor;
    private final Tika tika;

    public KnowledgeController(IKnowledgeBaseRepository knowledgeBaseRepository,
                               IDocumentRepository documentRepository,
                               IDocumentChunkRepository chunkRepository,
                               DocumentProcessor documentProcessor) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.documentProcessor = documentProcessor;
        this.tika = new Tika();
    }

    @PostMapping("/bases")
    public Response<KnowledgeBaseResponseDTO> createKnowledgeBase(
            @RequestParam String name,
            @RequestParam(defaultValue = "") String description,
            @RequestParam(defaultValue = "USER") String ownerType,
            @RequestParam(defaultValue = "") String ownerId) {
        KnowledgeBase kb = KnowledgeBase.builder()
                .baseId(UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .ownerType(OwnerType.valueOf(ownerType))
                .ownerId(ownerId)
                .createdAt(LocalDateTime.now())
                .build();
        knowledgeBaseRepository.save(kb);
        return Response.<KnowledgeBaseResponseDTO>builder()
                .code(Constants.ResponseCode.SUCCESS.getCode())
                .info(Constants.ResponseCode.SUCCESS.getInfo())
                .data(KnowledgeBaseResponseDTO.builder()
                        .baseId(kb.getBaseId())
                        .name(kb.getName())
                        .description(kb.getDescription())
                        .docCount(0)
                        .build())
                .build();
    }

    @PostMapping("/upload")
    public Response<String> uploadDocument(
            @RequestParam String knowledgeBaseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String userId) {
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

            // 持久化切片到数据库（修复chunks丢失问题）
            chunkRepository.saveBatch(chunks);

            documentRepository.updateStatus(docId, DocumentStatus.COMPLETED);
            return Response.<String>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(docId)
                    .build();
        } catch (Exception e) {
            log.error("文档上传处理失败, docId={}, fileName={}", docId, fileName, e);
            // 文档处理失败时更新状态为FAILED，不影响其他文档
            try {
                documentRepository.updateStatus(docId, DocumentStatus.FAILED);
            } catch (Exception ex) {
                log.error("更新文档状态为FAILED失败, docId={}", docId, ex);
            }
            return Response.<String>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info("文档处理失败: " + e.getMessage())
                    .data(null)
                    .build();
        }
    }
}
