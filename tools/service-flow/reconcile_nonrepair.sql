-- Run ONLY AFTER V109. Idempotent, additive audit + archive; no application state or business data deletion.
-- Inspect the candidate SELECT first, then execute this file in a database transaction after backup.
INSERT INTO service_flow_repair_audit
    (work_order_id, request_id, request_flow_type, original_order_status,
     original_request_status, repaired_at, repair_reason)
SELECT w.id, w.request_id, r.flow_type, w.status, r.status, CURRENT_TIMESTAMP,
       '补偿核对：非报修工单保留审计，退出维修派单队列；申请原业务状态不变'
FROM work_order w JOIN service_request r ON r.id = w.request_id
WHERE r.flow_type <> 'REPAIR'
  AND NOT EXISTS (SELECT 1 FROM service_flow_repair_audit a WHERE a.work_order_id = w.id);

INSERT INTO service_request_log
    (id, request_id, work_order_id, action, from_status, to_status, operator_id, operator_name,
     comment, created_at, updated_at, created_by, updated_by, deleted, version)
SELECT -a.work_order_id, a.request_id, a.work_order_id, 'FLOW_DETACH',
       a.original_request_status, a.original_request_status, 0, '流程修复',
       '非报修工单退出维修队列，原单及处理记录保留；业务以申请状态为准',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM service_flow_repair_audit a
WHERE NOT EXISTS (SELECT 1 FROM service_request_log l WHERE l.id = -a.work_order_id);

UPDATE work_order SET flow_archived = TRUE
WHERE flow_archived = FALSE AND request_id IN
    (SELECT id FROM service_request WHERE flow_type <> 'REPAIR');
