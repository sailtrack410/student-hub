-- 组织架构单根树：校区从"树层级"降为节点属性（campus_scope），业务数据仍按 campus_id 隔离。
-- 口径：
--   CAMPUS  具体校区（campus_id 必填）
--   SCHOOL  校级节点（campus_id 为空，如教务处/图书馆/根组织）
--   MULTI   跨校区节点（campus_id 为空，如跨校区的学院/专业）
-- 兼容 MySQL 8.0 与 H2 2.x（MODE=MySQL）：逐条 ALTER、不使用多列语法。
ALTER TABLE sys_organization ADD COLUMN campus_scope VARCHAR(16) NOT NULL DEFAULT 'CAMPUS';
ALTER TABLE sys_organization MODIFY COLUMN campus_id BIGINT NULL;

-- 编码改为全局唯一：校区别再参与唯一键，避免同名组织在不同校区重复建树。
-- MySQL 要求外键列始终有索引；原 uk(campus_id, organization_code) 同时承担
-- fk_sys_organization_campus 的索引职责，因此先补 campus_id 单列索引再删旧唯一键。
CREATE INDEX idx_sys_organization_campus ON sys_organization (campus_id);
ALTER TABLE sys_organization DROP CONSTRAINT uk_sys_organization_code;
ALTER TABLE sys_organization ADD CONSTRAINT uk_sys_organization_code UNIQUE (organization_code);

-- 存量根组织（原 organization_type=CAMPUS）升级为真正的校级根节点。
UPDATE sys_organization
   SET campus_scope = 'SCHOOL',
       campus_id = NULL,
       organization_type = 'ROOT'
 WHERE parent_id IS NULL;

CREATE INDEX idx_sys_organization_campus_scope ON sys_organization (campus_scope);
