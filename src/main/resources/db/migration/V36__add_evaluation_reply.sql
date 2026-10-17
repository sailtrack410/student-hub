-- 管理端服务评价回复：商家回复内容与回复时间
-- 拆成单子句 ALTER 并去掉 AFTER 子句：多子句与 AFTER 均为 MySQL 方言，H2（集成测试库）解析失败
ALTER TABLE service_evaluation ADD COLUMN reply VARCHAR(512) NULL COMMENT '商家回复';
ALTER TABLE service_evaluation ADD COLUMN replied_at DATETIME NULL COMMENT '回复时间';
