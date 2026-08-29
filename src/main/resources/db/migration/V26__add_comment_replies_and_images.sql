ALTER TABLE community_comment ADD COLUMN parent_comment_id BIGINT NULL AFTER author_id;
ALTER TABLE community_comment ADD COLUMN image_file_ids TEXT NULL AFTER content;
CREATE INDEX idx_community_comment_parent ON community_comment (post_id, parent_comment_id, created_at);
