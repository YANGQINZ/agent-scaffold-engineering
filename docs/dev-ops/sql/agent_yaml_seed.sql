-- ═══════════════════════════════════════════════════════════════
-- Agent YAML 种子数据
-- 由 agents/ 目录下 4 个 YAML 文件生成
-- 对应表: agent_definition, agent_workflow_node, agent_graph_edge, agent_agentscope_config
-- ═══════════════════════════════════════════════════════════════

-- 清空旧数据（按外键依赖逆序）
DELETE FROM agent_agentscope_config;
DELETE FROM agent_graph_edge;
DELETE FROM agent_workflow_node;
DELETE FROM agent_definition;

-- ═══════════════════════════════════════════════════════════════
-- 1. agent_definition 主表 (4行)
-- ═══════════════════════════════════════════════════════════════

INSERT INTO agent_definition (agent_id, name, engine, instruction, model_config, mcp_servers, graph_start, agentscope_pipeline_type, sub_engines)
VALUES
-- chat/simple-assistant.yaml
('simple-assistant', '简单助手', 'CHAT', '你是一个有用的助手，帮助用户解答问题。',
 '{"name":"qwen-plus","temperature":0.7,"maxTokens":2000}'::jsonb, '[]'::jsonb,
 NULL, NULL, NULL),

-- graph/tech-support.yaml
('tech-support', '技术支持系统', 'GRAPH', '你是技术支持系统，根据用户问题类型路由到对应的处理节点。',
 '{"name":"qwq-plus","temperature":0.3,"maxTokens":2000}'::jsonb, '[]'::jsonb,
 '["triage"]', NULL, NULL),

-- agentscope/travel-planner-team.yaml
('travel-planner-team', '旅行规划团队', 'AGENTSCOPE', '你是一个旅行规划团队，通过多角色协作完成旅行规划。依次收集需求、搜索路线、查询天气、生成行程。',
 '{"name":"qwq-plus","temperature":0.5,"maxTokens":2000}'::jsonb, '[]'::jsonb,
 NULL, 'sequential', NULL),

-- hybrid/intelligence-researcher.yaml
('intelligence-researcher', '智能调研助手', 'HYBRID', '你是一个企业级智能调研助手，能够分析需求、多维度调研并生成深度报告。',
 '{"name":"qwen-plus","temperature":0.7,"maxTokens":2000}'::jsonb, '[]'::jsonb,
 '["requirement_analyst"]', NULL,
 '{"requirement_analyst":"GRAPH","market_scout":"GRAPH","tech_scout":"GRAPH","deep_investigator":"AGENTSCOPE"}'::jsonb);


-- ═══════════════════════════════════════════════════════════════
-- 2. agent_workflow_node 节点表
--    CHAT / AGENTSCOPE 引擎无节点，仅 GRAPH 和 HYBRID 有
-- ═══════════════════════════════════════════════════════════════

-- ── tech-support (GRAPH) ── 5个节点
INSERT INTO agent_workflow_node (agent_definition_id, node_id, agent_id, rag_enabled, knowledge_base_id, instruction, sub_engine, mcp_servers, sort_order)
SELECT id, 'triage', 'tech-support', false, NULL,
       '你是技术支持分类器。分析用户的问题并判断类型，只输出分类标签：
- 如果是缺陷/Bug报告，输出 "type==bug"
- 如果是功能需求/建议，输出 "type==feature"
- 如果是使用问题/咨询，输出 "type==question"
只输出一个标签，不要解释。',
       NULL, '[]'::jsonb, 0
FROM agent_definition WHERE agent_id = 'tech-support';

INSERT INTO agent_workflow_node (agent_definition_id, node_id, agent_id, rag_enabled, knowledge_base_id, instruction, sub_engine, mcp_servers, sort_order)
SELECT id, 'bug_handler', 'tech-support', false, NULL,
       '你是一位资深Bug分析师。根据用户报告的Bug信息：
1. 分析可能的根本原因
2. 评估严重等级 (P0-P3)
3. 给出排查步骤和临时解决方案
4. 输出结构化的Bug分析报告',
       NULL, '[]'::jsonb, 1
FROM agent_definition WHERE agent_id = 'tech-support';

INSERT INTO agent_workflow_node (agent_definition_id, node_id, agent_id, rag_enabled, knowledge_base_id, instruction, sub_engine, mcp_servers, sort_order)
SELECT id, 'feature_handler', 'tech-support', false, NULL,
       '你是一位产品需求分析师。根据用户提出的功能需求：
1. 分析需求的价值和优先级
2. 评估技术可行性
3. 建议实现方案和里程碑
4. 输出结构化的需求分析报告',
       NULL, '[]'::jsonb, 2
