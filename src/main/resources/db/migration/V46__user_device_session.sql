-- 设备与安全管理（API需求清单 3.x）：登录设备登记表。
-- 每次登录按 UA+客户端类型生成指纹登记一行（同一设备重复登录覆盖刷新 token_hash 与 last_active_at），
-- 踢设备=删 device_session 行 + 删对应会话。Redis memory 双 store 兼容：踢线动作统一走 SessionStore.deleteByHash。
CREATE TABLE user_device_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_id VARCHAR(64) NOT NULL COMMENT '对外设备 ID（dev_ 前缀随机串）',
    token_hash VARCHAR(128) NOT NULL COMMENT '登录会话 token 摘要（踢线定位）',
    device_name VARCHAR(128) NULL COMMENT 'UA 解析或客户端上报的设备名',
    os_type VARCHAR(32) NULL,
    os_version VARCHAR(32) NULL,
    ip_address VARCHAR(64) NULL,
    client_type VARCHAR(16) NULL COMMENT 'APP/WEB，识别当前设备用',
    last_active_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_device_deviceid (user_id, device_id),
    KEY idx_user_device_token (token_hash)
) COMMENT '用户登录设备登记';
