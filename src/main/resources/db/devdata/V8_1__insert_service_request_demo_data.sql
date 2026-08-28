-- 开发环境演示数据：三条处于不同状态的申请，便于直接调试派单与处理流程
-- requester=1002 worker=1003 dispatcher=1004

INSERT INTO service_request
    (id, request_no, campus_id, item_id, item_name, category_id, requester_id, title, form_data, status,
     handler_organization_id, submitted_at, created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (3001, 'SR-DEV-3001', 1, 1001, '宿舍报修', 100, 1002, '宿舍空调不制冷',
     '{"faultType":"水电维修","description":"空调开机后只吹风不制冷，已经持续三天。","expectDate":"2026-09-01"}',
     'SUBMITTED', 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1002, 1002, 0, 0),
    (3002, 'SR-DEV-3002', 1, 1001, '宿舍报修', 100, 1002, '宿舍水龙头漏水',
     '{"faultType":"水电维修","description":"卫生间洗手台水龙头关不紧，持续滴水。"}',
     'PROCESSING', 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1002, 1002, 0, 0),
    (3003, 'SR-DEV-3003', 1, 3001, '在读证明申请', 300, 1002, '在读证明开具（实习求职）',
     '{"purpose":"实习求职","copies":2,"remark":"用于暑期实习入职材料。"}',
     'COMPLETED', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1002, 1002, 0, 0);

INSERT INTO work_order
    (id, order_no, request_id, campus_id, item_id, title, assigned_organization_id, assignee_id, assignee_name,
     status, priority, assigned_at, started_at, completed_at, created_at, updated_at, created_by, updated_by,
     deleted, version)
VALUES
    (4001, 'WO-DEV-4001', 3002, 1, 1001, '宿舍水龙头漏水', 12, 1003, '开发维修员', 'PROCESSING', 'NORMAL',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1004, 1003, 0, 0),
    (4002, 'WO-DEV-4002', 3003, 1, 3001, '在读证明开具（实习求职）', 10, 1003, '开发维修员', 'COMPLETED', 'NORMAL',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1004, 1003, 0, 0);

INSERT INTO service_request_log
    (id, request_id, work_order_id, action, from_status, to_status, operator_id, operator_name, comment,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (5001, 3001, NULL, 'SUBMIT', NULL, 'SUBMITTED', 1002, '开发申请人', '提交宿舍报修申请',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1002, 1002, 0, 0),
    (5002, 3002, NULL, 'SUBMIT', NULL, 'SUBMITTED', 1002, '开发申请人', '提交宿舍报修申请',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1002, 1002, 0, 0),
    (5003, 3002, 4001, 'DISPATCH', 'SUBMITTED', 'PROCESSING', 1004, '开发调度员', '指派给维修班组处理',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1004, 1004, 0, 0),
    (5004, 3002, 4001, 'START', 'PROCESSING', 'PROCESSING', 1003, '开发维修员', '开始上门处理',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1003, 1003, 0, 0),
    (5005, 3003, NULL, 'SUBMIT', NULL, 'SUBMITTED', 1002, '开发申请人', '提交在读证明申请',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1002, 1002, 0, 0),
    (5006, 3003, 4002, 'DISPATCH', 'SUBMITTED', 'PROCESSING', 1004, '开发调度员', '指派办理',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1004, 1004, 0, 0),
    (5007, 3003, 4002, 'START', 'PROCESSING', 'PROCESSING', 1003, '开发维修员', '开始办理',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1003, 1003, 0, 0),
    (5008, 3003, 4002, 'COMPLETE', 'PROCESSING', 'COMPLETED', 1003, '开发维修员', '电子证明已开具，请查收',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1003, 1003, 0, 0);

INSERT INTO notification
    (id, recipient_id, notification_type, title, content, related_type, related_id, read_at,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (6001, 1002, 'REQUEST', '申请已受理', '你的申请「宿舍空调不制冷」已提交成功，等待调度分配。', 'SERVICE_REQUEST', 3001,
     NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6002, 1002, 'REQUEST', '申请处理中', '你的申请「宿舍水龙头漏水」已派单，维修人员将尽快联系你。', 'SERVICE_REQUEST', 3002,
     NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6003, 1003, 'WORK_ORDER', '新工单待处理', '你有一个工单「宿舍水龙头漏水」待开始处理。', 'WORK_ORDER', 4001,
     NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6004, 1002, 'REQUEST', '申请已完成', '你的申请「在读证明开具（实习求职）」已办结，记得评价本次服务。', 'SERVICE_REQUEST', 3003,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);
