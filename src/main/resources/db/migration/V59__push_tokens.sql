-- 系统推送设备 Token（FCM/APNs/Web Push 统一登记）
-- H2/MySQL 双兼容：去掉 ENGINE/CHARSET/COMMENT/ON UPDATE
CREATE TABLE IF NOT EXISTS sys_push_token (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    platform VARCHAR(16) NOT NULL,
    token VARCHAR(512) NOT NULL,
    device_id VARCHAR(128) NULL,
    last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_push_token (token)
);

CREATE INDEX idx_push_user ON sys_push_token (user_id);
