-- 社区内容治理：举报（含举报时刻的内容快照）与用户发言限制
--
-- 内容状态语义（community_post.status / community_comment.status）：
--   ENABLED  正常展示
--   HIDDEN   举报成立后被治理下架，内容保留在库中便于追溯，可由治理端恢复
--   DELETED  作者自行删除或治理删除（配合 deleted 逻辑删除字段）
--   PENDING  预留给人工送审场景，当前链路不产生该值
--
-- 处罚有效期用 end_at 判定（NULL 表示长期有效），查询时按 end_at > NOW() 过滤，
-- 因此无需定时任务把到期记录刷成 EXPIRED；EXPIRED/REVOKED 仅用于人工提前终止。

CREATE TABLE community_report (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    target_type VARCHAR(16) NOT NULL COMMENT '举报对象：POST/COMMENT/MESSAGE/USER',
    target_id BIGINT NOT NULL COMMENT '举报对象 ID',
    target_author_id BIGINT NULL COMMENT '被举报内容作者，便于按作者聚合与处罚',
    reason_code VARCHAR(32) NOT NULL COMMENT '举报原因：AD/ABUSE/PORN/POLITICS/PRIVACY/OTHER',
    reason_detail VARCHAR(500) NULL COMMENT '举报人补充说明',
    snapshot TEXT NULL COMMENT '举报时刻的内容快照 JSON，防止作者删改后无法追溯',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED',
    handled_by BIGINT NULL COMMENT '处理人（管理端用户 ID），审计留痕',
    handled_at TIMESTAMP NULL COMMENT '处理时间',
    handle_note VARCHAR(500) NULL COMMENT '处理说明，写入通知并作为处置依据',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_community_report_reporter FOREIGN KEY (reporter_id) REFERENCES sys_user (id)
);
CREATE INDEX idx_community_report_queue ON community_report (campus_id, status, created_at);
CREATE INDEX idx_community_report_target ON community_report (target_type, target_id);
CREATE INDEX idx_community_report_reporter ON community_report (reporter_id, created_at);

CREATE TABLE community_user_sanction (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    sanction_type VARCHAR(16) NOT NULL COMMENT 'MUTE 限制发言 / BAN 禁止访问社区',
    reason VARCHAR(500) NULL COMMENT '处罚依据，通常引用来源举报',
    report_id BIGINT NULL COMMENT '触发处罚的举报 ID，便于追溯',
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NULL COMMENT '到期时间，NULL 表示长期有效',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED/EXPIRED',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_community_sanction_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);
CREATE INDEX idx_community_sanction_user ON community_user_sanction (user_id, status, end_at);

-- 社区治理权限点：默认只绑定平台管理员，校方运营人员由管理端按需授权
INSERT IGNORE INTO sys_permission
    (id, permission_code, permission_name, permission_type, status, created_at, updated_at, created_by, updated_by,
     deleted, version)
VALUES
    (24, 'community:moderate', '社区内容治理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, created_at, created_by)
VALUES
    (1501, 1, 24, CURRENT_TIMESTAMP, 0);
