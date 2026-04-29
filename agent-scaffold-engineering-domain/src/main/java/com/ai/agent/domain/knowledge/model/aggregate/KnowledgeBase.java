package com.ai.agent.domain.knowledge.model.aggregate;

import com.ai.agent.types.enums.OwnerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库聚合根
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBase {

    /** 知识库ID */
    private String baseId;

    /** 知识库名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 拥有者类型 */
    private OwnerType ownerType;

    /** 拥有者ID */
    private String ownerId;

    /** 文档数量 */
    private Integer docCount = 0;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
