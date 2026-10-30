-- 私信送达回执
-- H2 兼容：去掉 MySQL 专属 AFTER
ALTER TABLE community_direct_message ADD COLUMN delivered_at TIMESTAMP NULL;
