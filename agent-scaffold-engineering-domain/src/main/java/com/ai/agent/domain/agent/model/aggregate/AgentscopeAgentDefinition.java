package com.ai.agent.domain.agent.model.aggregate;

import com.ai.agent.domain.agent.model.entity.AgentscopeAgentConfig;
import com.ai.agent.types.enums.EngineType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * AGENTSCOPE 引擎 Agent 定义 — agentscope-java Pipeline 编排
 *
 * 持有 AgentScope 特有字段：Pipeline 类型、子 Agent 配置列表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class AgentscopeAgentDefinition extends AgentDefinition {

    /** AgentScope Pipeline 类型：sequential */
    private String agentscopePipelineType = "sequential";

    /** AgentScope Agent 配置列表 */
    private List<AgentscopeAgentConfig> agentscopeAgents = List.of();

    @Override
    public EngineType getEngine() {
        return EngineType.AGENTSCOPE;
    }

    @Override
    public void setEngine(EngineType engine) {
        if (engine != EngineType.AGENTSCOPE) {
            throw new IllegalArgumentException("AgentscopeAgentDefinition 的 engine 必须为 AGENTSCOPE");
        }
        super.setEngine(engine);
    }

}
