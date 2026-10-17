-- 社交关系与隐私设置（API需求清单 2.2-2.4）：user_follow 关注关系 + user_privacy 隐私开关。
-- 关注关系唯一索引防重复关注；本人视角不落库（is_following 均按查询计算），互关由双向行判定。
CREATE TABLE user_follow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    campus_id BIGINT NOT NULL COMMENT '校区（跨校隔离）',
    follower_id BIGINT NOT NULL COMMENT '关注发起人',
    followee_id BIGINT NOT NULL COMMENT '被关注人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_follow_pair (follower_id, followee_id),
    KEY idx_user_follow_followee (followee_id)
) COMMENT '用户关注关系';

CREATE TABLE user_privacy (
    user_id BIGINT PRIMARY KEY COMMENT '用户 ID（一对一）',
    show_academic_info TINYINT NOT NULL DEFAULT 1 COMMENT '向他人公开年级与专业',
    show_medals TINYINT NOT NULL DEFAULT 1 COMMENT '公开我的勋章',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '用户隐私设置（默认全公开，缺行=全公开）';
