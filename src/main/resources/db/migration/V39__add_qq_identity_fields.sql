-- QQ 登录身份：对齐 V2 的 wechat_open_id 做法。
-- unionid 现在建列：QQ 互联「移动应用」与「网站应用」对同一 QQ 用户发放不同 openid，
-- 未来接入网站/PC 登录时没有 unionid 会造成同一个人的账号分裂成两条。
ALTER TABLE sys_user ADD COLUMN qq_open_id VARCHAR(128);
ALTER TABLE sys_user ADD COLUMN qq_union_id VARCHAR(128);

-- 允许 NULL 重复（与 uk_sys_user_wechat_open_id 一致）："只有 QQ、没有微信"的用户正常共存。
ALTER TABLE sys_user ADD CONSTRAINT uk_sys_user_qq_open_id UNIQUE (qq_open_id);
