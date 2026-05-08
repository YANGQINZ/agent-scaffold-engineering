-- ============================================================
-- V2026050801: 创建 MCP Server 配置表
-- ============================================================

CREATE TABLE IF NOT EXISTS mcp_server_config (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    transport   VARCHAR(32) NOT NULL,
    command     VARCHAR(512),
    args        JSONB,
    url         VARCHAR(512),
    headers     JSONB,
    description VARCHAR(512),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mcp_config_name ON mcp_server_config(name);

ALTER TABLE mcp_server_config ADD CONSTRAINT chk_mcp_transport
    CHECK (transport IN ('stdio', 'sse', 'streamableHttp'));

COMMENT ON TABLE  mcp_server_config IS 'MCP Server 配置表';
COMMENT ON COLUMN mcp_server_config.id IS '主键';
COMMENT ON COLUMN mcp_server_config.name IS '配置名称（唯一）';
COMMENT ON COLUMN mcp_server_config.transport IS '传输协议：stdio/sse/streamableHttp';
COMMENT ON COLUMN mcp_server_config.command IS 'stdio 可执行命令';
COMMENT ON COLUMN mcp_server_config.args IS '命令参数（JSON 数组）';
COMMENT ON COLUMN mcp_server_config.url IS 'HTTP 端点 URL';
COMMENT ON COLUMN mcp_server_config.headers IS 'HTTP 请求头（JSON 对象）';
COMMENT ON COLUMN mcp_server_config.description IS '配置描述';
COMMENT ON COLUMN mcp_server_config.created_at IS '创建时间';
COMMENT ON COLUMN mcp_server_config.updated_at IS '更新时间';
