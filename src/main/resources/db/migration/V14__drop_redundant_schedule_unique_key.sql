-- 修复 V12 遗留问题：唯一键含 course_code（现阶段恒为 NULL，形同虚设），
-- 且补全课程维度后唯一键会超过 innodb 单索引长度上限，故移除该冗余约束，
-- 去重由应用层（同步 diff 前先 distinct）保证。
-- DROP INDEX 是 MySQL 方言，H2（集成测试库）用 DROP CONSTRAINT
ALTER TABLE schedule_class_course DROP CONSTRAINT uk_schedule_class_course;