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

    /** 分块ID（UUID） */
    private String id;

    /** 分块文本内容 */
    private String content;

    /** 向量嵌入 */
    private float[] embedding;

    /** 元数据（JSON字符串，包含baseId/docId/chunkIndex等） */
    private String metadata;

    /** 检索相关性得分（搜索结果返回时填充） */
    private Double score;

}
