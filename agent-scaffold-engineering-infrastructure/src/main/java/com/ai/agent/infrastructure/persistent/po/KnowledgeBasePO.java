package com.ai.agent.infrastructure.persistent.po;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库持久化对象
 */
@Data
public class KnowledgeBasePO {

    /** 知识库ID */
    private String baseId;

    /** 知识库名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 拥有者类型 */
    private String ownerType;

    /** 拥有者ID */
    private String ownerId;

    /** 文档数量 */
    private Integer docCount;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
