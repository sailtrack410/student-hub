-- 帖子分享短链码（D-Share）：8 位混淆安全字符集
-- [23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ]（去 0O1IiL），应用层生成。
-- 唯一索引兜底碰撞（~20 亿组合空间，校园体量基本永不碰撞）。
-- 存量帖子不在此回填：由应用层在发帖/详情访问/分享时懒生成（slug 为空则生成回写），
-- 避免在 SQL 里做 base54 编码这种扭曲实现。
ALTER TABLE community_post ADD COLUMN share_slug VARCHAR(12) NULL;
CREATE UNIQUE INDEX uk_community_post_share_slug ON community_post (share_slug);
