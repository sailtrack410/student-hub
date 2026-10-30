-- App 安装包版本管理（2026-10-19）：管理端发布新版本包，App 启动检查更新与强制更新
CREATE TABLE app_version (
    id BIGINT NOT NULL,
    platform VARCHAR(16) NOT NULL,
    channel VARCHAR(32) NOT NULL DEFAULT 'OFFICIAL',
    version_code INT NOT NULL,
    version_name VARCHAR(32) NOT NULL,
    min_supported_version_code INT NOT NULL DEFAULT 0,
    force_update TINYINT NOT NULL DEFAULT 0,
    update_log VARCHAR(4000) NULL,
    package_key VARCHAR(255) NULL,
    package_file_name VARCHAR(128) NULL,
    package_size BIGINT NULL,
    package_sha256 VARCHAR(64) NULL,
    download_url VARCHAR(512) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME NULL,
    download_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_version_platform_channel_code (platform, channel, version_code)
);

CREATE INDEX idx_app_version_lookup ON app_version (platform, channel, status);
