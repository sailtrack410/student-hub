-- V69 管理端服务闭环增强（admin-svc）
-- 1) 工单 SLA：承诺时限快照 + 截止时间（创建工单时按服务项 slaHours 计算）
-- 2) 服务目录：默认优先级、拍照流程、办事指南/材料清单/常见问题（S01/S02/S05）
-- 说明：仅新增列与索引，历史数据保持可空；旧工单读取时由服务层按创建时间兜底计算截止时间。

ALTER TABLE work_order ADD COLUMN sla_hours INT NULL;
ALTER TABLE work_order ADD COLUMN due_at TIMESTAMP NULL;
CREATE INDEX idx_work_order_due ON work_order (due_at);

ALTER TABLE service_item ADD COLUMN default_priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE service_item ADD COLUMN photo_workflow_required TINYINT NOT NULL DEFAULT 0;
ALTER TABLE service_item ADD COLUMN guide_content TEXT NULL;
ALTER TABLE service_item ADD COLUMN material_list TEXT NULL;
ALTER TABLE service_item ADD COLUMN faq TEXT NULL;

-- 历史工单回填：sla_hours 优先取服务项承诺时限，缺省 24 小时；due_at 由创建时间推算。
-- TIMESTAMPADD 同时被 MySQL 8 与 H2(MySQL 模式) 支持，保证测试库与生产库迁移一致。
UPDATE work_order
   SET sla_hours = COALESCE((SELECT i.sla_hours FROM service_item i WHERE i.id = work_order.item_id), 24)
 WHERE sla_hours IS NULL;

UPDATE work_order
   SET due_at = TIMESTAMPADD(HOUR, COALESCE(sla_hours, 24), created_at)
 WHERE due_at IS NULL;
