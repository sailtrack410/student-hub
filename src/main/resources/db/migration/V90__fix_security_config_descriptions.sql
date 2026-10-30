-- 修正安全配置说明与运行时钳制范围一致（V89 已执行，不修改历史迁移文件）。
-- 密码最小长度运行时范围为 6–64；广告事件/点击上限配置为 0 时表示不限制。

UPDATE sys_config
SET description = '密码最小长度（运行时读取；默认 8，运行时钳制 6-64）'
WHERE config_key = 'security.password_min_length';

UPDATE sys_config
SET description = '单用户广告事件（曝光/落地）每分钟上限，0 表示不限制'
WHERE config_key = 'community.ad_event_rate_per_minute';

UPDATE sys_config
SET description = '单用户广告点击每分钟上限，0 表示不限制'
WHERE config_key = 'community.ad_click_rate_per_minute';
