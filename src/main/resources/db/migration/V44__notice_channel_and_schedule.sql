-- 公告渠道拆分（API需求清单 4.2）：同一张 screen_notice 表承载终端大屏与 App 公告两个渠道，
-- 存量数据默认 SCREEN 渠道不受影响；发布状态 + 定时发布时间支持"先建稿、到点自动上线"。
-- H2 兼容：单子句 ALTER、无列 COMMENT。
ALTER TABLE screen_notice ADD COLUMN IF NOT EXISTS channel VARCHAR(16) NOT NULL DEFAULT 'SCREEN';
ALTER TABLE screen_notice ADD COLUMN IF NOT EXISTS publish_status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED';
ALTER TABLE screen_notice ADD COLUMN IF NOT EXISTS publish_at DATETIME NULL;
ALTER TABLE screen_notice ADD COLUMN IF NOT EXISTS author VARCHAR(64) NULL;

-- 定时发布扫描索引（H2 兼容写法）
CREATE INDEX IF NOT EXISTS idx_screen_notice_schedule ON screen_notice (publish_status, publish_at);

-- App 公告权限点（默认绑定平台管理员，参照 V38 community:moderate 模式）
INSERT IGNORE INTO sys_permission
    (id, permission_code, permission_name, permission_type, status, created_at, updated_at, created_by, updated_by,
     deleted, version)
VALUES
    (25, 'announcement:manage', '公告管理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, created_at, created_by)
VALUES
    (1601, 1, 25, CURRENT_TIMESTAMP, 0);
