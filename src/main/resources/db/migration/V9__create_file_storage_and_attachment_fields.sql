CREATE TABLE file_storage (
    id BIGINT NOT NULL,
    storage_key VARCHAR(128) NOT NULL,
    original_name VARCHAR(256) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_file_storage_key UNIQUE (storage_key)
);

-- 请假申请：新增选填的请假材料上传
UPDATE service_item
   SET form_schema = '[{"key":"leaveType","label":"请假类型","type":"select","required":true,"options":["病假","事假","其他"]},{"key":"startDate","label":"开始日期","type":"date","required":true},{"key":"endDate","label":"结束日期","type":"date","required":true},{"key":"reason","label":"请假事由","type":"textarea","required":true,"max":500},{"key":"materials","label":"请假材料","type":"image","required":false,"maxCount":3,"placeholder":"病假证明等，选填"}]'
 WHERE item_code = 'AFFAIR-LEAVE'
   AND deleted = 0;

-- 教室与公共区域报修：新增选填的现场照片
UPDATE service_item
   SET form_schema = '[{"key":"location","label":"具体位置","type":"input","required":true,"max":100,"placeholder":"如：教学楼A栋302教室"},{"key":"faultType","label":"故障类型","type":"select","required":true,"options":["桌椅损坏","多媒体设备","照明电路","门窗损坏","其他"]},{"key":"description","label":"问题描述","type":"textarea","required":true,"max":500},{"key":"photos","label":"现场照片","type":"image","required":false,"maxCount":3}]'
 WHERE item_code = 'REPAIR-PUBLIC'
   AND deleted = 0;
