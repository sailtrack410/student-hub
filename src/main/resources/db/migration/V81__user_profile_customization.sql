-- 记录昵称/头像是否被用户主动设置过：
-- QQ 登录只在用户从未自定义时用 QQ 资料覆盖默认昵称/头像，自定义值保持不变。
ALTER TABLE sys_user ADD COLUMN display_name_customized TINYINT NOT NULL DEFAULT 0;
ALTER TABLE sys_user ADD COLUMN avatar_customized TINYINT NOT NULL DEFAULT 0;

-- 历史数据回填（只执行一次）：
-- 密码账号旧默认昵称等于 username；社交账号默认昵称为固定文案；其余视为用户自定义。
-- 微信/QQ 的头像此前没有落库，因此已有 avatar_url 的历史值均按自定义处理。
UPDATE sys_user
SET display_name_customized = 1
WHERE display_name IS NOT NULL
  AND display_name <> username
  AND display_name NOT IN ('QQ用户', '微信用户', '调试同学')
  AND NOT (display_name LIKE '用户______');

UPDATE sys_user
SET avatar_customized = 1
WHERE avatar_url IS NOT NULL
  AND avatar_url <> '';
