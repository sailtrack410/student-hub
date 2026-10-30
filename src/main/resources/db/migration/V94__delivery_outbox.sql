-- 业务落库和待投递事件共享事务；投递失败不会回滚业务，实例重启后仍可恢复。
CREATE TABLE delivery_outbox (
    event_key VARCHAR(160) NOT NULL,
    topic VARCHAR(32) NOT NULL,
    recipient_id BIGINT NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMP,
    lease_token VARCHAR(36),
    last_error VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_key)
);
CREATE INDEX idx_delivery_outbox_ready ON delivery_outbox(status, available_at, lease_until);
