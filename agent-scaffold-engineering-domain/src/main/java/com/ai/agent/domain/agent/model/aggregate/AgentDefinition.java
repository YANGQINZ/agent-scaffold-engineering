package com.ai.agent.domain.agent.model.aggregate;

import com.ai.agent.domain.agent.model.entity.McpServerConfig;
import com.ai.agent.domain.agent.model.valobj.ModelConfig;
import com.ai.agent.types.enums.EngineType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * Agent定义聚合根（抽象基类）
 *
 * 公共字段由基类持有，引擎特有字段由子类持有。
 * 通过 engine 字段区分引擎类型，子类构造时固定 engine 值。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class AgentDefinition {

    /** Agent唯一标识 */
    private String agentId;

    /** Agent名称 */
    private String name;

    /** 引擎类型（子类构造时固定） */
    private EngineType engine;

    /** 系统指令 */
    private String instruction;

    /** 模型配置 */
    private ModelConfig modelConfig = new ModelConfig();

    /** MCP Server 配置列表（跨引擎通用） */
    private List<McpServerConfig> mcpServers = List.of();

}
