-- 为 chat_session 表添加 agent_id 列，支持按 Agent 维度查询会话历史
ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS agent_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_chat_session_agent_id ON chat_session(agent_id);
COMMENT ON COLUMN chat_session.agent_id IS '关联的Agent定义ID';
