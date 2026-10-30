-- 管理端社区/公告/成就/审计深度能力（V71，admin-content）
-- 兼容 MySQL 8.0 与 H2 2.x（MODE=MySQL）：逐列 ALTER、不使用多列 ADD COLUMN；新列可空或带默认值。
-- 本迁移只做追加，不修改 V1-V69 已应用内容。

-- ---------------------------------------------------------------------------
-- 1) 举报 / 申诉处理时限（SLA）
--    due_at 为空时业务侧按 created_at + 24h 计算，历史数据无需回填。
-- ---------------------------------------------------------------------------
ALTER TABLE community_report ADD COLUMN due_at DATETIME NULL;
ALTER TABLE community_appeal ADD COLUMN due_at DATETIME NULL;
CREATE INDEX idx_community_report_due ON community_report (status, due_at);
CREATE INDEX idx_community_appeal_due ON community_appeal (status, due_at);

-- ---------------------------------------------------------------------------
-- 2) 处罚 / 理由模板（C03 处罚模板、C04 理由模板）
--    campus_id = 0 为平台内置模板（只读参考，所有校区可见）；>0 为校区自定义模板。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS community_moderation_template (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL DEFAULT 0,
    template_type VARCHAR(32) NOT NULL,
    template_name VARCHAR(64) NOT NULL,
    reason_code VARCHAR(32) NULL,
    content VARCHAR(1000) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_community_moderation_template_scope
    ON community_moderation_template (campus_id, template_type, status, sort_order);

-- 内置模板（campus_id = 0）：管理端可直接引用，不允许在页面修改/删除
INSERT IGNORE INTO community_moderation_template
    (id, campus_id, template_type, template_name, reason_code, content, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (7101, 0, 'REPORT_NOTE', '广告营销·删除并说明', 'AD',
     '内容含未经许可的商业推广，已按社区规范处理。如需申诉，请在 7 日内提交申诉并附证明材料。', 10, 'ENABLED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7102, 0, 'REPORT_NOTE', '辱骂攻击·下架并提醒', 'ABUSE',
     '内容含人身攻击或侮辱性表述，已下架处理。请文明发言，重复违规将限制社区使用权限。', 20, 'ENABLED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7103, 0, 'REPORT_NOTE', '泄露隐私·删除并保护', 'PRIVACY',
     '内容涉及他人隐私信息，已删除以保护当事人。请勿在社区发布手机号、住址、证件号等信息。', 30, 'ENABLED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7104, 0, 'REPORT_NOTE', '证据不足·驳回举报', 'OTHER',
     '经核查现有材料不足以认定违规，本次举报不成立。如补充证据可重新提交。', 40, 'ENABLED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7105, 0, 'APPEAL_REPLY', '申诉通过·已恢复', NULL,
     '你的申诉已复核通过，相关内容/处罚已恢复。感谢你对社区规范的关注。', 10, 'ENABLED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7106, 0, 'APPEAL_REPLY', '申诉驳回·维持原处理', NULL,
     '经复核，原处理依据充分，维持原结论。如仍有疑问，可联系校园服务台进一步沟通。', 20, 'ENABLED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

-- ---------------------------------------------------------------------------
-- 3) 分区：推荐权重（拖拽排序复用 sort_order）
-- ---------------------------------------------------------------------------
ALTER TABLE community_section ADD COLUMN recommend_weight INT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------------
-- 4) 话题：推荐位 + 合并（合并后源话题标记 merged_into_id 并停用）
-- ---------------------------------------------------------------------------
ALTER TABLE community_topic ADD COLUMN recommended TINYINT NOT NULL DEFAULT 0;
ALTER TABLE community_topic ADD COLUMN merged_into_id BIGINT NULL;

-- ---------------------------------------------------------------------------
-- 5) 屏蔽词：字面量/正则两种匹配 + 命中统计
-- ---------------------------------------------------------------------------
ALTER TABLE community_banned_word ADD COLUMN match_type VARCHAR(16) NOT NULL DEFAULT 'LITERAL';
ALTER TABLE community_banned_word ADD COLUMN hit_count INT NOT NULL DEFAULT 0;
ALTER TABLE community_banned_word ADD COLUMN last_hit_at DATETIME NULL;

-- ---------------------------------------------------------------------------
-- 6) 公告（N01）：正文与封面
-- ---------------------------------------------------------------------------
ALTER TABLE screen_notice ADD COLUMN content TEXT NULL;
ALTER TABLE screen_notice ADD COLUMN cover_image_url VARCHAR(512) NULL;

-- ---------------------------------------------------------------------------
-- 7) 官方通知（C08）：单篇 URL 导入、预览、编辑、定时、失败重试、版权确认
-- ---------------------------------------------------------------------------
ALTER TABLE official_feed_article ADD COLUMN content_blocks TEXT NULL;
ALTER TABLE official_feed_article ADD COLUMN plain_text TEXT NULL;
ALTER TABLE official_feed_article ADD COLUMN cover_url VARCHAR(512) NULL;
ALTER TABLE official_feed_article ADD COLUMN import_mode VARCHAR(16) NOT NULL DEFAULT 'CRAWL';
ALTER TABLE official_feed_article ADD COLUMN copyright_confirmed TINYINT NOT NULL DEFAULT 0;
ALTER TABLE official_feed_article ADD COLUMN scheduled_at DATETIME NULL;
ALTER TABLE official_feed_article ADD COLUMN retry_count INT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------------
-- 8) 审计（SYS10）：失败写操作、错误信息、资源可读名
-- ---------------------------------------------------------------------------
ALTER TABLE sys_audit_log ADD COLUMN result_status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS';
ALTER TABLE sys_audit_log ADD COLUMN error_message VARCHAR(512) NULL;
ALTER TABLE sys_audit_log ADD COLUMN resource_name VARCHAR(255) NULL;
CREATE INDEX idx_audit_result ON sys_audit_log (result_status, created_at);

-- ---------------------------------------------------------------------------
-- 9) 成就（SYS09）：发放记录区分「自动达成」与「管理端手动发放」
-- ---------------------------------------------------------------------------
ALTER TABLE user_achievement_badge ADD COLUMN grant_source VARCHAR(16) NOT NULL DEFAULT 'AUTO';
CREATE INDEX idx_user_achievement_badge_source ON user_achievement_badge (grant_source, unlocked_at);
