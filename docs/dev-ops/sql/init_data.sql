-- auto-generated definition
create table agent_agentscope_config
(
    id                  bigserial
        primary key,
    agent_definition_id bigint      not null
        references agent_definition
            on delete cascade,
    config_agent_id     varchar(64) not null,
    mcp_servers         jsonb   default '[]'::jsonb,
    enable_tools        jsonb   default '[]'::jsonb,
    sort_order          integer default 0,
    instruction         text,
    output_key          varchar(255),
    name                varchar(128)
);

comment on table agent_agentscope_config is 'AgentScope代理配置表';

comment on column agent_agentscope_config.id is '主键';

comment on column agent_agentscope_config.agent_definition_id is '关联代理定义ID';

comment on column agent_agentscope_config.config_agent_id is '关联子Agent的agentId';

comment on column agent_agentscope_config.mcp_servers is '代理级MCP配置';

comment on column agent_agentscope_config.enable_tools is '启用的工具名称列表';

comment on column agent_agentscope_config.sort_order is '排序序号';

comment on column agent_agentscope_config.name is 'Agent 名称（前端节点标签）';

alter table agent_agentscope_config
    owner to postgres;

create index idx_agentscope_config_agent_def_id
    on agent_agentscope_config (agent_definition_id);



-- auto-generated definition
create table agent_definition
(
    id                       bigserial
        primary key,
    agent_id                 varchar(64)  not null
        unique,
    name                     varchar(128) not null,
    engine                   varchar(32)  not null
        constraint chk_engine_type
            check ((engine)::text = ANY
        ((ARRAY ['CHAT'::character varying, 'GRAPH'::character varying, 'AGENTSCOPE'::character varying, 'HYBRID'::character varying])::text[])),
    instruction              text,
    model_config             jsonb,
    mcp_servers              jsonb     default '[]'::jsonb,
    graph_start              varchar(64),
    agentscope_pipeline_type varchar(32),
    sub_engines              jsonb,
    created_at               timestamp default now(),
    updated_at               timestamp default now()
);

comment on table agent_definition is '代理定义主表';

comment on column agent_definition.id is '主键';

comment on column agent_definition.agent_id is '代理唯一标识';

comment on column agent_definition.name is '代理名称';

comment on column agent_definition.engine is '引擎类型枚举（CHAT/GRAPH/AGENTSCOPE/HYBRID）';

comment on column agent_definition.instruction is '系统指令';

comment on column agent_definition.model_config is '模型配置 { name, temperature, maxTokens }';

comment on column agent_definition.mcp_servers is 'MCP服务器配置列表';

comment on column agent_definition.graph_start is '起始节点ID（GRAPH/HYBRID引擎使用）';

comment on column agent_definition.agentscope_pipeline_type is '流水线类型（AGENTSCOPE引擎使用）';

comment on column agent_definition.sub_engines is '子引擎映射（HYBRID引擎使用）';

comment on column agent_definition.created_at is '创建时间';

comment on column agent_definition.updated_at is '更新时间';

alter table agent_definition
    owner to postgres;

-- auto-generated definition
create table agent_graph_edge
(
    id                  bigserial
        primary key,
    agent_definition_id bigint      not null
        references agent_definition
            on delete cascade,
    from_node           varchar(64) not null,
    to_node             varchar(64) not null,
    condition_expr      text,
    sort_order          integer default 0
);

comment on table agent_graph_edge is '代理图边表';

comment on column agent_graph_edge.id is '主键';

comment on column agent_graph_edge.agent_definition_id is '关联代理定义ID';

comment on column agent_graph_edge.from_node is '起始节点';

comment on column agent_graph_edge.to_node is '目标节点';

comment on column agent_graph_edge.condition_expr is '条件表达式';

comment on column agent_graph_edge.sort_order is '排序序号';

alter table agent_graph_edge
    owner to postgres;

create index idx_graph_edge_agent_def_id
    on agent_graph_edge (agent_definition_id);

create unique index uq_graph_edge_from_to
    on agent_graph_edge (agent_definition_id, from_node, to_node);

-- auto-generated definition
create table agent_workflow_node
(
    id                  bigserial
        primary key,
    agent_definition_id bigint      not null
        references agent_definition
            on delete cascade,
    node_id             varchar(64) not null,
    agent_id            varchar(64),
    rag_enabled         boolean default false,
    knowledge_base_id   varchar(64),
    sort_order          integer default 0,
    instruction         text,
    sub_engine          varchar(32),
    mcp_servers         jsonb
);

comment on table agent_workflow_node is '代理工作流节点表';

comment on column agent_workflow_node.id is '主键';

comment on column agent_workflow_node.agent_definition_id is '关联代理定义ID';

comment on column agent_workflow_node.node_id is '节点ID';

comment on column agent_workflow_node.agent_id is '关联代理ID';

comment on column agent_workflow_node.react_agent_id is 'ReAct代理ID';

comment on column agent_workflow_node.next_node_id is '下一节点ID';

comment on column agent_workflow_node.rag_enabled is '是否启用RAG';

comment on column agent_workflow_node.knowledge_base_id is '知识库ID';

comment on column agent_workflow_node.sort_order is '排序序号';

alter table agent_workflow_node
    owner to postgres;

create index idx_workflow_node_agent_def_id
    on agent_workflow_node (agent_definition_id);

create unique index uq_workflow_node_def_node
    on agent_workflow_node (agent_definition_id, node_id);

