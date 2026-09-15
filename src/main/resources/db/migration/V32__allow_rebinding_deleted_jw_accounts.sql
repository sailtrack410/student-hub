-- 教务绑定采用逻辑删除；唯一约束需要区分有效记录和历史记录，才能重新绑定同一学号。
ALTER TABLE jw_binding
    DROP INDEX uk_jw_binding_user,
    DROP INDEX uk_jw_binding_sso_account,
    ADD CONSTRAINT uk_jw_binding_user_deleted UNIQUE (user_id, deleted),
    ADD CONSTRAINT uk_jw_binding_sso_account_deleted UNIQUE (sso_account, deleted);
