-- Enable PGvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Chat domain tables
CREATE TABLE IF NOT EXISTS chat_session (
    session_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
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
    owner_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    owner_id VARCHAR(64),
    doc_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document (
    doc_id VARCHAR(64) PRIMARY KEY,
    base_id VARCHAR(64) NOT NULL REFERENCES knowledge_base(base_id),
    file_name VARCHAR(256),
    file_type VARCHAR(10),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    chunk_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document_chunk (
    chunk_id BIGSERIAL PRIMARY KEY,
    doc_id VARCHAR(64) NOT NULL REFERENCES document(doc_id),
    base_id VARCHAR(64) NOT NULL REFERENCES knowledge_base(base_id),
    content TEXT NOT NULL,
    embedding vector(1536),
    metadata JSONB,
    chunk_index INT NOT NULL,
    content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED
);

CREATE INDEX IF NOT EXISTS idx_document_chunk_base_id ON document_chunk(base_id);
CREATE INDEX IF NOT EXISTS idx_document_chunk_doc_id ON document_chunk(doc_id);
CREATE INDEX IF NOT EXISTS idx_chunk_embedding ON document_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_chunk_content_tsv ON document_chunk USING GIN (content_tsv);
