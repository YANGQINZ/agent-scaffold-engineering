-- Enable PGvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Chat domain tables
CREATE TABLE IF NOT EXISTS chat_session (
    session_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128),
    user_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64),
    mode VARCHAR(20) NOT NULL,
    agent_mode VARCHAR(20),
    rag_enabled BOOLEAN DEFAULT FALSE,
    knowledge_base_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ttl_expire_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_message (
    message_id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL REFERENCES chat_session(session_id),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    token_count INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_message_session_created ON chat_message(session_id, created_at);

-- Knowledge domain tables
CREATE TABLE IF NOT EXISTS knowledge_base (
    base_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    file_name VARCHAR(256),
    file_type VARCHAR(10),
    owner_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    owner_id VARCHAR(64),
    doc_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document_chunk (
    id UUID NOT NULL DEFAULT uuid_generate_v4(),
    content TEXT,
    metadata JSON,
    embedding vector,
    content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    CONSTRAINT vector_store_pkey PRIMARY KEY ("id")
);

CREATE INDEX IF NOT EXISTS idx_chunk_embedding ON document_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_chunk_content_tsv ON document_chunk USING GIN (content_tsv);

-- Memory domain table
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
