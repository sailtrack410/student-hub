-- 教务绑定采用逻辑删除；唯一约束需要区分有效记录和历史记录，才能重新绑定同一学号。
-- 拆成单子句、DROP INDEX 改 DROP CONSTRAINT：MySQL 方言，H2（集成测试库）解析失败
ALTER TABLE jw_binding DROP CONSTRAINT uk_jw_binding_user;
ALTER TABLE jw_binding DROP CONSTRAINT uk_jw_binding_sso_account;
ALTER TABLE jw_binding ADD CONSTRAINT uk_jw_binding_user_deleted UNIQUE (user_id, deleted);
ALTER TABLE jw_binding ADD CONSTRAINT uk_jw_binding_sso_account_deleted UNIQUE (sso_account, deleted);
