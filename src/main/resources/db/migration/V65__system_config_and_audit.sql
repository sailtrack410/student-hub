-- 管理端配置中心与审计日志：非密钥运行配置 + 操作/配置变更审计。
-- 密钥不落库明文，仍由环境变量/Secret 管理；DB 只保存可运行、非敏感配置。

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL DEFAULT 0,
    config_group VARCHAR(64) NOT NULL,
    config_key VARCHAR(128) NOT NULL,
    config_value TEXT NULL,
    value_type VARCHAR(16) NOT NULL DEFAULT 'STRING',
    is_secret TINYINT NOT NULL DEFAULT 0,
    description VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_config_scope_key (campus_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_sys_config_group ON sys_config (config_group, status);

CREATE TABLE IF NOT EXISTS sys_audit_log (
    id BIGINT NOT NULL,
    operator_id BIGINT NULL,
    operator_name VARCHAR(128) NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NULL,
    before_json TEXT NULL,
    after_json TEXT NULL,
    ip VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_audit_operator ON sys_audit_log (operator_id, created_at);
CREATE INDEX idx_audit_resource ON sys_audit_log (resource_type, resource_id, created_at);
CREATE INDEX idx_audit_action ON sys_audit_log (action, created_at);

-- 非敏感默认配置（campus_id=0 为平台级，后续可覆盖为校区级）
INSERT IGNORE INTO sys_config
    (id, campus_id, config_group, config_key, config_value, value_type, is_secret, description, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (6501, 0, 'platform', 'platform.name', '绵阳城市学院', 'STRING', 0, '学校名称', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6502, 0, 'platform', 'platform.short_name', '绵城', 'STRING', 0, '学校简称', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6503, 0, 'platform', 'platform.default_campus_id', '1', 'LONG', 0, '默认校区 ID', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6504, 0, 'platform', 'brand.primary_color', '#0F766E', 'STRING', 0, '管理端/App 品牌主色', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6505, 0, 'platform', 'contact.phone', '0816-6280000', 'STRING', 0, '客服电话', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6506, 0, 'platform', 'contact.email', 'support@mycc.edu.cn', 'STRING', 0, '服务邮箱', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6507, 0, 'platform', 'legal.privacy_url', '/legal/privacy', 'STRING', 0, '隐私政策地址', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6508, 0, 'platform', 'legal.terms_url', '/legal/terms', 'STRING', 0, '用户协议地址', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6509, 0, 'platform', 'legal.icp', '', 'STRING', 0, 'ICP 备案号', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6510, 0, 'feature', 'feature.maintenance', 'false', 'BOOLEAN', 0, '维护模式', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6511, 0, 'feature', 'feature.register', 'true', 'BOOLEAN', 0, '注册开关', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6512, 0, 'feature', 'feature.password_login', 'true', 'BOOLEAN', 0, '密码登录开关（生产仍受环境变量约束）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6513, 0, 'feature', 'feature.wechat_login', 'true', 'BOOLEAN', 0, '微信登录开关', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6514, 0, 'feature', 'feature.qq_login', 'true', 'BOOLEAN', 0, 'QQ 登录开关', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6515, 0, 'feature', 'feature.ai', 'false', 'BOOLEAN', 0, 'AI 助手开关', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6516, 0, 'feature', 'feature.rag', 'false', 'BOOLEAN', 0, 'RAG 知识库开关', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6517, 0, 'feature', 'feature.community', 'true', 'BOOLEAN', 0, '社区开关', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6518, 0, 'feature', 'feature.homework', 'true', 'BOOLEAN', 0, '作业中心开关', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6519, 0, 'feature', 'feature.push', 'false', 'BOOLEAN', 0, '推送开关', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6520, 0, 'feature', 'feature.electricity', 'true', 'BOOLEAN', 0, '电费查询开关', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6521, 0, 'feature', 'feature.location', 'true', 'BOOLEAN', 0, '定位/地点开关', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6522, 0, 'feature', 'feature.app_update', 'true', 'BOOLEAN', 0, 'App 更新检查开关', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6523, 0, 'community', 'community.feed_ad_interval', '8', 'INT', 0, '信息流每 N 条插入一条广告', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6524, 0, 'community', 'community.ad_bounce_seconds', '5', 'INT', 0, '广告落地跳出统计秒数 N', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6525, 0, 'security', 'security.session_ttl_hours', '8', 'INT', 0, '平台会话时长（小时，需重启/缓存刷新后生效）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT IGNORE INTO sys_permission
    (id, permission_code, permission_name, permission_type, status, created_at, updated_at, created_by, updated_by,
     deleted, version)
VALUES
    (26, 'system:config:view', '配置中心查看', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (27, 'system:config:manage', '配置中心管理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (28, 'system:audit:view', '审计日志查看', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (29, 'system:audit:export', '审计日志导出', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, created_at, created_by)
VALUES
    (1701, 1, 26, CURRENT_TIMESTAMP, 0),
    (1702, 1, 27, CURRENT_TIMESTAMP, 0),
    (1703, 1, 28, CURRENT_TIMESTAMP, 0),
    (1704, 1, 29, CURRENT_TIMESTAMP, 0);