FROM agent_definition WHERE agent_id = 'tech-support';

INSERT INTO agent_workflow_node (agent_definition_id, node_id, agent_id, rag_enabled, knowledge_base_id, instruction, sub_engine, mcp_servers, sort_order)
SELECT id, 'faq_handler', 'tech-support', true, '2050102098263064578',
       '你是一位技术咨询顾问。根据用户的问题，结合知识库中的技术文档，
给出准确、详细的解答。如果知识库中没有相关信息，基于专业知识回答并标注"非官方建议"。',
       NULL, '[]'::jsonb, 3
FROM agent_definition WHERE agent_id = 'tech-support';

INSERT INTO agent_workflow_node (agent_definition_id, node_id, agent_id, rag_enabled, knowledge_base_id, instruction, sub_engine, mcp_servers, sort_order)
SELECT id, 'resolver', 'tech-support', false, NULL,
       '你是一位技术支持主管。根据前面的分析结果，
生成一份用户友好的回复，要求：
1. 用通俗易懂的语言总结技术分析
2. 给出明确的下一步行动建议
3. 如有临时解决方案，优先提供
4. 标注预计解决时间（如适用）',
       NULL, '[]'::jsonb, 4
FROM agent_definition WHERE agent_id = 'tech-support';

-- ── intelligence-researcher (HYBRID) ── 4个节点
INSERT INTO agent_workflow_node (agent_definition_id, node_id, agent_id, rag_enabled, knowledge_base_id, instruction, sub_engine, mcp_servers, sort_order)
SELECT id, 'requirement_analyst', 'intelligence-researcher', false, NULL,
       '你是一位企业需求分析专家。根据用户的调研需求，分析并输出结构化的调研框架：
1. 明确调研目标
2. 拆分为市场维度和技术维度的调研方向
3. 列出每个维度需要回答的关键问题
请以结构化格式输出，便于后续分维度调研。',
       'GRAPH', '[]'::jsonb, 0
FROM agent_definition WHERE agent_id = 'intelligence-researcher';

INSERT INTO agent_workflow_node (agent_definition_id, node_id, agent_id, rag_enabled, knowledge_base_id, instruction, sub_engine, mcp_servers, sort_order)
SELECT id, 'market_scout', 'intelligence-researcher', false, NULL,
       '你是一位资深市场分析师。根据上一步输出的市场维度调研方向，撰写市场分析报告，包括：
- 市场规模与增长趋势
- 竞争格局与主要玩家
- 用户需求与痛点分析
- 市场机会与风险提示',
       'GRAPH', '[]'::jsonb, 1
FROM agent_definition WHERE agent_id = 'intelligence-researcher';

INSERT INTO agent_workflow_node (agent_definition_id, node_id, agent_id, rag_enabled, knowledge_base_id, instruction, sub_engine, mcp_servers, sort_order)
SELECT id, 'tech_scout', 'intelligence-researcher', false, NULL,
       '你是一位技术架构师。根据上一步输出的技术维度调研方向，撰写技术分析报告，包括：
- 核心技术栈与演进趋势
- 开源方案对比与选型建议
- 技术风险与应对策略
- 创新技术机会点',
       'GRAPH', '[]'::jsonb, 2
FROM agent_definition WHERE agent_id = 'intelligence-researcher';

INSERT INTO agent_workflow_node (agent_definition_id, node_id, agent_id, rag_enabled, knowledge_base_id, instruction, sub_engine, mcp_servers, sort_order)
SELECT id, 'deep_investigator', 'intelligence-researcher', false, NULL,
       '你是一位深度调研员，拥有网络搜索能力。
你将收到市场分析和技术分析两份报告作为输入。
请综合两份报告，使用搜索工具补充最新的行业数据和新闻，
然后输出一份完整的深度调研报告，要求：
1. 数据有出处，标注信息来源
2. 观点有依据，引用具体数据
3. 建议可落地，给出明确的行动项',
       'AGENTSCOPE',
       '[]'::jsonb,
       3
FROM agent_definition WHERE agent_id = 'intelligence-researcher';


-- ═══════════════════════════════════════════════════════════════
-- 3. agent_graph_edge 边表
--    仅 GRAPH 和 HYBRID 引擎有边
-- ═══════════════════════════════════════════════════════════════

