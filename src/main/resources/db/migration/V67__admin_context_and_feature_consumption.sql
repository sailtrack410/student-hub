-- 管理端校区上下文与 App 功能开关消费基础。
-- 说明：只追加，不修改 V66 已应用内容；语法兼容 MySQL 8.0 与 H2 2.x（MODE=MySQL）。

-- 社区广告独立开关：客户端读取 /api/common/features 后决定是否请求/展示广告。
INSERT IGNORE INTO sys_config
    (id, campus_id, config_group, config_key, config_value, value_type, is_secret, description, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (6529, 0, 'feature', 'feature.community_ads', 'true', 'BOOLEAN', 0, '社区广告开关（关闭后 App 不展示官方直投广告）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);
