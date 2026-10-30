-- 账号注册/找回密码：校园邮箱与学号
-- H2 兼容：逐列单子句 ALTER（2026-10-19 测试修复）
ALTER TABLE sys_user ADD COLUMN email VARCHAR(128) NULL;
ALTER TABLE sys_user ADD COLUMN student_no VARCHAR(32) NULL;

CREATE UNIQUE INDEX uk_sys_user_email ON sys_user (email);
CREATE UNIQUE INDEX uk_sys_user_student_no ON sys_user (student_no);
