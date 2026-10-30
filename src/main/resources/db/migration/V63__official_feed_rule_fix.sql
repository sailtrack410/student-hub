-- 官方通知规则修正：
-- 1) 政策/收费类栏目不受时效限制（max_age_days=0 表示不限制），避免官方号长期无帖；
-- 2) 收费公示改由财务处发布，补一个官方身份；
-- 3) 清掉旧规则下被跳过的记录，让下次同步按新规则重新评估。
INSERT INTO sys_user
    (id, campus_id, default_organization_id, username, password_hash, display_name, phone, email, student_no,
     avatar_url, college, gender, status, wechat_open_id, qq_open_id, qq_union_id, identity_type,
     authorization_status, last_login_at, created_at, updated_at, created_by, updated_by, deleted, version,
     official, official_title)
VALUES
    (990000000000000007, 1, NULL, 'official_cwc', NULL, '财务处', NULL, NULL, NULL,
     NULL, NULL, NULL, 'ENABLED', NULL, NULL, NULL, 'ADMIN',
     'AUTHORIZED', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0, 1, '财务处');

UPDATE official_feed_source
SET author_id = 990000000000000007, max_age_days = 0
WHERE id = 8003;

UPDATE official_feed_source
SET max_age_days = 0
WHERE id = 8002;

DELETE FROM official_feed_article
WHERE source_id IN (8002, 8003) AND status = 'SKIPPED';

UPDATE official_feed_source
SET last_status = NULL
WHERE id IN (8002, 8003);
