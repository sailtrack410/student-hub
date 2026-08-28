CREATE TABLE ai_conversation (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_conversation_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_ai_conversation_user ON ai_conversation (user_id, updated_at);

CREATE TABLE ai_message (
    id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    model VARCHAR(64),
    prompt_version VARCHAR(32),
    token_usage INT,
    cost_ms INT,
    error_code VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_message_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversation (id)
);

CREATE INDEX idx_ai_message_conversation ON ai_message (conversation_id, created_at);
