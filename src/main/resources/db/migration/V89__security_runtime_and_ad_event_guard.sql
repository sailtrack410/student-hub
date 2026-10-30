-- 安全策略运行时生效与广告事件防刷配置（V89）
--
-- 1) security.* 不再只是展示项：密码策略、验证码、设备上限、会话 TTL、CORS 已在运行时读取；
-- 2) 新增广告事件限流与点击归因开关，供 CommunityAdEventGuard 使用。
-- 所有语句保持幂等，H2（MODE=MySQL）与 MySQL 8 均可执行。

UPDATE sys_config
SET description = '密码最小长度（运行时读取；默认 8，最大 128）'
WHERE config_key = 'security.password_min_length';

UPDATE sys_config
SET description = '密码需同时包含字母和数字（运行时读取）'
WHERE config_key = 'security.password_require_complex';

UPDATE sys_config
SET description = '登录图形验证码开关（运行时读取；开启后密码登录必须携带验证码）'
WHERE config_key = 'security.login_captcha_enabled';

UPDATE sys_config
SET description = '单用户同时在线设备上限（运行时读取；新设备登录时踢除最旧设备，0 表示不限制）'
WHERE config_key = 'security.max_devices_per_user';

UPDATE sys_config
SET description = '平台 Web/管理端会话时长（小时，运行时读取；App 长期会话不受此项影响）'
WHERE config_key = 'security.session_ttl_hours';

UPDATE sys_config
SET description = '管理端 CORS 白名单（逗号分隔；非空时覆盖环境变量，支持 * 通配，运行时读取）'
WHERE config_key = 'security.cors_allowed_origins';

INSERT IGNORE INTO sys_config
    (id, campus_id, config_group, config_key, config_value, value_type, is_secret,
     description, status, created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (6545, 0, 'community', 'community.ad_event_rate_per_minute', '120', 'INT', 0,
     '单用户广告事件（曝光/落地）每分钟上限，0 表示不限制', 'ENABLED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6546, 0, 'community', 'community.ad_click_rate_per_minute', '30', 'INT', 0,
     '单用户广告点击每分钟上限，0 表示不限制', 'ENABLED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6547, 0, 'community', 'community.ad_click_requires_impression', 'true', 'BOOLEAN', 0,
     '点击必须存在同用户、同广告、30 分钟内的有效曝光', 'ENABLED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);
