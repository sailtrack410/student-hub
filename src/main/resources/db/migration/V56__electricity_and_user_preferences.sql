-- 宿舍电费查询（校园资产仅保留电费查询）+ 用户偏好持久化
-- H2/MySQL 双兼容：去掉 ENGINE/CHARSET/COMMENT/ON UPDATE 等 MySQL 专属尾注；
-- 种子数据用普通 INSERT（该迁移只会在空库执行一次）。
CREATE TABLE IF NOT EXISTS campus_electricity_account (
    id BIGINT PRIMARY KEY,
    campus_id BIGINT NOT NULL,
    building VARCHAR(64) NOT NULL,
    room_no VARCHAR(32) NOT NULL,
    balance DECIMAL(10,2) NOT NULL DEFAULT 0,
    monthly_usage DECIMAL(10,2) NOT NULL DEFAULT 0,
    monthly_cost DECIMAL(10,2) NOT NULL DEFAULT 0,
    subsidy_kwh DECIMAL(10,2) NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_electricity_room (campus_id, building, room_no)
);

INSERT INTO campus_electricity_account
    (id, campus_id, building, room_no, balance, monthly_usage, monthly_cost, subsidy_kwh, updated_at)
VALUES
    (56001, 1, '众立楼', '302', 48.50, 48.50, 26.68, 6.00, '2026-10-19 08:00:00'),
    (56002, 1, '众立楼', '303', 12.80, 31.20, 17.16, 6.00, '2026-10-19 08:00:00'),
    (56003, 1, '文昌楼', '201', 86.20, 20.40, 11.22, 6.00, '2026-10-19 08:00:00'),
    (56004, 1, '明德楼', '105', 5.60, 56.80, 31.24, 6.00, '2026-10-19 08:00:00');

CREATE TABLE IF NOT EXISTS sys_user_preference (
    user_id BIGINT PRIMARY KEY,
    chat_push TINYINT NOT NULL DEFAULT 1,
    repair_push TINYINT NOT NULL DEFAULT 1,
    work_order_push TINYINT NOT NULL DEFAULT 1,
    chat_reminder TINYINT NOT NULL DEFAULT 0,
    show_academic_info TINYINT NOT NULL DEFAULT 1,
    show_medals TINYINT NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
