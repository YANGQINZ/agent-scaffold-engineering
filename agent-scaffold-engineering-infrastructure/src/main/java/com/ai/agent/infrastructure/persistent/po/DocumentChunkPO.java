package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;

/**
 * 文档分块持久化对象
 */
@Data
public class DocumentChunkPO {

    /** 分块ID（UUID） */
    private String id;

    /** 分块文本内容 */
    private String content;

    /** 向量嵌入 */
    private String embedding;

    /** 元数据（JSON，包含baseId/docId/chunkIndex等） */
    private String metadata;

    /** 搜索得分 */
    private Double score;

}
