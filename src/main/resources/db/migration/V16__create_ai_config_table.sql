-- AI 动态配置表：单行表（id 恒为 1），管理后台可修改并即时生效。
-- api_key 留空时，运行时会回退到环境变量 AI_API_KEY。

CREATE TABLE ai_config (
    id BIGINT NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 0,
    provider VARCHAR(32) NOT NULL DEFAULT 'OPENAI_COMPATIBLE',
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(512) NULL,
    model VARCHAR(128) NOT NULL,
    connect_timeout_seconds INT NOT NULL DEFAULT 5,
    read_timeout_seconds INT NOT NULL DEFAULT 60,
    max_input_characters INT NOT NULL DEFAULT 12000,
    hourly_message_limit INT NOT NULL DEFAULT 20,
    history_window INT NOT NULL DEFAULT 10,
    max_message_characters INT NOT NULL DEFAULT 2000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

INSERT INTO ai_config
    (id, enabled, provider, base_url, api_key, model, connect_timeout_seconds, read_timeout_seconds,
     max_input_characters, hourly_message_limit, history_window, max_message_characters,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (1, 0, 'OPENAI_COMPATIBLE', 'https://api.deepseek.com', NULL, 'deepseek-chat', 5, 60,
     12000, 20, 10, 2000,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);