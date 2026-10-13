-- 每个学期记录“第一周周一日期”与“总周数”，作为课表日期定位的唯一事实源。
-- 课表页表头、AI 按日期查询共用，避免前后端各自推算导致结果不一致。
-- 拆成单子句 ALTER：MySQL 的多子句写法在 H2（集成测试库）中解析失败
ALTER TABLE schedule_semester ADD COLUMN first_monday DATE NULL DEFAULT NULL COMMENT '该学期第一周的周一日期';
ALTER TABLE schedule_semester ADD COLUMN total_weeks INT NULL DEFAULT NULL COMMENT '该学期总周数';
