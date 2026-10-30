-- V74：修复 V72 平台配置种子与既有 feature 配置 ID 冲突导致 platform.logo_url 未插入的问题。
-- V72 使用固定 ID 6529 插入 platform.logo_url，但历史配置已占用 6529（feature.community_ads），
-- INSERT IGNORE 跳过了该行；此处用新的固定 ID 补种，保持系统配置页 SYS01 完整。
INSERT IGNORE INTO sys_config
    (id, campus_id, config_group, config_key, config_value, value_type, is_secret, description, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (6544, 0, 'platform', 'platform.logo_url', '', 'STRING', 0, '平台 Logo 地址（PNG/SVG，建议 512×512）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);
