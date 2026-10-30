-- 普通发送和 SSE 共用请求键；占位事务先提交，消息和终态随后原子提交。
CREATE TABLE ai_chat_request (
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    client_request_id VARCHAR(96) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, conversation_id, client_request_id)
);
