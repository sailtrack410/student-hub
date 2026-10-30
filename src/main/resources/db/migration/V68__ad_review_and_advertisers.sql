-- 社区商业化闭环：广告主管理 + 官方直投广告审核流。
-- 说明：
-- 1) 本迁移只做追加，不改动 V67 及更早的迁移；
-- 2) 语法同时兼容 MySQL 8.0 与 H2 2.x（MODE=MySQL）：逐列 ALTER、不使用多列 ADD COLUMN；
-- 3) 兼容既有广告数据：V66/V67 期间已创建或已上架的广告回填 review_status=APPROVED，
--    只有 status=DRAFT 的草稿回填为 DRAFT，保证既有数据不会从信息流消失。

-- ---------------------------------------------------------------------------
-- 1) 广告主
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS community_advertiser (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    contact_name VARCHAR(50) NULL,
    contact_phone VARCHAR(30) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_community_advertiser_campus ON community_advertiser (campus_id, status);

-- ---------------------------------------------------------------------------
-- 2) 广告审核字段（逐列追加，DEFAULT 'APPROVED' 保证旧广告继续投放）
-- ---------------------------------------------------------------------------
ALTER TABLE community_direct_ad ADD COLUMN advertiser_id BIGINT NULL;
ALTER TABLE community_direct_ad ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE community_direct_ad ADD COLUMN review_reason VARCHAR(200) NULL;
ALTER TABLE community_direct_ad ADD COLUMN submitted_at DATETIME NULL;
ALTER TABLE community_direct_ad ADD COLUMN reviewed_by BIGINT NULL;
ALTER TABLE community_direct_ad ADD COLUMN reviewed_at DATETIME NULL;

CREATE INDEX idx_community_direct_ad_review ON community_direct_ad (campus_id, review_status);

-- 旧数据回填：ENABLED/DISABLED 保持 APPROVED（列默认值已满足）；草稿回填 DRAFT。
UPDATE community_direct_ad SET review_status = 'DRAFT' WHERE status = 'DRAFT';

-- ---------------------------------------------------------------------------
-- 3) 权限点：广告审核、广告主管理（仅平台管理员默认拥有，运营人员可在管理端按需授权）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_permission
    (id, permission_code, permission_name, permission_type, status, created_at, updated_at, created_by, updated_by,
     deleted, version)
VALUES
    (32, 'community:ad:review', '官方直投广告审核', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (33, 'community:advertiser:manage', '社区广告主管理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, created_at, created_by)
VALUES
    (1803, 1, 32, CURRENT_TIMESTAMP, 0),
    (1804, 1, 33, CURRENT_TIMESTAMP, 0);
