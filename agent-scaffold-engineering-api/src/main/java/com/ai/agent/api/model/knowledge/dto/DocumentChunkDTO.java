package com.ai.agent.api.model.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档分块DTO — 用于返回知识库下的文档列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunkDTO {

    /** 分块ID */
    private String id;

    /** 分块内容 */
    private String content;

    /** 元数据 */
    private String metadata;

}
