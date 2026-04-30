-- 为 chat_session 表添加 name 列，支持会话命名
ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS name VARCHAR(256);
COMMENT ON COLUMN chat_session.name IS '会话名称';
