package com.ai.agent.trigger.http;

import com.ai.agent.api.IKnowledgeBaseService;
import com.ai.agent.api.model.knowledge.dto.DocumentChunkDTO;
import com.ai.agent.api.model.knowledge.dto.KnowledgeBaseResponseDTO;
import com.ai.agent.domain.knowledge.model.aggregate.KnowledgeBase;
import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import com.ai.agent.domain.knowledge.service.IKnowledgeService;
import com.ai.agent.types.model.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库 HTTP 控制器 — 通过 IKnowledgeService 接口与业务层交互
 * 仅负责参数校验、HTTP 协议处理和响应封装。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController implements IKnowledgeBaseService {

    private final IKnowledgeService knowledgeService;

    /**
     * 上传知识库文件
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<Void> uploadKnowledgeBase(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "name", required = false) String name) {
        knowledgeService.uploadKnowledgeBase(file, name);
        return Response.buildSuccess();
    }

    /**
     * 查询所有知识库列表
     */
    @GetMapping("/bases")
    @Override
    public Response<List<KnowledgeBaseResponseDTO>> listKnowledgeBases() {
        List<KnowledgeBase> bases = knowledgeService.listKnowledgeBases();
        List<KnowledgeBaseResponseDTO> dtoList = bases.stream()
                .map(this::toKnowledgeBaseDTO)
                .toList();
        return Response.buildSuccess(dtoList);
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/bases/{id}")
    @Override
    public Response<Void> deleteKnowledgeBase(@PathVariable("id") String baseId) {
        log.info("删除知识库请求: baseId={}", baseId);
        knowledgeService.deleteKnowledgeBase(baseId);
        return Response.buildSuccess();
    }

    /**
     * 查询知识库下的文档分块列表
     */
    @GetMapping("/bases/{id}/documents")
    @Override
    public Response<List<DocumentChunkDTO>> listDocuments(@PathVariable("id") String baseId) {
        List<DocumentChunk> chunks = knowledgeService.listDocuments(baseId);
        List<DocumentChunkDTO> dtoList = chunks.stream()
                .map(this::toDocumentChunkDTO)
                .toList();
        return Response.buildSuccess(dtoList);
    }

    /**
     * KnowledgeBase -> KnowledgeBaseResponseDTO
     */
    private KnowledgeBaseResponseDTO toKnowledgeBaseDTO(KnowledgeBase kb) {
        return KnowledgeBaseResponseDTO.builder()
                .baseId(String.valueOf(kb.getBaseId()))
                .name(kb.getName())
                .description(kb.getDescription())
                .docCount(kb.getDocCount())
                .build();
    }

    /**
     * DocumentChunk -> DocumentChunkDTO
     */
    private DocumentChunkDTO toDocumentChunkDTO(DocumentChunk chunk) {
        return DocumentChunkDTO.builder()
                .id(chunk.getId())
                .content(chunk.getContent())
                .metadata(chunk.getMetadata())
                .build();
    }
}
