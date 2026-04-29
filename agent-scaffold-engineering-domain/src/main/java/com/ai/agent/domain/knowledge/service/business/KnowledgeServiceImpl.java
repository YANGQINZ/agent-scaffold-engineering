package com.ai.agent.domain.knowledge.service.business;

import com.ai.agent.domain.knowledge.model.aggregate.KnowledgeBase;
import com.ai.agent.domain.knowledge.repository.IKnowledgeBaseRepository;
import com.ai.agent.domain.knowledge.service.IKnowledgeService;
import com.ai.agent.types.exception.KnowledgeException;
import com.ai.agent.types.exception.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

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
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    @Override
    public void uploadKnowledgeBase(MultipartFile file, String name) {
        // 1. 验证文件
        validateFile(file, MAX_FILE_SIZE, "知识库");

        TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
        List<org.springframework.ai.document.Document> documentList = tokenTextSplitter.apply(documentReader.get());
        String fileName = file.getOriginalFilename();
        String fileType = fileName != null ? fileName.substring(fileName.lastIndexOf(".") + 1) : "txt";

        KnowledgeBase kb = KnowledgeBase.builder()
            .name(name != null && !name.trim().isEmpty() ? name : extractNameFromFilename(file.getOriginalFilename()))
            .fileName(fileName)
            .fileType(fileType)
            .createdAt(LocalDateTime.now())
            .docCount(documentList.size())
            .build();
        long baseId = knowledgeBaseRepository.save(kb);

        // 添加知识库标签
        documentList.forEach(chunk -> chunk.getMetadata().put("kb_id", baseId));
        // 存储知识库文件
        vectorStore.accept(documentList);
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

    /**
     * 从文件名提取知识库名称（去除扩展名）
     */
    private String extractNameFromFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "未命名知识库";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }
}
