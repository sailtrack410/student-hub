-- V72：工作台/分析/SLA/系统/文件/任务（admin-ops bundle）
-- 1) 节假日与工作日历：SLA 看板按 service_item.sla_hours + 本表推算承诺截止时间（节假日顺延）
-- 2) 平台信息/登录安全/文件存储/SLA 口径配置项（非密钥，落 sys_config）
-- 3) 新增权限点：数据分析查看、SLA 查看、文件管理（默认仅平台管理员）

-- ---------------------------------------------------------------------------
-- 1) 节假日/工作日历
--    is_workday = 1 时表示“调休上班日”（周末但计为工作日），= 0 表示节假日。
--    campus_id = 0 为平台级，可扩展为校区级；唯一键 (campus_id, holiday_date)。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS service_holiday (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL DEFAULT 0,
    holiday_date DATE NOT NULL,
    holiday_name VARCHAR(64) NOT NULL,
    is_workday TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_service_holiday_scope_date UNIQUE (campus_id, holiday_date)
);

CREATE INDEX idx_service_holiday_date ON service_holiday (holiday_date, campus_id);

-- ---------------------------------------------------------------------------
-- 2) 配置项（campus_id = 0 为平台级默认值）
--    说明：logo/favicon/theme 会在系统配置页展示并由 App/管理端读取；
--    security.* 当前仅保存到配置中心，运行时鉴权策略（密码强度、验证码、设备上限、CORS）
--    尚需在安全过滤器/设备会话模块消费，页面会明确标注“配置已保存，运行时策略待接”。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_config
    (id, campus_id, config_group, config_key, config_value, value_type, is_secret, description, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (6529, 0, 'platform', 'platform.logo_url', '', 'STRING', 0, '平台 Logo 地址（PNG/SVG，建议 512×512）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6530, 0, 'platform', 'platform.favicon_url', '', 'STRING', 0, 'Favicon 地址（ICO/PNG，建议 64×64）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6531, 0, 'platform', 'platform.theme_mode', 'light', 'STRING', 0, '默认主题：light / dark（管理端与 App 读取）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6532, 0, 'security', 'security.password_min_length', '8', 'INT', 0, '密码最小长度（配置中心保存，运行时策略待接）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6533, 0, 'security', 'security.password_require_complex', 'true', 'BOOLEAN', 0, '密码需包含字母与数字（运行时策略待接）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6534, 0, 'security', 'security.login_captcha_enabled', 'false', 'BOOLEAN', 0, '登录图形验证码开关（运行时策略待接）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6535, 0, 'security', 'security.max_devices_per_user', '5', 'INT', 0, '单用户同时在线设备上限（运行时策略待接）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6536, 0, 'security', 'security.cors_allowed_origins', '', 'STRING', 0, '管理端 CORS 白名单（逗号分隔；运行时策略待接）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6537, 0, 'file', 'file.max_size_mb', '5', 'INT', 0, '单文件大小上限（MB，需重启并接入 FileProperties 消费后生效）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6538, 0, 'file', 'file.view_ttl_minutes', '30', 'INT', 0, '签名访问链接有效期（分钟，需重启后生效）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6539, 0, 'file', 'file.storage', 'local', 'STRING', 0, '存储实现：local / minio（需重启后生效）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6540, 0, 'file', 'file.minio_bucket', 'student-hub', 'STRING', 0, 'MinIO 桶名称（需重启后生效）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6541, 0, 'sla', 'sla.workday_start_hour', '9', 'INT', 0, '工作日服务时段开始（整点，SLA 截止推算使用）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6542, 0, 'sla', 'sla.workday_end_hour', '18', 'INT', 0, '工作日服务时段结束（整点，SLA 截止推算使用）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6543, 0, 'sla', 'sla.weekend_workday', 'false', 'BOOLEAN', 0, '周六周日是否计为工作日（SLA 截止推算使用）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

-- ---------------------------------------------------------------------------
-- 3) 权限点：分析与 SLA 为只读、文件管理含删除；默认仅平台管理员
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_permission
    (id, permission_code, permission_name, permission_type, status, created_at, updated_at, created_by, updated_by,
     deleted, version)
VALUES
    (34, 'analytics:view', '数据分析查看', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (35, 'sla:view', 'SLA 看板查看', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (36, 'file:manage', '文件存储管理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, created_at, created_by)
VALUES
    (7201, 1, 34, CURRENT_TIMESTAMP, 0),
    (7202, 1, 35, CURRENT_TIMESTAMP, 0),
    (7203, 1, 36, CURRENT_TIMESTAMP, 0);
