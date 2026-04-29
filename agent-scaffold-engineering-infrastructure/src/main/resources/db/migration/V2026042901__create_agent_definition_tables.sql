-- ============================================================
-- V2026042901: 创建代理定义相关的4张表
-- ============================================================

-- 1. 代理定义主表
CREATE TABLE IF NOT EXISTS agent_definition (
    id                       BIGSERIAL PRIMARY KEY,
    agent_id                 VARCHAR(64) UNIQUE NOT NULL,
    name                     VARCHAR(128) NOT NULL,
    engine                   VARCHAR(32) NOT NULL,
    instruction              TEXT,
    model_config             JSONB,
    mcp_servers              JSONB DEFAULT '[]',
    graph_start              VARCHAR(64),
    agentscope_pipeline_type VARCHAR(32),
    sub_engines              JSONB,
    created_at               TIMESTAMP DEFAULT NOW(),
    updated_at               TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE  agent_definition IS '代理定义主表';
COMMENT ON COLUMN agent_definition.id IS '主键';
COMMENT ON COLUMN agent_definition.agent_id IS '代理唯一标识';
COMMENT ON COLUMN agent_definition.name IS '代理名称';
COMMENT ON COLUMN agent_definition.engine IS '引擎类型枚举（CHAT/GRAPH/AGENTSCOPE/HYBRID）';
COMMENT ON COLUMN agent_definition.instruction IS '系统指令';
COMMENT ON COLUMN agent_definition.model_config IS '模型配置 { name, temperature, maxTokens }';
COMMENT ON COLUMN agent_definition.mcp_servers IS 'MCP服务器配置列表';
COMMENT ON COLUMN agent_definition.graph_start IS '起始节点ID（GRAPH/HYBRID引擎使用）';
COMMENT ON COLUMN agent_definition.agentscope_pipeline_type IS '流水线类型（AGENTSCOPE引擎使用）';
COMMENT ON COLUMN agent_definition.sub_engines IS '子引擎映射（HYBRID引擎使用）';
COMMENT ON COLUMN agent_definition.created_at IS '创建时间';
COMMENT ON COLUMN agent_definition.updated_at IS '更新时间';

ALTER TABLE agent_definition ADD CONSTRAINT chk_engine_type CHECK (engine IN ('CHAT', 'GRAPH', 'AGENTSCOPE', 'HYBRID'));

-- 2. 工作流节点表
CREATE TABLE IF NOT EXISTS agent_workflow_node (
    id                   BIGSERIAL PRIMARY KEY,
    agent_definition_id  BIGINT NOT NULL REFERENCES agent_definition(id) ON DELETE CASCADE,
    node_id              VARCHAR(64) NOT NULL,
    agent_id             VARCHAR(64),
    react_agent_id       VARCHAR(64),
    next_node_id         VARCHAR(64),
    rag_enabled          BOOLEAN DEFAULT FALSE,
    knowledge_base_id    VARCHAR(64),
    sort_order           INT DEFAULT 0
);

COMMENT ON TABLE  agent_workflow_node IS '代理工作流节点表';
COMMENT ON COLUMN agent_workflow_node.id IS '主键';
COMMENT ON COLUMN agent_workflow_node.agent_definition_id IS '关联代理定义ID';
COMMENT ON COLUMN agent_workflow_node.node_id IS '节点ID';
COMMENT ON COLUMN agent_workflow_node.agent_id IS '关联代理ID';
COMMENT ON COLUMN agent_workflow_node.react_agent_id IS 'ReAct代理ID';
COMMENT ON COLUMN agent_workflow_node.next_node_id IS '下一节点ID';
COMMENT ON COLUMN agent_workflow_node.rag_enabled IS '是否启用RAG';
COMMENT ON COLUMN agent_workflow_node.knowledge_base_id IS '知识库ID';
COMMENT ON COLUMN agent_workflow_node.sort_order IS '排序序号';

CREATE INDEX idx_workflow_node_agent_def_id ON agent_workflow_node(agent_definition_id);
-- 节点ID在单个Agent定义内唯一
CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_node_def_node ON agent_workflow_node(agent_definition_id, node_id);

-- 3. 图边表
CREATE TABLE IF NOT EXISTS agent_graph_edge (
    id                   BIGSERIAL PRIMARY KEY,
    agent_definition_id  BIGINT NOT NULL REFERENCES agent_definition(id) ON DELETE CASCADE,
    from_node            VARCHAR(64) NOT NULL,
    to_node              VARCHAR(64) NOT NULL,
    condition_expr       TEXT,
    sort_order           INT DEFAULT 0
);

COMMENT ON TABLE  agent_graph_edge IS '代理图边表';
COMMENT ON COLUMN agent_graph_edge.id IS '主键';
COMMENT ON COLUMN agent_graph_edge.agent_definition_id IS '关联代理定义ID';
COMMENT ON COLUMN agent_graph_edge.from_node IS '起始节点';
COMMENT ON COLUMN agent_graph_edge.to_node IS '目标节点';
COMMENT ON COLUMN agent_graph_edge.condition_expr IS '条件表达式';
COMMENT ON COLUMN agent_graph_edge.sort_order IS '排序序号';

CREATE INDEX idx_graph_edge_agent_def_id ON agent_graph_edge(agent_definition_id);
-- 边在单个Agent定义内唯一
CREATE UNIQUE INDEX IF NOT EXISTS uq_graph_edge_from_to ON agent_graph_edge(agent_definition_id, from_node, to_node);

-- 4. AgentScope配置表
CREATE TABLE IF NOT EXISTS agent_agentscope_config (
    id                   BIGSERIAL PRIMARY KEY,
    agent_definition_id  BIGINT NOT NULL REFERENCES agent_definition(id) ON DELETE CASCADE,
    config_agent_id      VARCHAR(64) NOT NULL,
    mcp_servers          JSONB DEFAULT '[]',
    enable_tools         JSONB DEFAULT '[]',
    sort_order           INT DEFAULT 0
);

COMMENT ON TABLE  agent_agentscope_config IS 'AgentScope代理配置表';
COMMENT ON COLUMN agent_agentscope_config.id IS '主键';
COMMENT ON COLUMN agent_agentscope_config.agent_definition_id IS '关联代理定义ID';
COMMENT ON COLUMN agent_agentscope_config.config_agent_id IS '关联子Agent的agentId';
COMMENT ON COLUMN agent_agentscope_config.mcp_servers IS '代理级MCP配置';
COMMENT ON COLUMN agent_agentscope_config.enable_tools IS '启用的工具名称列表';
COMMENT ON COLUMN agent_agentscope_config.sort_order IS '排序序号';

CREATE INDEX idx_agentscope_config_agent_def_id ON agent_agentscope_config(agent_definition_id);
