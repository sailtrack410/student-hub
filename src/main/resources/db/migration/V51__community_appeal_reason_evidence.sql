-- 内容申诉补充：复核原因编码与证明材料文件 ID（JSON 数组）
-- H2 兼容：逐列单子句 ALTER，不使用 MySQL 专属 AFTER 多子句写法（2026-10-19 测试修复）
ALTER TABLE community_appeal ADD COLUMN reason_code VARCHAR(32) NULL;
ALTER TABLE community_appeal ADD COLUMN evidence_file_ids VARCHAR(2048) NULL;
