-- 恢复教务账号单一绑定规则：历史逻辑删除记录不能占用学号，仍只允许一个有效绑定。
-- 拆成单子句、DROP INDEX 改 DROP CONSTRAINT：MySQL 方言，H2（集成测试库）解析失败
ALTER TABLE jw_binding DROP CONSTRAINT uk_jw_binding_user_deleted;
ALTER TABLE jw_binding DROP CONSTRAINT uk_jw_binding_sso_account_deleted;
ALTER TABLE jw_binding ADD CONSTRAINT uk_jw_binding_user UNIQUE (user_id);
ALTER TABLE jw_binding ADD CONSTRAINT uk_jw_binding_sso_account UNIQUE (sso_account);
