CREATE TABLE community_section (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    section_code VARCHAR(32) NOT NULL,
    section_name VARCHAR(64) NOT NULL,
    icon VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    banner_title VARCHAR(128),
    banner_subtitle VARCHAR(255),
    banner_image VARCHAR(255),
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_community_section_code UNIQUE (campus_id, section_code)
);

CREATE TABLE community_post (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    title VARCHAR(80) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    image_file_ids TEXT,
    topics VARCHAR(512),
    comment_count INT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_community_post_section FOREIGN KEY (section_id) REFERENCES community_section (id),
    CONSTRAINT fk_community_post_author FOREIGN KEY (author_id) REFERENCES sys_user (id)
);
CREATE INDEX idx_community_post_feed ON community_post (campus_id, section_id, status, created_at);

CREATE TABLE community_comment (
    id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    like_count INT NOT NULL DEFAULT 0,
    pinned TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_community_comment_post FOREIGN KEY (post_id) REFERENCES community_post (id),
    CONSTRAINT fk_community_comment_author FOREIGN KEY (author_id) REFERENCES sys_user (id)
);
CREATE INDEX idx_community_comment_post ON community_comment (post_id, status, created_at);

CREATE TABLE community_topic (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    name VARCHAR(32) NOT NULL,
    heat INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_community_topic_name UNIQUE (campus_id, name)
);

CREATE TABLE community_post_like (
    id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_community_post_like UNIQUE (post_id, user_id),
    CONSTRAINT fk_community_post_like_post FOREIGN KEY (post_id) REFERENCES community_post (id),
    CONSTRAINT fk_community_post_like_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

CREATE TABLE community_comment_like (
    id BIGINT NOT NULL,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_community_comment_like UNIQUE (comment_id, user_id),
    CONSTRAINT fk_community_comment_like_comment FOREIGN KEY (comment_id) REFERENCES community_comment (id),
    CONSTRAINT fk_community_comment_like_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

INSERT INTO community_section
    (id, campus_id, section_code, section_name, icon, description, banner_title, banner_subtitle, banner_image, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (6001, 1, 'LOST_FOUND', '失物招领', 'cat-lost-found', '捡到或丢失物品，可以在这里互相寻找', '校园里互相帮忙找回遗失物品', '描述物品特征、时间和地点，更容易被找到', '/assets/community/community-banner.jpg', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6002, 1, 'SECOND_HAND', '二手交易', 'cat-second-hand', '闲置物品交换与转让', '让闲置物品在校园里继续发挥价值', '发布清晰图片和价格，友好交流', '/assets/community/community-banner.jpg', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6003, 1, 'STUDY', '学习咨询', 'cat-study', '课程、考试与学习经验交流', '一起讨论课程、考试和学习方法', '把问题说清楚，大家一起想办法', '/assets/community/community-banner.jpg', 30, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6004, 1, 'ALUMNI_CHAT', '校友杂谈', 'cat-alumni', '校园生活与校友话题分享', '分享校园里的新鲜事和小故事', '保持友善，分享真实的校园生活', '/assets/community/community-banner.jpg', 40, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT INTO community_topic (id, campus_id, name, heat, status, created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (7001, 1, '校园生活', 0, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7002, 1, '期末复习', 0, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7003, 1, '宿舍日常', 0, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7004, 1, '闲置转让', 0, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7005, 1, '失物招领', 0, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7006, 1, '选课经验', 0, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7007, 1, '校园美食', 0, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7008, 1, '社团活动', 0, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);
