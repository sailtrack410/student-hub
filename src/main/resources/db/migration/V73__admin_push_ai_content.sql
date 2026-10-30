-- V73 管理端增长运营（admin-growth bundle）
-- 覆盖：推送 N02/N03/N04、AI 安全 I04（版本化 Prompt + 安全策略）、内容运营 A03/C09/C10
--      （推荐位/内容日历、创作者认证/处罚/申诉）。
--
-- 兼容性说明：
-- 1) 仅新增表、索引与配置种子，不改动 V72 及更早迁移；
-- 2) 类型使用 TIMESTAMP/TINYINT/TEXT，同时兼容 MySQL 8.0 与 H2 2.x（MODE=MySQL）；
-- 3) 主键由应用层雪花 ID（MyBatis-Plus ASSIGN_ID）生成，不使用自增；
-- 4) 所有业务表带 created_at/updated_at/created_by/updated_by/deleted/version，与 BaseEntity 对齐；
-- 5) 外键一律不建（与既有表一致），引用完整性由应用层校验。
--
-- 说明：内容运营表（content_slot/content_calendar/creator_*）与推送同属「增长运营」交付包，
-- 但当前工作区写权限仅开放 modules/push，故 Java 侧暂置于 modules.push.content 子包，
-- 建议后续迁至独立 modules/growth 包（不影响表结构与接口）。

-- ---------------------------------------------------------------------------
-- 1) 推送模板（N02）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS push_template (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    scene VARCHAR(32) NOT NULL,
    title_template VARCHAR(200) NOT NULL,
    content_template VARCHAR(2000) NOT NULL,
    variables VARCHAR(1000) NULL,
    default_channels VARCHAR(100) NOT NULL DEFAULT 'LOG',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    remark VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_template_code UNIQUE (campus_id, template_code)
);

CREATE INDEX idx_push_template_scene ON push_template (campus_id, scene, status);

-- ---------------------------------------------------------------------------
-- 2) 推送任务（N03）：人群 / 渠道 / 定时 / 频控 / 状态 / 触达计数
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS push_task (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    task_name VARCHAR(100) NOT NULL,
    template_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    variables VARCHAR(1000) NULL,
    audience_type VARCHAR(20) NOT NULL DEFAULT 'ALL',
    audience_value VARCHAR(1000) NULL,
    channels VARCHAR(100) NOT NULL DEFAULT 'LOG',
    schedule_type VARCHAR(16) NOT NULL DEFAULT 'NOW',
    scheduled_at TIMESTAMP NULL,
    frequency_limit INT NOT NULL DEFAULT 3,
    frequency_window_hours INT NOT NULL DEFAULT 24,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    delivered_count INT NOT NULL DEFAULT 0,
    clicked_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    not_configured_count INT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500) NULL,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    remark VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_push_task_status ON push_task (campus_id, status, scheduled_at);
CREATE INDEX idx_push_task_created ON push_task (campus_id, created_at);

-- ---------------------------------------------------------------------------
-- 3) 推送逐用户投递明细（N04 触达统计的原子数据）
--    status: PENDING / SENT / DELIVERED / CLICKED / FAILED / NOT_CONFIGURED / SKIPPED
--    语义：SENT=已交给通道但无到达证明；DELIVERED=通道确认到达（如在线 WebSocket 写入成功）；
--          NOT_CONFIGURED=通道未配置，不计入失败也不算成功。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS push_task_delivery (
    id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_message VARCHAR(200) NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    attempts INT NOT NULL DEFAULT 1,
    sent_at TIMESTAMP NULL,
    delivered_at TIMESTAMP NULL,
    clicked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_delivery_target UNIQUE (task_id, user_id, channel)
);

CREATE INDEX idx_push_delivery_task_status ON push_task_delivery (task_id, status);
CREATE INDEX idx_push_delivery_user_time ON push_task_delivery (user_id, sent_at);
CREATE INDEX idx_push_delivery_campus_time ON push_task_delivery (campus_id, created_at, channel);

-- ---------------------------------------------------------------------------
-- 4) AI 版本化提示词（I04）
--    同一 prompt_code 多版本；active=1 表示运行时生效版本（每 code 至多一个，应用层保证）。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_prompt_template (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL DEFAULT 0,
    prompt_code VARCHAR(64) NOT NULL,
    prompt_name VARCHAR(100) NOT NULL,
    scene VARCHAR(32) NOT NULL,
    system_prompt TEXT NOT NULL,
    user_prompt_template TEXT NULL,
    variables VARCHAR(1000) NULL,
    version_no INT NOT NULL DEFAULT 1,
    active TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    change_note VARCHAR(200) NULL,
    remark VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_prompt_code_version UNIQUE (campus_id, prompt_code, version_no)
);

CREATE INDEX idx_ai_prompt_scene ON ai_prompt_template (campus_id, scene, active);

