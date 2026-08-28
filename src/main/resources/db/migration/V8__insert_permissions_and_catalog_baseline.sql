-- 新增业务权限与角色绑定（管理端目录管理、服务评价、AI 助手）

INSERT INTO sys_permission
    (id, permission_code, permission_name, permission_type, status, created_at, updated_at, created_by, updated_by,
     deleted, version)
VALUES
    (20, 'catalog:manage', '服务目录管理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (21, 'evaluation:create', '提交服务评价', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (22, 'ai:chat', '使用AI助手', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT INTO sys_role_permission (id, role_id, permission_id, created_at, created_by)
VALUES
    (510, 1, 20, CURRENT_TIMESTAMP, 0),
    (511, 1, 21, CURRENT_TIMESTAMP, 0),
    (512, 1, 22, CURRENT_TIMESTAMP, 0),
    (520, 2, 21, CURRENT_TIMESTAMP, 0),
    (521, 2, 22, CURRENT_TIMESTAMP, 0),
    (530, 3, 22, CURRENT_TIMESTAMP, 0),
    (540, 4, 22, CURRENT_TIMESTAMP, 0);

-- 基线服务目录：正式环境同样加载（业务配置型种子数据）

INSERT INTO service_category
    (id, campus_id, category_code, category_name, icon, description, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (100, 1, 'REPAIR', '报修服务', 'cat-repair', '宿舍、教室与公共区域设施报修', 10, 'ENABLED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (200, 1, 'AFFAIR', '事务办理', 'cat-affair', '请假、活动场地等日常事务申请', 20, 'ENABLED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (300, 1, 'CERTIFICATE', '证明开具', 'cat-certificate', '在读证明等常用证明申请', 30, 'ENABLED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (400, 1, 'GUIDE', '咨询与指引', 'cat-consult', '校园政策咨询与办事指引', 40, 'ENABLED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT INTO service_item
    (id, campus_id, category_id, item_code, item_name, summary, description, icon, form_schema,
     handler_organization_id, sla_hours, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (1001, 1, 100, 'REPAIR-DORM', '宿舍报修', '水电、家具、门窗等宿舍设施故障报修',
     '适用于宿舍内水电、家具、门窗、网络等设施的故障报修。请尽量描述故障情况并附上现场照片，维修人员将在承诺时限内上门处理。',
     'cat-repair',
     '[{"key":"faultType","label":"故障类型","type":"select","required":true,"options":["水电维修","家具维修","门窗维修","网络故障","其他"]},{"key":"description","label":"问题描述","type":"textarea","required":true,"max":500,"placeholder":"请描述故障现象、发生时间等"},{"key":"expectDate","label":"期望上门日期","type":"date","required":false},{"key":"photos","label":"现场照片","type":"image","required":false,"maxCount":3}]',
     12, 24, 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1002, 1, 100, 'REPAIR-PUBLIC', '教室与公共区域报修', '教学楼教室、自习区、走廊等公共设施报修',
     '适用于教室、自习区、走廊、公共卫生间等公共区域的设施故障报修。请填写具体位置，便于快速定位处理。',
     'cat-repair',
     '[{"key":"location","label":"具体位置","type":"input","required":true,"max":100,"placeholder":"如：教学楼A栋302教室"},{"key":"faultType","label":"故障类型","type":"select","required":true,"options":["桌椅损坏","多媒体设备","照明电路","门窗损坏","其他"]},{"key":"description","label":"问题描述","type":"textarea","required":true,"max":500}]',
     12, 24, 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (2001, 1, 200, 'AFFAIR-LEAVE', '请假申请', '病假、事假等学生请假申请',
     '适用于学生因病、因事需要请假的申请。请如实填写请假类型与时间范围，辅导员审批后将通过消息通知结果。',
     'cat-leave',
     '[{"key":"leaveType","label":"请假类型","type":"select","required":true,"options":["病假","事假","其他"]},{"key":"startDate","label":"开始日期","type":"date","required":true},{"key":"endDate","label":"结束日期","type":"date","required":true},{"key":"reason","label":"请假事由","type":"textarea","required":true,"max":500}]',
     10, 48, 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (2002, 1, 200, 'AFFAIR-VENUE', '活动场地申请', '社团活动、班级活动等场地使用申请',
     '适用于社团、班级或个人申请使用校内公共活动场地。请提前至少 3 个工作日提交申请。',
     'cat-activity',
     '[{"key":"activityName","label":"活动名称","type":"input","required":true,"max":100},{"key":"venue","label":"申请场地","type":"select","required":true,"options":["学生活动中心","室外广场","报告厅","会议室"]},{"key":"useDate","label":"使用日期","type":"date","required":true},{"key":"description","label":"活动说明","type":"textarea","required":true,"max":500}]',
     10, 48, 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (3001, 1, 300, 'CERT-ENROLL', '在读证明申请', '用于签证办理、贷款等场景的在读证明',
     '适用于需要学校出具在读证明的场景。证明将在承诺时限内开具电子版，可凭申请记录到指定地点领取纸质版。',
     'cat-certificate',
     '[{"key":"purpose","label":"用途","type":"select","required":true,"options":["签证办理","助学贷款","实习求职","其他"]},{"key":"copies","label":"份数","type":"number","required":true,"min":1,"max":5},{"key":"remark","label":"备注","type":"textarea","required":false,"max":200}]',
     10, 48, 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (4001, 1, 400, 'GUIDE-CONSULT', '校园事务咨询', '办事流程、政策规定等问题咨询',
     '适用于对办事流程、校园政策有疑问的咨询。也可以直接在 AI 助手中提问，获得即时解答。',
     'cat-consult',
     '[{"key":"questionType","label":"咨询类型","type":"select","required":true,"options":["办事流程","政策规定","系统使用","其他"]},{"key":"description","label":"问题描述","type":"textarea","required":true,"max":500}]',
     10, 24, 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);
