-- AI 供应商/模型管理：支持多供应商接入、模型列表与按类型默认模型。
-- 旧 ai_config 单配置行的连接信息迁移到 ai_provider/ai_model，并降级为纯全局设置表。

CREATE TABLE ai_provider (
    id BIGINT NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    provider_name VARCHAR(64) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(512) NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    last_check_at DATETIME NULL,
    last_check_ok TINYINT NULL,
    last_check_result VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_provider_code (provider_code)
);

CREATE TABLE ai_model (
    id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_model_id VARCHAR(128) NOT NULL,
    model_type VARCHAR(32) NOT NULL DEFAULT 'CHAT',
    capabilities VARCHAR(255) NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_model_provider_model (provider_id, provider_model_id)
);

CREATE TABLE ai_default_model (
    id BIGINT NOT NULL,
    type_code VARCHAR(32) NOT NULL,
    model_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_default_type (type_code)
);

-- 迁移旧单配置行：建默认供应商 + 默认聊天模型
INSERT INTO ai_provider (id, provider_code, provider_name, base_url, api_key, enabled, created_by, updated_by)
SELECT 1, 'default', '默认供应商', base_url, api_key, enabled, 0, 0 FROM ai_config WHERE id = 1;

INSERT INTO ai_model (id, provider_id, provider_model_id, model_type, enabled, created_by, updated_by)
SELECT 1, 1, model, 'CHAT', 1, 0, 0 FROM ai_config WHERE id = 1;

INSERT INTO ai_default_model (id, type_code, model_id, created_by, updated_by)
VALUES (1, 'CHAT', 1, 0, 0);

-- ai_config 降级为纯全局设置（总开关 + 超时 + 限流），连接类字段移交 ai_provider/ai_model
ALTER TABLE ai_config
    DROP COLUMN provider,
    DROP COLUMN base_url,
    DROP COLUMN api_key,
    DROP COLUMN model;
