-- 教务账号单一绑定规则需要"只约束有效行"的唯一索引：逻辑删除的行不应占用学号。
-- MySQL 8 与 H2（MySQL 模式）都不支持部分索引/索引内表达式，改用生成列等价实现：
-- active_flag 在 deleted=0 时恒为 0，deleted=1 时为 NULL；NULL 不参与唯一性比较，
-- 因此历史行任意多条互不冲突，有效行仍全局唯一。
-- 约束名为 V33 恢复后的 uk_jw_binding_user / uk_jw_binding_sso_account；MySQL 8 的
-- DDL 无事务，失败会留下半程状态。注意 MySQL 8 不支持 ADD COLUMN IF NOT EXISTS
-- （MariaDB 语法，H2 MySQL 模式支持），此处必须用普通 ADD COLUMN 保证两边兼容。
-- 语句顺序约束：必须先建新唯一索引、再删旧索引——jw_binding.user_id 上有外键，
-- MySQL 要求删除支撑索引前已存在 user_id 为最左前缀的替代索引（否则 Error 1553）。
ALTER TABLE jw_binding ADD COLUMN active_flag TINYINT GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN 0 END) NULL;
ALTER TABLE jw_binding ADD CONSTRAINT uk_jw_binding_user_active UNIQUE (user_id, active_flag);
ALTER TABLE jw_binding ADD CONSTRAINT uk_jw_binding_sso_account_active UNIQUE (sso_account, active_flag);
ALTER TABLE jw_binding DROP INDEX uk_jw_binding_user;
ALTER TABLE jw_binding DROP INDEX uk_jw_binding_sso_account;
