package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;

/**
 * 文档分块持久化对象
 */
@Data
public class DocumentChunkPO {

    /** 分块ID */
    private Long chunkId;

    /** 文档ID */
    private String docId;

    /** 知识库ID */
    private String baseId;

    /** 分块文本内容 */
    private String content;

    /** 向量嵌入 */
    private String embedding;

    /** 元数据 */
    private String metadata;

    /** 分块索引 */
    private Integer chunkIndex;

    /** 搜索得分 */
    private Double score;

}
