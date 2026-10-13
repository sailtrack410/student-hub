-- 去掉 AFTER 子句：MySQL 方言，H2（集成测试库）解析失败
ALTER TABLE community_comment ADD COLUMN parent_comment_id BIGINT NULL;
ALTER TABLE community_comment ADD COLUMN image_file_ids TEXT NULL;
CREATE INDEX idx_community_comment_parent ON community_comment (post_id, parent_comment_id, created_at);
