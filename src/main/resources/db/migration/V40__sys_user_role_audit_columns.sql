-- sys_user_role 补齐 BaseEntity 审计列（V1 建表时只有 created_at/created_by，
-- 实体侧 UserRoleEntity 为绕开缺列而手写审计字段；本迁移后实体改继承 BaseEntity）
ALTER TABLE sys_user_role ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE sys_user_role ADD COLUMN updated_by BIGINT NULL;
ALTER TABLE sys_user_role ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0;
ALTER TABLE sys_user_role ADD COLUMN version INT NOT NULL DEFAULT 0;
