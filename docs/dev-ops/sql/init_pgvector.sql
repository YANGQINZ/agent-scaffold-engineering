-- Enable PGvector extension
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- Chat domain tables
CREATE TABLE "public"."chat_session" (
                                         "session_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
                                         "name" varchar(128) COLLATE "pg_catalog"."default",
                                         "user_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
                                         "mode" varchar(20) COLLATE "pg_catalog"."default" NOT NULL,
                                         "agent_mode" varchar(20) COLLATE "pg_catalog"."default",
                                         "rag_enabled" bool DEFAULT false,
                                         "knowledge_base_id" varchar(64) COLLATE "pg_catalog"."default",
                                         "created_at" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
                                         "updated_at" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
                                         "ttl_expire_at" timestamp(6),
                                         "agent_id" varchar(64) COLLATE "pg_catalog"."default",
                                         CONSTRAINT "chat_session_pkey" PRIMARY KEY ("session_id")
)
;


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
                                              base_id BIGINT PRIMARY KEY,
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
    content TEXT COLLATE "pg_catalog"."default",
    metadata JSON,
    embedding "public"."vector"(1024),
    content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    CONSTRAINT vector_store_pkey PRIMARY KEY ("id")
    );

-- CREATE INDEX IF NOT EXISTS idx_document_chunk_base_id ON document_chunk((metadata->>'baseId'));
CREATE INDEX IF NOT EXISTS idx_chunk_embedding ON document_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_chunk_content_tsv ON document_chunk USING GIN (content_tsv);

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
