-- 社区帖子双类型：DYNAMIC 动态（短文本+图集）/ ARTICLE 文章（图文混排）。
-- 文章正文以 content_blocks JSON 存储（[{type:TEXT|IMAGE,text,fileId}]），
-- content 列保留纯文本摘要（≤2000，评论/列表兼容与搜索用），image_file_ids 保留文章内图片的扁平列表。
ALTER TABLE community_post ADD COLUMN post_type VARCHAR(16) NOT NULL DEFAULT 'DYNAMIC';
ALTER TABLE community_post ADD COLUMN content_blocks TEXT NULL;
