-- C12 官方号运营 + C13 投放计划/素材库（V75，admin-content）
-- 兼容 MySQL 8.0 与 H2 2.x（MODE=MySQL）：逐列 ALTER、新列可空或带默认值；只做追加，不改已有迁移。

-- ---------------------------------------------------------------------------
-- 1) C12：帖子置顶
--    注意：V23 的 pinned 列属于 community_comment（V23 第 47-53 行），community_post 没有 pinned；
--    MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，为避免「列已存在/列不存在」两种库状态导致迁移失败，
--    这里用新列名 feed_pinned 承载帖子置顶状态（与历史不存在的 pinned 不冲突），
--    并以 pinned_at/pinned_by 记录置顶时间与操作人。
-- ---------------------------------------------------------------------------
ALTER TABLE community_post ADD COLUMN feed_pinned TINYINT NOT NULL DEFAULT 0;
ALTER TABLE community_post ADD COLUMN pinned_at DATETIME NULL;
ALTER TABLE community_post ADD COLUMN pinned_by BIGINT NULL;
CREATE INDEX idx_community_post_feed_pinned ON community_post (campus_id, status, feed_pinned, created_at);

-- ---------------------------------------------------------------------------
-- 2) C13：投放计划（同一广告主/同一排期下可挂多条直投广告）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS community_ad_plan (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    advertiser_id BIGINT NULL,
    plan_name VARCHAR(100) NOT NULL,
    objective VARCHAR(32) NOT NULL DEFAULT 'AWARENESS',
    slot VARCHAR(16) NOT NULL DEFAULT 'FEED',
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    budget_cents BIGINT NULL,
    daily_budget_cents BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_community_ad_plan_scope ON community_ad_plan (campus_id, status, start_at, end_at);
CREATE INDEX idx_community_ad_plan_advertiser ON community_ad_plan (advertiser_id);

-- ---------------------------------------------------------------------------
-- 3) C13：素材库（图片/视频/文本，独立审核；直投广告可引用已通过素材）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS community_ad_material (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    material_name VARCHAR(100) NOT NULL,
    material_type VARCHAR(16) NOT NULL DEFAULT 'IMAGE',
    file_url VARCHAR(512) NULL,
    cover_url VARCHAR(512) NULL,
    title VARCHAR(100) NULL,
    description VARCHAR(500) NULL,
    width INT NULL,
    height INT NULL,
    size_bytes BIGINT NULL,
    review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    review_reason VARCHAR(255) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_community_ad_material_scope ON community_ad_material (campus_id, review_status, status);
CREATE INDEX idx_community_ad_material_type ON community_ad_material (material_type);

-- ---------------------------------------------------------------------------
-- 4) C13：直投广告最小侵入关联计划/素材（nullable，历史广告不受影响）
-- ---------------------------------------------------------------------------
ALTER TABLE community_direct_ad ADD COLUMN plan_id BIGINT NULL;
ALTER TABLE community_direct_ad ADD COLUMN material_id BIGINT NULL;
CREATE INDEX idx_community_direct_ad_plan ON community_direct_ad (plan_id);
CREATE INDEX idx_community_direct_ad_material ON community_direct_ad (material_id);
