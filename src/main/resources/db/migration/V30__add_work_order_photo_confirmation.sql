-- 拆成单子句 ALTER、UPDATE JOIN 改相关子查询：多子句 / UPDATE JOIN / AFTER 均为 MySQL 方言，
-- H2（集成测试库）解析失败
ALTER TABLE work_order ADD COLUMN photo_workflow_required TINYINT NOT NULL DEFAULT 0;
ALTER TABLE work_order ADD COLUMN arrival_photo_file_id BIGINT NULL;
ALTER TABLE work_order ADD COLUMN completion_photo_file_id BIGINT NULL;
ALTER TABLE work_order ADD COLUMN completion_submitted_at TIMESTAMP NULL;
ALTER TABLE work_order ADD COLUMN confirmed_at TIMESTAMP NULL;
ALTER TABLE work_order ADD COLUMN confirmation_reject_reason VARCHAR(512) NULL;

UPDATE work_order
SET photo_workflow_required = 1
WHERE deleted = 0
  AND item_id IN (SELECT id FROM service_item WHERE item_code LIKE 'REPAIR-%' AND deleted = 0);

UPDATE service_item
SET form_schema = '[{"key":"contactPhone","label":"联系方式","type":"input","required":true,"max":32,"placeholder":"请输入手机号"},{"key":"address","label":"报修地址","type":"input","required":true,"max":160,"placeholder":"如：XX校区X栋X室"},{"key":"faultType","label":"故障类型","type":"select","required":true,"options":["水电维修","家具维修","门窗维修","网络故障","其他"]},{"key":"description","label":"问题描述","type":"textarea","required":true,"max":500,"placeholder":"请描述故障现象、发生时间等"},{"key":"expectDate","label":"期望上门日期","type":"date","required":false},{"key":"photos","label":"故障照片","type":"image","required":false,"maxCount":3}]'
WHERE item_code = 'REPAIR-DORM' AND deleted = 0;

UPDATE service_item
SET form_schema = '[{"key":"contactPhone","label":"联系方式","type":"input","required":true,"max":32,"placeholder":"请输入手机号"},{"key":"address","label":"报修地址","type":"input","required":true,"max":160,"placeholder":"如：教学楼A栋302教室"},{"key":"faultType","label":"故障类型","type":"select","required":true,"options":["桌椅损坏","多媒体设备","照明电路","门窗损坏","其他"]},{"key":"description","label":"问题描述","type":"textarea","required":true,"max":500},{"key":"photos","label":"故障照片","type":"image","required":false,"maxCount":3}]'
WHERE item_code = 'REPAIR-PUBLIC' AND deleted = 0;
