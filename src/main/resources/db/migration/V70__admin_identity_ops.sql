-- 管理端身份运营增强（admin-identity bundle）：
-- 1) 组织负责人：sys_organization 增加 leader_id，指向 sys_user.id。
--    可空（既有组织与新建组织未指定负责人时保持 NULL）；不建外键，避免删除用户时被组织引用阻塞，
--    负责人由应用层校验（必须是当前校区启用用户）。
-- 2) 登录设备、用户导入、SSO/花名册状态均复用既有表或只读接口，不新增表。
ALTER TABLE sys_organization ADD COLUMN leader_id BIGINT NULL;

CREATE INDEX idx_sys_organization_leader ON sys_organization (leader_id);
