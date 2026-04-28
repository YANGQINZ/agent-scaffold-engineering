package com.ai.agent.domain.knowledge;

import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import com.ai.agent.domain.knowledge.service.EmbeddingService;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.exception.KnowledgeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档处理服务 — 分块 + 嵌入
 */
@Slf4j
@Service
public class DocumentProcessor {

    private final EmbeddingService embeddingService;

    public DocumentProcessor(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    /**
     * 处理文档内容：分块并生成向量嵌入
     *
     * @param docId   文档ID
     * @param baseId  知识库ID
     * @param content 文档文本内容
     * @return 文档分块列表
     */
    public List<DocumentChunk> process(String docId, String baseId, String content) {
        try {
            // 使用Spring AI TokenTextSplitter进行分块
            Document aiDoc = new Document(content);
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(800)
                    .withMinChunkSizeChars(350)
                    .withMinChunkLengthToEmbed(5)
                    .withMaxNumChunks(10000)
                    .withKeepSeparator(true)
                    .build();
            List<Document> chunks = splitter.apply(List.of(aiDoc));

            // 提取文本并批量嵌入
            List<String> texts = chunks.stream().map(Document::getText).toList();
            List<float[]> embeddings = embeddingService.embedBatch(texts);

            // 构建DocumentChunk列表
            List<DocumentChunk> result = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Document aiChunk = chunks.get(i);
                DocumentChunk chunk = DocumentChunk.builder()
                        .docId(docId)
                        .baseId(baseId)
                        .content(aiChunk.getText())
                        .embedding(embeddings.get(i))
                        .chunkIndex(i)
                        .build();
                result.add(chunk);
            }

            log.info("文档处理完成 docId={}, baseId={}, 分块数={}", docId, baseId, result.size());
            return result;
        } catch (Exception e) {
            log.error("文档处理失败 docId={}, baseId={}", docId, baseId, e);
            throw new KnowledgeException(Constants.ErrorCode.KNOW_PROCESSING_FAILED, "文档处理失败: " + e.getMessage(), e);
        }
    }

}
