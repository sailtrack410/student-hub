-- 私信关联场景卡片（API需求清单 二-6）：发私信携带来源上下文，会话流顶部回显卡片。
-- reference_type 目前仅 POST；卡片快照落库（标题/缩略图），源帖删除后卡片仍可展示。
ALTER TABLE community_direct_message ADD COLUMN reference_type VARCHAR(32) NULL COMMENT '关联业务类型：POST';
ALTER TABLE community_direct_message ADD COLUMN reference_id BIGINT NULL COMMENT '关联业务 ID';
ALTER TABLE community_direct_message ADD COLUMN reference_card TEXT NULL COMMENT '卡片快照 JSON：{type,id,title,thumbnail}';
