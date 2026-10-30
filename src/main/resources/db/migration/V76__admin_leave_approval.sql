-- V76：独立请假审批（H01）所需的申请单扩展字段。
-- 说明：请假仍复用 service_request（item_code = AFFAIR-LEAVE），本迁移只加请假专属列，
-- 由 servicerequest 模块在提交/审批时写入；历史数据缺失时由接口按 form_data 解析兜底展示。
-- 字段均 nullable 或带默认值，兼容 H2（MySQL 模式）与 MySQL。

ALTER TABLE service_request ADD COLUMN leave_type VARCHAR(32) NULL;
ALTER TABLE service_request ADD COLUMN leave_start_at DATETIME NULL;
ALTER TABLE service_request ADD COLUMN leave_end_at DATETIME NULL;
ALTER TABLE service_request ADD COLUMN leave_days DECIMAL(6, 1) NULL;
-- 销假时间与销假状态：NONE 未销假 / CANCELLED 已销假；销假不改写原有申请状态语义之外的内容
ALTER TABLE service_request ADD COLUMN leave_cancel_at DATETIME NULL;
ALTER TABLE service_request ADD COLUMN leave_cancel_status VARCHAR(16) NOT NULL DEFAULT 'NONE';

CREATE INDEX idx_service_request_leave ON service_request (item_id, status, submitted_at);
