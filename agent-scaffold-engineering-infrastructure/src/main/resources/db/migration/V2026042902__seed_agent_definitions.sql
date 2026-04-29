-- ============================================================
-- V2026042902: 从现有YAML定义中导入12个代理的种子数据
-- ============================================================

-- -----------------------------------------------------------
-- 一、插入 agent_definition 主表（12条）
-- -----------------------------------------------------------

-- CHAT 引擎代理（9个）

INSERT INTO agent_definition (agent_id, name, engine, instruction, model_config, mcp_servers)
VALUES ('classifier', '意图分类器', 'CHAT',
        '你是意图分类器，分析用户问题类型。如果是常见FAQ问题，输出''type==faq''；如果是复杂问题或需要人工，输出''type==complex''。只输出分类结果，不要解释。',
        '{"name":"qwq-plus","temperature":0.1}', '[]')
ON CONFLICT (agent_id) DO NOTHING;

INSERT INTO agent_definition (agent_id, name, engine, instruction, model_config, mcp_servers)
VALUES ('faq-bot', 'FAQ机器人', 'CHAT',
        '你是FAQ机器人，回答常见的客户问题。',
        '{"name":"qwq-plus","temperature":0.3}', '[]')
ON CONFLICT (agent_id) DO NOTHING;

INSERT INTO agent_definition (agent_id, name, engine, instruction, model_config, mcp_servers)
VALUES ('human-agent', '人工代理', 'CHAT',
        '你是人工代理，处理需要人工介入的复杂客户问题。请先安抚用户情绪，然后提供解决方案或转接路径。',
        '{"name":"qwq-plus","temperature":0.5}', '[]')
ON CONFLICT (agent_id) DO NOTHING;

INSERT INTO agent_definition (agent_id, name, engine, instruction, model_config, mcp_servers)
VALUES ('deep-researcher', '深度研究员', 'CHAT',
        '你是深度研究员，使用工具进行深入搜索和分析。请尽可能全面地收集信息。',
        '{"name":"qwq-plus","temperature":0.7}', '[]')
ON CONFLICT (agent_id) DO NOTHING;

INSERT INTO agent_definition (agent_id, name, engine, instruction, model_config, mcp_servers)
VALUES ('summarizer', '摘要总结员', 'CHAT',
        '你是摘要总结员，将研究结果整理成清晰、有条理的摘要报告。请用中文输出。',
        '{"name":"qwq-plus","temperature":0.3}', '[]')
ON CONFLICT (agent_id) DO NOTHING;

INSERT INTO agent_definition (agent_id, name, engine, instruction, model_config, mcp_servers)
VALUES ('weather-assistant', '天气助手', 'CHAT',
        '你是一个天气查询助手，可以帮助用户查询城市天气信息。请用中文回答。',
        '{"name":"qwq-plus","temperature":0.7,"maxTokens":2000}', '[]')
ON CONFLICT (agent_id) DO NOTHING;

INSERT INTO agent_definition (agent_id, name, engine, instruction, model_config, mcp_servers)
VALUES ('info-collector', '信息收集员', 'CHAT',
        '你是旅行信息收集员，负责从用户那里收集出行需求信息（目的地、出发地、时间、预算等）。请用中文与用户交流。',
        '{"name":"qwq-plus","temperature":0.7}', '[]')
ON CONFLICT (agent_id) DO NOTHING;

INSERT INTO agent_definition (agent_id, name, engine, instruction, model_config, mcp_servers)
VALUES ('route-searcher', '路线搜索员', 'CHAT',
        '你是路线搜索员，根据收集到的旅行需求搜索合适的交通路线。',
        '{"name":"qwq-plus","temperature":0.5}', '[]')
ON CONFLICT (agent_id) DO NOTHING;

INSERT INTO agent_definition (agent_id, name, engine, instruction, model_config, mcp_servers)
VALUES ('plan-generator', '行程生成员', 'CHAT',
        '你是行程生成员，根据收集的信息和搜索到的路线，生成完整的旅行行程计划。请用中文输出。',
        '{"name":"qwq-plus","temperature":0.6}', '[]')
ON CONFLICT (agent_id) DO NOTHING;

-- GRAPH 引擎代理（1个）

INSERT INTO agent_definition (agent_id, name, engine, instruction, model_config, mcp_servers, graph_start)
VALUES ('customer-service', '客服系统', 'GRAPH',
        '你是智能客服系统，根据用户问题类型路由到对应的处理节点。',
        '{"name":"qwq-plus","temperature":0.3}', '[]', 'classify')
