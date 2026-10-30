-- 社区运营增强（2026-10-18 定稿）：
-- 1) 屏蔽词库：命中自动隐藏内容并通知作者，作者可申诉；
-- 2) 申诉：内容（帖子/评论）与处罚均可申诉，人工复核；
-- 3) 评论举报：举报人对该评论不可见、权重下降；举报人数达 10 删除评论；
-- 4) 帖子：隐藏原因/时间与举报计数（帖子不做自动删除）。
CREATE TABLE community_banned_word (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    word VARCHAR(64) NOT NULL,
    remark VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_community_banned_word ON community_banned_word (campus_id, status);

CREATE TABLE community_appeal (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(16) NOT NULL COMMENT '申诉对象：POST/COMMENT/SANCTION',
    target_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    handled_by BIGINT NULL,
    handled_at TIMESTAMP NULL,
    handle_note VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_community_appeal_queue ON community_appeal (campus_id, status, created_at);
CREATE INDEX idx_community_appeal_user ON community_appeal (user_id, created_at);
CREATE INDEX idx_community_appeal_target ON community_appeal (target_type, target_id);

ALTER TABLE community_post ADD COLUMN hidden_reason VARCHAR(32) NULL;
ALTER TABLE community_post ADD COLUMN hidden_at TIMESTAMP NULL;
ALTER TABLE community_post ADD COLUMN report_count INT NOT NULL DEFAULT 0;

ALTER TABLE community_comment ADD COLUMN hidden_reason VARCHAR(32) NULL;
ALTER TABLE community_comment ADD COLUMN report_count INT NOT NULL DEFAULT 0;
ALTER TABLE community_comment ADD COLUMN weight INT NOT NULL DEFAULT 0;

-- 存量评论权重按点赞数初始化（新评论在点赞/举报时增量维护）
UPDATE community_comment SET weight = like_count;

CREATE INDEX idx_community_comment_weight ON community_comment (post_id, status, weight);
