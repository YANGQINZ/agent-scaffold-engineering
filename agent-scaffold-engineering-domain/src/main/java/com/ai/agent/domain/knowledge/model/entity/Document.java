package com.ai.agent.domain.knowledge.model.entity;

import com.ai.agent.types.enums.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    /** 文档ID */
    private String docId;

    /** 知识库ID */
    private String baseId;

    /** 文件名 */
    private String fileName;

    /** 文件类型 */
    private String fileType;

    /** 处理状态 */
    private DocumentStatus status;

    /** 分块数量 */
    private Integer chunkCount = 0;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