-- ── tech-support (GRAPH) ── 6条边
INSERT INTO agent_graph_edge (agent_definition_id, from_node, to_node, condition_expr, sort_order)
SELECT id, 'triage', 'bug_handler', 'type==bug', 0 FROM agent_definition WHERE agent_id = 'tech-support';
INSERT INTO agent_graph_edge (agent_definition_id, from_node, to_node, condition_expr, sort_order)
SELECT id, 'triage', 'feature_handler', 'type==feature', 1 FROM agent_definition WHERE agent_id = 'tech-support';
INSERT INTO agent_graph_edge (agent_definition_id, from_node, to_node, condition_expr, sort_order)
SELECT id, 'triage', 'faq_handler', 'type==question', 2 FROM agent_definition WHERE agent_id = 'tech-support';
INSERT INTO agent_graph_edge (agent_definition_id, from_node, to_node, condition_expr, sort_order)
SELECT id, 'bug_handler', 'resolver', NULL, 3 FROM agent_definition WHERE agent_id = 'tech-support';
INSERT INTO agent_graph_edge (agent_definition_id, from_node, to_node, condition_expr, sort_order)
SELECT id, 'feature_handler', 'resolver', NULL, 4 FROM agent_definition WHERE agent_id = 'tech-support';
INSERT INTO agent_graph_edge (agent_definition_id, from_node, to_node, condition_expr, sort_order)
SELECT id, 'faq_handler', 'resolver', NULL, 5 FROM agent_definition WHERE agent_id = 'tech-support';

-- ── intelligence-researcher (HYBRID) ── 4条边
INSERT INTO agent_graph_edge (agent_definition_id, from_node, to_node, condition_expr, sort_order)
SELECT id, 'requirement_analyst', 'market_scout', NULL, 0 FROM agent_definition WHERE agent_id = 'intelligence-researcher';
INSERT INTO agent_graph_edge (agent_definition_id, from_node, to_node, condition_expr, sort_order)
SELECT id, 'requirement_analyst', 'tech_scout', NULL, 1 FROM agent_definition WHERE agent_id = 'intelligence-researcher';
INSERT INTO agent_graph_edge (agent_definition_id, from_node, to_node, condition_expr, sort_order)
SELECT id, 'market_scout', 'deep_investigator', NULL, 2 FROM agent_definition WHERE agent_id = 'intelligence-researcher';
INSERT INTO agent_graph_edge (agent_definition_id, from_node, to_node, condition_expr, sort_order)
SELECT id, 'tech_scout', 'deep_investigator', NULL, 3 FROM agent_definition WHERE agent_id = 'intelligence-researcher';


-- ═══════════════════════════════════════════════════════════════
-- 4. agent_agentscope_config AgentScope子Agent配置表
--    仅 AGENTSCOPE 引擎有配置
-- ═══════════════════════════════════════════════════════════════

-- ── travel-planner-team (AGENTSCOPE) ── 4个子Agent
INSERT INTO agent_agentscope_config (agent_definition_id, config_agent_id, name, instruction, mcp_servers, enable_tools, output_key, sort_order)
SELECT id, 'info-collector', '信息收集员', NULL, '[]'::jsonb, '[]'::jsonb, 'collected_info', 0
FROM agent_definition WHERE agent_id = 'travel-planner-team';

INSERT INTO agent_agentscope_config (agent_definition_id, config_agent_id, name, instruction, mcp_servers, enable_tools, output_key, sort_order)
SELECT id, 'route-searcher', '路线搜索员',
       '你是一位专业的旅行路线搜索员。
基于收集到的旅行需求信息 {collected_info}，
搜索从出发地到目的地的所有可选交通方式，
比较价格、时长和便捷度，推荐最优路线方案。',
       '[]'::jsonb, '[]'::jsonb, 'route_options', 1
FROM agent_definition WHERE agent_id = 'travel-planner-team';

INSERT INTO agent_agentscope_config (agent_definition_id, config_agent_id, name, instruction, mcp_servers, enable_tools, output_key, sort_order)
SELECT id, 'weather-assistant', '天气查询员',
       '你是一位旅行天气顾问。
基于目的地信息 {collected_info} 和选定路线 {route_options}，
查询目的地未来几天的天气情况，
给出穿衣建议和出行提醒。',
       '[]'::jsonb, '[]'::jsonb, 'weather_info', 2
FROM agent_definition WHERE agent_id = 'travel-planner-team';

INSERT INTO agent_agentscope_config (agent_definition_id, config_agent_id, name, instruction, mcp_servers, enable_tools, output_key, sort_order)
SELECT id, 'plan-generator', '行程生成员',
       '你是一位资深旅行规划师。
请综合以下信息，生成一份完整的旅行行程计划：
- 用户需求: {collected_info}
- 推荐路线: {route_options}
- 天气情况: {weather_info}
行程计划应包含：每日安排、交通详情、住宿建议、预算估算和注意事项。',
       '[]'::jsonb, '[]'::jsonb, 'travel_plan', 3
FROM agent_definition WHERE agent_id = 'travel-planner-team';
