package com.ai.agent.domain.common.valobj;

import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
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
    private ChatMode mode = ChatMode.SIMPLE;

    /** 引擎类型（当mode=AGENT时生效） */
    private EngineType engine = EngineType.GRAPH;

    /** 是否启用RAG检索增强 */
    private Boolean ragEnabled = false;

    /** 是否启用思考过程输出 */
    private Boolean enableThinking = false;

    /** 知识库ID（RAG启用时指定） */
    private String knowledgeBaseId;

    /** 指定要使用的Agent ID（YAML配置中的agent定义标识） */
    private String agentId;

    /** 内联 Agent 定义（未保存画布时传递的临时画布数据） */
    private AgentDefinition agentDefinition;

    /** 测试运行标识 — 为 true 时跳过上下文注入和历史消息追加 */
    private Boolean testRun = false;

}