-- ---------------------------------------------------------------------------
-- 5) AI 安全配置（I04）：敏感词 / 工具白名单 / 限流 / 单用户配额 / 内容保留天数
--    campus_id=0 为平台级默认；校区可覆盖。0 表示不限制（限流/配额）。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_security_config (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    sensitive_words TEXT NULL,
    tool_whitelist TEXT NULL,
    rate_limit_per_minute INT NOT NULL DEFAULT 30,
    daily_quota_per_user INT NOT NULL DEFAULT 200,
    content_retention_days INT NOT NULL DEFAULT 90,
    retention_enabled TINYINT NOT NULL DEFAULT 0,
    block_on_hit TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_security_campus UNIQUE (campus_id)
);

-- 平台级默认策略：启用但敏感词为空、限流 30 次/分、单用户 200 次/日，
-- 与既有 AiRuntimeConfig 的默认行为兼容（不改变现有调用结果）。
INSERT INTO ai_security_config (id, campus_id, enabled, sensitive_words, tool_whitelist,
                                rate_limit_per_minute, daily_quota_per_user,
                                content_retention_days, retention_enabled, block_on_hit, remark,
                                created_at, updated_at, created_by, updated_by, deleted, version)
VALUES (7301, 0, 1, '', 'AI_SEARCH_CAMPUS,CALENDAR_QUERY,SERVICE_GUIDE,REPAIR_CREATE',
        30, 200, 90, 0, 1, '平台默认安全策略（迁移基线）',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

-- ---------------------------------------------------------------------------
-- 6) 内容运营推荐位（A03/C09）：BANNER / POPUP / RECOMMEND / TOPIC / ACTIVITY
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS content_slot (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    slot_code VARCHAR(64) NOT NULL,
    slot_name VARCHAR(100) NOT NULL,
    slot_type VARCHAR(20) NOT NULL DEFAULT 'BANNER',
    position VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    subtitle VARCHAR(500) NULL,
    image_url VARCHAR(500) NULL,
    target_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
    target_value VARCHAR(200) NULL,
    weight INT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    start_at TIMESTAMP NULL,
    end_at TIMESTAMP NULL,
    remark VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_content_slot_code UNIQUE (campus_id, slot_code)
);

CREATE INDEX idx_content_slot_position ON content_slot (campus_id, position, status, start_at, end_at);

-- ---------------------------------------------------------------------------
-- 7) 内容日历（A03/C09）：活动 / 专题 / 推荐 / 促销排期
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS content_calendar (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    slot_id BIGINT NULL,
    entry_type VARCHAR(20) NOT NULL DEFAULT 'ACTIVITY',
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(1000) NULL,
    cover_url VARCHAR(500) NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    owner VARCHAR(64) NULL,
    remark VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_content_calendar_range ON content_calendar (campus_id, start_at, end_at, status);

-- ---------------------------------------------------------------------------
-- 8) 创作者档案（C10）：认证 / 等级 / 状态（NORMAL/MUTED/BANNED）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS creator_profile (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    display_name VARCHAR(100) NULL,
    certification_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
    certification_note VARCHAR(255) NULL,
    level INT NOT NULL DEFAULT 1,
    score INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    muted_until TIMESTAMP NULL,
    banned_reason VARCHAR(255) NULL,
    last_active_at TIMESTAMP NULL,
    remark VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_creator_profile_user UNIQUE (user_id)
);

CREATE INDEX idx_creator_profile_status ON creator_profile (campus_id, status, certification_status);

-- ---------------------------------------------------------------------------
-- 9) 创作者处罚与申诉（C10）
--    penalty_type: WARN / MUTE / BAN；appeal_status: NONE / PENDING / APPROVED / REJECTED
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS creator_penalty (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    creator_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    penalty_type VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    evidence_url VARCHAR(500) NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    appeal_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
    appeal_reason VARCHAR(500) NULL,
    appeal_at TIMESTAMP NULL,
    appeal_result VARCHAR(500) NULL,
    handled_by BIGINT NULL,
    handled_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_creator_penalty_creator ON creator_penalty (creator_id, status);
CREATE INDEX idx_creator_penalty_user ON creator_penalty (user_id, status);
CREATE INDEX idx_creator_penalty_appeal ON creator_penalty (campus_id, appeal_status);

-- ---------------------------------------------------------------------------
-- 10) 权限点（默认仅平台管理员；运营人员可在角色权限页按需授予）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_permission
    (id, permission_code, permission_name, permission_type, status, created_at, updated_at, created_by, updated_by,
     deleted, version)
VALUES
    (40, 'push:manage', '推送模板与任务管理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (41, 'ai:security:manage', 'AI 提示词与安全策略管理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (42, 'content:operate', '内容运营位与内容日历管理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (43, 'creator:manage', '创作者认证与处罚管理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, created_at, created_by)
VALUES
    (1910, 1, 40, CURRENT_TIMESTAMP, 0),
    (1911, 1, 41, CURRENT_TIMESTAMP, 0),
    (1912, 1, 42, CURRENT_TIMESTAMP, 0),
    (1913, 1, 43, CURRENT_TIMESTAMP, 0);
