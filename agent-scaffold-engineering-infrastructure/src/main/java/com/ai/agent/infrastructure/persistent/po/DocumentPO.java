package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档持久化对象
 */
@Data
public class DocumentPO {

    /** 文档ID */
    private String docId;

    /** 知识库ID */
    private String baseId;

    /** 文件名 */
    private String fileName;

    /** 文件类型 */
    private String fileType;

    /** 处理状态 */
    private String status;

    /** 分块数量 */
    private Integer chunkCount;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
