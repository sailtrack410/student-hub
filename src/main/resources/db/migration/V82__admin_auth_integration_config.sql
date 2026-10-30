-- V82：QQ / 微信 / 邮箱三类登录与发信配置进入管理端配置中心。
-- 敏感值（AppSecret / APP KEY / SMTP 密码）使用 AES-256-GCM 加密后落 sys_config，
-- 页面只写不读；主密钥来自环境变量 CONFIG_SECRET_KEY，不落库、不进 Git。
-- 初始值留空：应用启动时会把当前环境变量中的非空值迁移到本表（updated_by 仍为 0），
-- 之后以数据库值优先；管理员保存后 updated_by 记录操作人，空值即视为显式清空。

INSERT IGNORE INTO sys_config
    (id, campus_id, config_group, config_key, config_value, value_type, is_secret, description, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    -- QQ 登录
    (6601, 0, 'qq', 'qq.mode', '', 'STRING', 0, 'QQ 登录模式：real（真实反查）/ mock（本地联调）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6602, 0, 'qq', 'qq.client_id', '', 'STRING', 0, 'QQ 互联移动应用 APP ID（客户端只需此 ID）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6603, 0, 'qq', 'qq.client_secret', '', 'STRING', 1, 'QQ 互联 APP KEY（加密存储，预留给网站授权码流程）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6604, 0, 'qq', 'qq.api_base_url', '', 'STRING', 0, 'QQ 互联 API 地址（默认 https://graph.qq.com）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6605, 0, 'qq', 'qq.default_campus_id', '', 'LONG', 0, 'QQ 新用户默认校区 ID', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6606, 0, 'qq', 'qq.connect_timeout_seconds', '', 'INT', 0, 'QQ API 连接超时（秒）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6607, 0, 'qq', 'qq.read_timeout_seconds', '', 'INT', 0, 'QQ API 读取超时（秒）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),

    -- 微信登录
    (6611, 0, 'wechat', 'wechat.mode', '', 'STRING', 0, '微信登录模式：real（真实 jscode2session）/ mock（本地联调）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6612, 0, 'wechat', 'wechat.app_id', '', 'STRING', 0, '微信小程序 AppID', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6613, 0, 'wechat', 'wechat.app_secret', '', 'STRING', 1, '微信小程序 AppSecret（加密存储）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6614, 0, 'wechat', 'wechat.api_base_url', '', 'STRING', 0, '微信 API 地址（默认 https://api.weixin.qq.com）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6615, 0, 'wechat', 'wechat.default_campus_id', '', 'LONG', 0, '微信新用户默认校区 ID', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6616, 0, 'wechat', 'wechat.connect_timeout_seconds', '', 'INT', 0, '微信 API 连接超时（秒）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6617, 0, 'wechat', 'wechat.read_timeout_seconds', '', 'INT', 0, '微信 API 读取超时（秒）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),

    -- 邮箱 SMTP
    (6621, 0, 'mail', 'mail.enabled', '', 'BOOLEAN', 0, '启用校园邮箱验证码真实发信', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6622, 0, 'mail', 'mail.host', '', 'STRING', 0, 'SMTP 服务器地址', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6623, 0, 'mail', 'mail.port', '', 'INT', 0, 'SMTP 端口（465/587/25）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6624, 0, 'mail', 'mail.username', '', 'STRING', 0, 'SMTP 登录账号', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6625, 0, 'mail', 'mail.password', '', 'STRING', 1, 'SMTP 登录密码或授权码（加密存储）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6626, 0, 'mail', 'mail.from', '', 'STRING', 0, '发件人地址；留空时回退到 SMTP 登录账号', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6627, 0, 'mail', 'mail.from_name', '', 'STRING', 0, '发件人显示名', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6628, 0, 'mail', 'mail.ssl_enabled', '', 'BOOLEAN', 0, 'SMTP 使用 SSL（465 常用 true）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6629, 0, 'mail', 'mail.starttls_enabled', '', 'BOOLEAN', 0, 'SMTP 使用 STARTTLS（587 常用 true）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6630, 0, 'mail', 'mail.connect_timeout_ms', '', 'INT', 0, 'SMTP 连接超时（毫秒）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6631, 0, 'mail', 'mail.read_timeout_ms', '', 'INT', 0, 'SMTP 读取超时（毫秒）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6632, 0, 'mail', 'mail.write_timeout_ms', '', 'INT', 0, 'SMTP 写入超时（毫秒）', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6633, 0, 'mail', 'mail.dev_return_code', '', 'BOOLEAN', 0, '开发/测试环境响应返回验证码；生产 profile 永不返回', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);
