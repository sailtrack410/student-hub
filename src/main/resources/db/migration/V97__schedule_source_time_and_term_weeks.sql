-- 保存学校课表原始节次时间与学校发布的总周数，避免客户端硬编码作息。
ALTER TABLE schedule_course ADD COLUMN time_range VARCHAR(32) NULL COMMENT '教务原始节次时间';
ALTER TABLE schedule_class_course ADD COLUMN time_range VARCHAR(32) NULL COMMENT '教务原始节次时间';

-- 已导入学期若缺少学校总周数，使用保守的25周，禁止用课程最大周充当学期结束周。
UPDATE schedule_semester SET total_weeks = 25 WHERE total_weeks IS NULL OR total_weeks < 20;
