-- 创建记忆存储表 memory_item
-- 用于 MemoryFacade 的语义检索和记忆管理
CREATE TABLE IF NOT EXISTS memory_item (
    id            BIGSERIAL PRIMARY KEY,
    session_id    VARCHAR(64) NOT NULL REFERENCES chat_session(session_id),
    content       TEXT NOT NULL,
    embedding     vector(1024) NOT NULL,
    importance    FLOAT DEFAULT 0.5,
    tags          TEXT[] DEFAULT '{}',
    source_msg_ids BIGINT[] DEFAULT '{}',
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metadata      JSONB
);

CREATE INDEX IF NOT EXISTS idx_memory_embedding
    ON memory_item USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_memory_session_id
    ON memory_item(session_id);

CREATE INDEX IF NOT EXISTS idx_memory_tags
    ON memory_item USING GIN (tags);
