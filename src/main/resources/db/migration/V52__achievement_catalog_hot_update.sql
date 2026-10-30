-- 徽章方案B：字典热更新（2026-10-19）
-- 1) 新增分类字典表（分类名/排序可随字典下发，热更新不依赖发版）
-- 2) 勋章目录补 icon_url（内置 19 枚为 NULL，客户端走本地素材；新增码由管理端上传图标）
-- 3) 清理历史脏数据：已停用/已删除勋章的佩戴态清空，保证 /auth/me 与作者主页不再返回失效 code
CREATE TABLE achievement_category (
    id BIGINT NOT NULL,
    category_code VARCHAR(32) NOT NULL COMMENT '分类编码：COMMUNITY/SERVICE/WORKER/JW/GROWTH',
    category_name VARCHAR(64) NOT NULL COMMENT '分类展示名',
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_achievement_category_code (category_code)
);

ALTER TABLE achievement_badge ADD COLUMN icon_url VARCHAR(255) NULL;

INSERT INTO achievement_category (id, category_code, category_name, sort_order, created_by, updated_by)
VALUES
    (1, 'COMMUNITY', '社区互动', 10, 0, 0),
    (2, 'SERVICE',   '校园办事', 20, 0, 0),
    (3, 'WORKER',    '后勤服务', 30, 0, 0),
    (4, 'JW',        '教务学习', 40, 0, 0),
    (5, 'GROWTH',    '等级荣耀', 50, 0, 0);

UPDATE sys_user
   SET worn_badge_code = NULL
 WHERE worn_badge_code IS NOT NULL
   AND worn_badge_code NOT IN (SELECT badge_code FROM achievement_badge WHERE enabled = 1 AND deleted = 0);
