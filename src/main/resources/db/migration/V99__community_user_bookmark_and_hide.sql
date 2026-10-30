-- 用户行为服务端化（P1）：收藏与隐藏此前仅存 App 本机 SharedPreferences，
-- 换设备/重装即丢失。本迁移落两张行为表，客户端登录后合并本机存量。
-- 语义与 community_post_like 一致：唯一键防重，取消 = 删行；读路径批量回显。
CREATE TABLE community_user_bookmark (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    campus_id BIGINT NOT NULL COMMENT '校区（跨校隔离）',
    user_id BIGINT NOT NULL COMMENT '收藏人',
    post_id BIGINT NOT NULL COMMENT '帖子',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_community_user_bookmark (user_id, post_id),
    KEY idx_community_user_bookmark_post (post_id)
) COMMENT '用户帖子收藏（服务端权威，客户端本地仅写穿透缓存）';

CREATE TABLE community_user_hide (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    campus_id BIGINT NOT NULL COMMENT '校区（跨校隔离）',
    user_id BIGINT NOT NULL COMMENT '隐藏发起人',
    post_id BIGINT NOT NULL COMMENT '帖子',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_community_user_hide (user_id, post_id),
    KEY idx_community_user_hide_post (post_id)
) COMMENT '用户帖子隐藏（不感兴趣，仅过滤信息流，不影响分享/主页/搜索进入）';
