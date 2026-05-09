-- 补充 agent_agentscope_config 表缺失的列
-- DDL (V2026042901) 创建时遗漏，但 PO/Mapper/Repository 代码已引用

ALTER TABLE agent_agentscope_config
    ADD COLUMN IF NOT EXISTS name        VARCHAR(128),
    ADD COLUMN IF NOT EXISTS instruction TEXT,
    ADD COLUMN IF NOT EXISTS output_key  VARCHAR(64);

COMMENT ON COLUMN agent_agentscope_config.name        IS 'Agent 名称（前端节点标签）';
COMMENT ON COLUMN agent_agentscope_config.instruction IS '内联指令（避免需要AgentRegistry查找）';
COMMENT ON COLUMN agent_agentscope_config.output_key  IS 'OverAllState 中的输出键';
