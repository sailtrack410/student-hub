-- 记录每个用户最后一次同步课表所属的学期。
-- 新学期课表未排（0 门课）时视图仍需返回学期信息，前端据此显示假期空表格。
-- 拆成单子句 ALTER：MySQL 的多子句写法在 H2（集成测试库）中解析失败
ALTER TABLE jw_binding ADD COLUMN last_semester_code VARCHAR(16) NULL DEFAULT NULL COMMENT '最后一次同步课表的学期编码(学年,学期)';
ALTER TABLE jw_binding ADD COLUMN last_semester_name VARCHAR(64) NULL DEFAULT NULL COMMENT '最后一次同步课表的学期名称';
