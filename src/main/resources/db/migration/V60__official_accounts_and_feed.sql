-- 官方账号体系 + 官方通知抓取
-- 1) 用户官方标识：official=1 时客户端以「官方」标识替代 Lv.N 展示
ALTER TABLE sys_user ADD COLUMN official TINYINT NOT NULL DEFAULT 0;
ALTER TABLE sys_user ADD COLUMN official_title VARCHAR(64) NULL;

-- 2) 官方通知分区（学校官网通知统一落到该分区）
INSERT INTO community_section
    (id, campus_id, section_code, section_name, icon, description, banner_title, banner_subtitle, banner_image, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (6005, 1, 'OFFICIAL_NOTICE', '校园通知', 'cat-official', '学校各部门官方通知公告同步',
     '学校官方发布', '通知公告自动同步，内容以学校官网原文为准',
     '/assets/community/community-banner.jpg', 5, 'ENABLED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

-- 3) 学校官方账号（不设密码，仅作为社区发布主体；不参与登录）
INSERT INTO sys_user
    (id, campus_id, default_organization_id, username, password_hash, display_name, phone, email, student_no,
     avatar_url, college, gender, status, wechat_open_id, qq_open_id, qq_union_id, identity_type,
     authorization_status, last_login_at, created_at, updated_at, created_by, updated_by, deleted, version,
     official, official_title)
VALUES
    (990000000000000001, 1, NULL, 'official_school', NULL, '绵阳城市学院', NULL, NULL, NULL,
     NULL, NULL, NULL, 'ENABLED', NULL, NULL, NULL, 'ADMIN',
     'AUTHORIZED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0, 1, '学校官方'),
    (990000000000000002, 1, NULL, 'official_jwc', NULL, '教务处', NULL, NULL, NULL,
     NULL, NULL, NULL, 'ENABLED', NULL, NULL, NULL, 'ADMIN',
     'AUTHORIZED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0, 1, '教务处'),
    (990000000000000003, 1, NULL, 'official_xsc', NULL, '学生工作处', NULL, NULL, NULL,
     NULL, NULL, NULL, 'ENABLED', NULL, NULL, NULL, 'ADMIN',
     'AUTHORIZED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0, 1, '学生工作处'),
    (990000000000000004, 1, NULL, 'official_hqc', NULL, '后勤保障处', NULL, NULL, NULL,
     NULL, NULL, NULL, 'ENABLED', NULL, NULL, NULL, 'ADMIN',
     'AUTHORIZED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0, 1, '后勤保障处'),
    (990000000000000005, 1, NULL, 'official_lib', NULL, '图书馆', NULL, NULL, NULL,
     NULL, NULL, NULL, 'ENABLED', NULL, NULL, NULL, 'ADMIN',
     'AUTHORIZED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0, 1, '图书馆'),
    (990000000000000006, 1, NULL, 'official_tw', NULL, '校团委', NULL, NULL, NULL,
     NULL, NULL, NULL, 'ENABLED', NULL, NULL, NULL, 'ADMIN',
     'AUTHORIZED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0, 1, '校团委');

-- 4) 抓取来源（页面列表地址 + 发布分区 + 发布主体）
CREATE TABLE official_feed_source (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    source_name VARCHAR(64) NOT NULL,
    list_url VARCHAR(512) NOT NULL,
    section_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    max_pages INT NOT NULL DEFAULT 1,
    max_articles INT NOT NULL DEFAULT 3,
    max_age_days INT NOT NULL DEFAULT 120,
    enabled TINYINT NOT NULL DEFAULT 1,
    last_fetch_at TIMESTAMP,
    last_status VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_official_feed_source_campus ON official_feed_source (campus_id);

-- 5) 抓取文章去重与发布记录
CREATE TABLE official_feed_article (
    id BIGINT NOT NULL,
    source_id BIGINT NOT NULL,
    source_url VARCHAR(512) NOT NULL,
    title VARCHAR(255) NOT NULL,
    published_at TIMESTAMP,
    content_hash VARCHAR(64) NOT NULL,
    post_id BIGINT,
    post_type VARCHAR(16),
    image_count INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(512),
    fetched_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_official_feed_article_url ON official_feed_article (source_url);
CREATE INDEX idx_official_feed_article_source ON official_feed_article (source_id);
CREATE INDEX idx_official_feed_article_hash ON official_feed_article (content_hash);

INSERT INTO official_feed_source
    (id, campus_id, source_name, list_url, section_id, author_id, max_pages, max_articles, max_age_days, enabled,
     last_fetch_at, last_status, created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (8001, 1, '通知公告', 'https://www.mycc.edu.cn/index/tzgg.htm', 6005, 990000000000000001,
     1, 3, 120, 1, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (8002, 1, '奖助政策', 'https://www.mycc.edu.cn/xxgong_ka/jzzc.htm', 6005, 990000000000000003,
     1, 3, 120, 1, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (8003, 1, '教学管理', 'https://www.mycc.edu.cn/kjdh/jxgl1.htm', 6005, 990000000000000002,
     1, 3, 120, 1, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);
