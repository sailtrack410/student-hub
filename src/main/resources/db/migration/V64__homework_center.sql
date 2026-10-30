-- 作业中心：教师/管理员发布课程作业，学生在 App 内查看并在线提交（截止时间校验、重复提交覆盖）。
-- 字符集：显式声明 ENGINE=InnoDB / DEFAULT CHARSET=utf8mb4 / COLLATE=utf8mb4_unicode_ci，
-- 与 MySQL 服务端（docker-compose --character-set-server=utf8mb4）保持一致；
-- H2 2.x（MODE=MySQL，集成测试）已验证可接受该建表尾注，故 MySQL/H2 双兼容。
-- 不插入任何作业/提交种子数据，全部由管理端接口创建。

CREATE TABLE IF NOT EXISTS homework (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    course_name VARCHAR(128) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    teacher_name VARCHAR(64) NULL,
    publish_at DATETIME NOT NULL,
    due_at DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_homework_campus_status ON homework (campus_id, status, publish_at);

CREATE TABLE IF NOT EXISTS homework_submission (
    id BIGINT NOT NULL,
    homework_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    submitted_at DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_homework_submission (homework_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_homework_submission_user ON homework_submission (user_id, submitted_at);
