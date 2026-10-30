-- SYS02：功能开关灰度放量；未配置行按 100%（全量）处理。
CREATE TABLE IF NOT EXISTS sys_feature_rollout (
    id BIGINT NOT NULL PRIMARY KEY,
    feature_key VARCHAR(64) NOT NULL,
    campus_id BIGINT NOT NULL DEFAULT 0,
    rollout_percent INT NOT NULL DEFAULT 100,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_feature_rollout_scope UNIQUE (feature_key, campus_id)
);
CREATE INDEX idx_feature_rollout_campus ON sys_feature_rollout (campus_id, feature_key);
