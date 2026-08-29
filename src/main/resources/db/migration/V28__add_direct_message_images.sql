-- V27 已在部分环境执行，单独补充图片字段时保持幂等结构。
ALTER TABLE community_direct_message MODIFY COLUMN content VARCHAR(2000) NULL;
ALTER TABLE community_direct_message ADD COLUMN image_file_ids TEXT NULL;
