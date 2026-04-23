package com.ai.agent.trigger.http;

import com.ai.agent.api.model.knowledge.KnowledgeBaseResponseDTO;
import com.ai.agent.domain.knowledge.model.aggregate.KnowledgeBase;
import com.ai.agent.domain.knowledge.model.entity.Document;
import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import com.ai.agent.domain.knowledge.repository.IDocumentRepository;
import com.ai.agent.domain.knowledge.repository.IKnowledgeBaseRepository;
import com.ai.agent.domain.knowledge.service.DocumentProcessor;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.enums.DocumentStatus;
import com.ai.agent.types.enums.OwnerType;
import com.ai.agent.types.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final IKnowledgeBaseRepository knowledgeBaseRepository;
    private final IDocumentRepository documentRepository;
    private final DocumentProcessor documentProcessor;

    public KnowledgeController(IKnowledgeBaseRepository knowledgeBaseRepository,
                               IDocumentRepository documentRepository,
                               DocumentProcessor documentProcessor) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.documentProcessor = documentProcessor;
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
        try {
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

            String content = new BufferedReader(new InputStreamReader(file.getInputStream()))
                    .lines().collect(Collectors.joining("\n"));

            List<DocumentChunk> chunks = documentProcessor.process(docId, knowledgeBaseId, content);

            documentRepository.updateStatus(docId, DocumentStatus.COMPLETED);
            return Response.<String>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(docId)
                    .build();
        } catch (Exception e) {
            log.error("文档上传处理失败", e);
            return Response.<String>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info("文档处理失败: " + e.getMessage())
                    .data(null)
                    .build();
        }
    }
}