ON CONFLICT (agent_id) DO NOTHING;

-- AGENTSCOPE 引擎代理（1个）

INSERT INTO agent_definition (agent_id, name, engine, instruction, model_config, mcp_servers, agentscope_pipeline_type)
VALUES ('research-team', '研究团队', 'AGENTSCOPE',
        '你是一个深度研究团队，能够进行多角度搜索和总结。',
        '{"name":"qwq-plus","temperature":0.5}', '[]', 'sequential')
ON CONFLICT (agent_id) DO NOTHING;

-- HYBRID 引擎代理（1个）

INSERT INTO agent_definition (agent_id, name, engine, instruction, model_config, mcp_servers, graph_start, sub_engines)
VALUES ('research-hybrid', '研究助手', 'HYBRID',
        '你是一个深度研究分析助手，能够进行深度搜索和总结。',
        '{"name":"qwq-plus","temperature":0.5}', '[]', 'research',
        '{"research":"AGENTSCOPE","summarize":"GRAPH"}')
ON CONFLICT (agent_id) DO NOTHING;


-- -----------------------------------------------------------
-- 二、插入 agent_workflow_node（GRAPH + HYBRID 引擎节点）
-- -----------------------------------------------------------

-- customer-service（GRAPH）的3个节点
INSERT INTO agent_workflow_node (agent_definition_id, node_id, agent_id, react_agent_id, next_node_id, sort_order)
SELECT id, 'classify', 'classifier', NULL, NULL, 1
FROM agent_definition WHERE agent_id = 'customer-service';

INSERT INTO agent_workflow_node (agent_definition_id, node_id, agent_id, react_agent_id, next_node_id, sort_order)
SELECT id, 'faq', 'faq-bot', NULL, NULL, 2
FROM agent_definition WHERE agent_id = 'customer-service';

INSERT INTO agent_workflow_node (agent_definition_id, node_id, agent_id, react_agent_id, next_node_id, sort_order)
SELECT id, 'human', 'human-agent', NULL, NULL, 3
FROM agent_definition WHERE agent_id = 'customer-service';

-- research-hybrid（HYBRID）的2个节点
INSERT INTO agent_workflow_node (agent_definition_id, node_id, agent_id, react_agent_id, next_node_id, sort_order)
SELECT id, 'research', NULL, 'deep-researcher', NULL, 1
FROM agent_definition WHERE agent_id = 'research-hybrid';

INSERT INTO agent_workflow_node (agent_definition_id, node_id, agent_id, react_agent_id, next_node_id, sort_order)
SELECT id, 'summarize', 'summarizer', NULL, NULL, 2
FROM agent_definition WHERE agent_id = 'research-hybrid';


-- -----------------------------------------------------------
-- 三、插入 agent_graph_edge（GRAPH + HYBRID 引擎边）
-- -----------------------------------------------------------

-- customer-service（GRAPH）的2条边
INSERT INTO agent_graph_edge (agent_definition_id, from_node, to_node, condition_expr, sort_order)
SELECT id, 'classify', 'faq', 'type==faq', 1
FROM agent_definition WHERE agent_id = 'customer-service';

INSERT INTO agent_graph_edge (agent_definition_id, from_node, to_node, condition_expr, sort_order)
SELECT id, 'classify', 'human', 'type==complex', 2
FROM agent_definition WHERE agent_id = 'customer-service';

-- research-hybrid（HYBRID）的1条边
INSERT INTO agent_graph_edge (agent_definition_id, from_node, to_node, condition_expr, sort_order)
SELECT id, 'research', 'summarize', NULL, 1
FROM agent_definition WHERE agent_id = 'research-hybrid';


-- -----------------------------------------------------------
-- 四、插入 agent_agentscope_config（AGENTSCOPE 引擎代理配置）
-- -----------------------------------------------------------

-- research-team 的2个代理配置
INSERT INTO agent_agentscope_config (agent_definition_id, config_agent_id, mcp_servers, enable_tools, sort_order)
SELECT id, 'deep-researcher', '[]', '["getWeather"]', 1
FROM agent_definition WHERE agent_id = 'research-team';

INSERT INTO agent_agentscope_config (agent_definition_id, config_agent_id, mcp_servers, enable_tools, sort_order)
SELECT id, 'summarizer', '[]', '[]', 2
FROM agent_definition WHERE agent_id = 'research-team';
