package com.ai.agent.domain.chat.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG检索引用来源值对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceRef {

    /** 文档名称 */
    private String docName;

    /** 片段内容 */
    private String chunkContent;

    /** 相关性得分 */
    private Double score;

}
