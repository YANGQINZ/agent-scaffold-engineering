package com.ai.agent.domain.knowledge.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档分块实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    /** 分块ID */
    private Long chunkId;

    /** 文档ID */
    private String docId;

    /** 知识库ID */
    private String baseId;

    /** 分块文本内容 */
    private String content;

    /** 向量嵌入 */
    private float[] embedding;

    /** 元数据 */
    private String metadata;

    /** 分块索引 */
    private Integer chunkIndex;

    /** 检索相关性得分（搜索结果返回时填充） */
    private Double score;

}
