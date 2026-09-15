-- 恢复教务账号单一绑定规则：历史逻辑删除记录不能占用学号，仍只允许一个有效绑定。
ALTER TABLE jw_binding
    DROP INDEX uk_jw_binding_user_deleted,
    DROP INDEX uk_jw_binding_sso_account_deleted,
    ADD CONSTRAINT uk_jw_binding_user UNIQUE (user_id),
    ADD CONSTRAINT uk_jw_binding_sso_account UNIQUE (sso_account);
