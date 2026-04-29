package com.ai.agent.domain.common.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** 回答内容 */
    private String answer;

    /** 思考过程内容（enableThinking=true时有值） */
    private String thinkingContent;

    /** RAG引用来源 */
    private List<Source> sources;

    /** 会话ID */
    private String sessionId;

    /** RAG是否降级（检索失败时降级为普通对话） */
    private Boolean ragDegraded = false;

    /** 元数据 */
    private Map<String, Object> metadata;

    /**
     * RAG引用来源
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Source {

        /** 文档名称 */
        private String docName;

        /** 切片内容 */
        private String chunkContent;

        /** 相似度得分 */
        private Double score;

    }

}
