package com.ai.agent.domain.knowledge.model.valobj;

import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG检索结果值对象 — 封装retrieveAndBuild的完整输出
 * 包含增强提示词和原始检索切片，供RagDecorator构建引用来源
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagResult {

    /** 增强后的提示词 */
    private String enhancedPrompt;

    /** 检索到的文档切片（用于构建引用来源） */
    private List<DocumentChunk> chunks;

    /** RAG是否降级执行 */
    private boolean degraded;

}
