-- 将 chat_session 表的 agent_mode 列重命名为 engine
ALTER TABLE chat_session RENAME COLUMN agent_mode TO engine;
