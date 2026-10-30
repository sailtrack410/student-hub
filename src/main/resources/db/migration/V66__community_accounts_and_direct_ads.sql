-- 社区商业化基础能力：账号类型、官方直投广告、广告事件链路与信息流投放。
-- 说明：
-- 1) 本迁移只做追加，不改动 V60-V65 已应用内容；
-- 2) 语法同时兼容 MySQL 8.0 与 H2 2.x（MODE=MySQL）：逐列 ALTER、不使用多列 ADD COLUMN；
-- 3) 不插入广告演示数据，官方直投广告由管理端创建。

-- ---------------------------------------------------------------------------
-- 1) 社区账号类型：sys_user 扩展（兼容 V60 的 official 标记）
-- ---------------------------------------------------------------------------
ALTER TABLE sys_user ADD COLUMN account_type VARCHAR(32) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE sys_user ADD COLUMN account_operator VARCHAR(128) NULL;
ALTER TABLE sys_user ADD COLUMN account_description VARCHAR(500) NULL;
ALTER TABLE sys_user ADD COLUMN verification_status VARCHAR(16) NOT NULL DEFAULT 'NONE';
CREATE INDEX idx_sys_user_account_type ON sys_user (account_type, status);

-- V60 的官方账号均为学校官网抓取/官方发布主体，按自动号回填，保证官方抓取来源仍可用。
UPDATE sys_user SET account_type = 'OFFICIAL_AUTO', verification_status = 'APPROVED' WHERE official = 1;

-- ---------------------------------------------------------------------------
-- 2) 官方直投广告（不绑定社区账号，独立投放与统计）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS community_direct_ad (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    brand_name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    cover_url VARCHAR(512) NULL,
    target_url VARCHAR(512) NOT NULL,
    landing_type VARCHAR(32) NOT NULL DEFAULT 'IN_APP_WEBVIEW',
    section_id BIGINT NULL,
    slot VARCHAR(16) NOT NULL DEFAULT 'FEED',
    priority INT NOT NULL DEFAULT 0,
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    bounce_threshold_seconds INT NOT NULL DEFAULT 5,
    cta_text VARCHAR(32) NULL,
    sponsored_label VARCHAR(32) NOT NULL DEFAULT '推广',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_community_direct_ad_feed ON community_direct_ad (campus_id, slot, status, priority);
CREATE INDEX idx_community_direct_ad_window ON community_direct_ad (start_at, end_at);

-- ---------------------------------------------------------------------------
-- 3) 广告事件：IMPRESSION / CLICK / LANDING / DWELL
--    click_id 串起「曝光→点击→落地→停留/跳出」链路；
--    (click_id, event_type) 唯一保证点击、落地、停留事件幂等（click_id 为 NULL 的曝光由前端每次生成）。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS community_ad_event (
    id BIGINT NOT NULL,
    ad_id BIGINT NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    user_id BIGINT NULL,
    session_id VARCHAR(64) NULL,
    click_id VARCHAR(64) NULL,
    dwell_ms BIGINT NULL,
    bounced TINYINT NULL,
    landing_type VARCHAR(32) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX uk_community_ad_event_click_type ON community_ad_event (click_id, event_type);
CREATE INDEX idx_community_ad_event_ad ON community_ad_event (ad_id, event_type, created_at);
CREATE INDEX idx_community_ad_event_click ON community_ad_event (click_id);
CREATE INDEX idx_community_ad_event_user ON community_ad_event (user_id, created_at);

-- ---------------------------------------------------------------------------
-- 4) 权限点：仅平台管理员默认拥有；校方社区运营人员可在管理端按需授权。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_permission
    (id, permission_code, permission_name, permission_type, status, created_at, updated_at, created_by, updated_by,
     deleted, version)
VALUES
    (30, 'community:official:manage', '官方账号管理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (31, 'community:ad:manage', '官方直投广告管理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, created_at, created_by)
VALUES
    (1801, 1, 30, CURRENT_TIMESTAMP, 0),
    (1802, 1, 31, CURRENT_TIMESTAMP, 0);

-- ---------------------------------------------------------------------------
-- 5) 信息流投放参数（V65 已有插入间隔与跳出统计秒数，这里补频控上限；campus_id=0 为平台默认）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_config
    (id, campus_id, config_group, config_key, config_value, value_type, is_secret, description, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (6526, 0, 'community', 'community.ad_daily_impression_cap', '20', 'INT', 0, '单用户单日信息流广告曝光上限', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6527, 0, 'community', 'community.ad_user_ad_daily_cap', '3', 'INT', 0, '单用户单个广告单日曝光上限', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6528, 0, 'community', 'community.ad_session_impression_cap', '5', 'INT', 0, '单用户单次会话信息流广告曝光上限', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);
