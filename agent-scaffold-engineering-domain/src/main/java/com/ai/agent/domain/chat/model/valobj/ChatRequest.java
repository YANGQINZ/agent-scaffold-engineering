package com.ai.agent.domain.chat.model.valobj;

import com.ai.agent.types.enums.EngineType;
import com.ai.agent.types.enums.ChatMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /** 用户ID */
    private String userId;

    /** 会话ID（多轮对话时传入，首次为null） */
    private String sessionId;

    /** 用户查询内容 */
    private String query;

    /** 对话模式 */
    @Builder.Default
    private ChatMode mode = ChatMode.SIMPLE;

    /** 引擎类型（当mode=AGENT时生效） */
    @Builder.Default
    private EngineType engine = EngineType.GRAPH;

    /** 是否启用RAG检索增强 */
    @Builder.Default
    private Boolean ragEnabled = false;

    /** 是否启用思考过程输出 */
    @Builder.Default
    private Boolean enableThinking = false;

    /** 知识库ID（RAG启用时指定） */
    private String knowledgeBaseId;

    /** 指定要使用的Agent ID（YAML配置中的agent定义标识） */
    private String agentId;

}
