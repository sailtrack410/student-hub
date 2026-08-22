ALTER TABLE sys_user ADD COLUMN wechat_open_id VARCHAR(128);
ALTER TABLE sys_user ADD COLUMN identity_type VARCHAR(32) NOT NULL DEFAULT 'STUDENT';
ALTER TABLE sys_user ADD COLUMN authorization_status VARCHAR(32) NOT NULL DEFAULT 'BASIC';

ALTER TABLE sys_user ADD CONSTRAINT uk_sys_user_wechat_open_id UNIQUE (wechat_open_id);

UPDATE sys_user SET identity_type = 'ADMIN', authorization_status = 'AUTHORIZED' WHERE username = 'admin';
UPDATE sys_user SET identity_type = 'STUDENT', authorization_status = 'BASIC' WHERE username = 'requester';
UPDATE sys_user SET identity_type = 'LOGISTICS', authorization_status = 'AUTHORIZED' WHERE username IN ('worker', 'dispatcher');
