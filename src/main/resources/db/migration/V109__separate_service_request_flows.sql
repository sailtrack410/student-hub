-- Explicit routing. Only the two known repair services dispatch maintenance work.
-- Unknown/custom services start with MANUAL, never inferred from display names.
ALTER TABLE service_item ADD COLUMN flow_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL';
UPDATE service_item SET flow_type = 'REPAIR' WHERE item_code IN ('REPAIR-DORM', 'REPAIR-PUBLIC');
UPDATE service_item SET flow_type = 'LEAVE_APPROVAL' WHERE item_code = 'AFFAIR-LEAVE';
UPDATE service_item SET flow_type = 'GUIDE' WHERE item_code = 'GUIDE-CONSULT';
-- These two services previously enforced photos in Java regardless of the old column default.
-- Persist that effective policy once; later edits use this explicit setting without code-name inference.
UPDATE service_item SET photo_workflow_required = 1 WHERE item_code IN ('REPAIR-DORM', 'REPAIR-PUBLIC');

-- Snapshot the selected flow. Later catalog edits must not reroute existing requests.
ALTER TABLE service_request ADD COLUMN flow_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE service_request ADD COLUMN item_code VARCHAR(64) NULL;
ALTER TABLE service_request ADD COLUMN approval_comment VARCHAR(2000) NULL;
UPDATE service_request SET flow_type = COALESCE(
    (SELECT i.flow_type FROM service_item i WHERE i.id = service_request.item_id), 'MANUAL'),
    item_code = (SELECT i.item_code FROM service_item i WHERE i.id = service_request.item_id);
CREATE INDEX idx_service_request_flow ON service_request (campus_id, flow_type, status);

-- Preserve every historical work-order field, including state, assignee, photos and dates.
-- The archive flag only removes records from repair operations; it is not logical deletion.
ALTER TABLE work_order ADD COLUMN flow_archived BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE service_flow_repair_audit (
    work_order_id BIGINT NOT NULL PRIMARY KEY,
    request_id BIGINT NOT NULL,
    request_flow_type VARCHAR(32) NOT NULL,
    original_order_status VARCHAR(32) NOT NULL,
    original_request_status VARCHAR(32) NOT NULL,
    repaired_at TIMESTAMP NOT NULL,
    repair_reason VARCHAR(512) NOT NULL
);
INSERT INTO service_flow_repair_audit
    (work_order_id, request_id, request_flow_type, original_order_status,
     original_request_status, repaired_at, repair_reason)
SELECT w.id, w.request_id, r.flow_type, w.status, r.status, CURRENT_TIMESTAMP,
       '流程分流：非报修工单保留审计，退出维修派单队列；申请原业务状态不变'
FROM work_order w JOIN service_request r ON r.id = w.request_id
WHERE r.flow_type <> 'REPAIR';

-- Negative IDs reserve a deterministic migration namespace; normal generated IDs are positive.
INSERT INTO service_request_log
    (id, request_id, work_order_id, action, from_status, to_status, operator_id, operator_name,
     comment, created_at, updated_at, created_by, updated_by, deleted, version)
SELECT -a.work_order_id, a.request_id, a.work_order_id, 'FLOW_DETACH',
       a.original_request_status, a.original_request_status, 0, '流程迁移',
       '非报修工单退出维修队列，原单及处理记录保留；业务以申请状态为准',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM service_flow_repair_audit a;
UPDATE work_order SET flow_archived = TRUE
WHERE id IN (SELECT work_order_id FROM service_flow_repair_audit);
CREATE INDEX idx_work_order_flow_archive ON work_order (flow_archived, campus_id, status);

-- Replace only unchanged legacy seed text, preserving administrators' edited descriptions.
UPDATE service_item SET description = '适用于学生因病、因事需要请假的申请。请如实填写请假类型与时间范围，由具备权限且在受理范围内的管理员审批并通知结果。'
WHERE item_code = 'AFFAIR-LEAVE' AND description = '适用于学生因病、因事需要请假的申请。请如实填写请假类型与时间范围，辅导员审批后将通过消息通知结果。';
UPDATE service_item SET description = '请提交在读证明所需用途与份数，等待受理部门人工确认办理方式和领取要求。本系统尚未接通自动出证或电子证明下载。'
WHERE item_code = 'CERT-ENROLL' AND description = '适用于需要学校出具在读证明的场景。证明将在承诺时限内开具电子版，可凭申请记录到指定地点领取纸质版。';
