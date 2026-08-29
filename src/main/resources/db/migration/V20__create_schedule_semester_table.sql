-- 记录每个用户导入过课表的所有学期（含未排课的“空学期”）。
-- 学期下拉据此累积展示，切换学期后不会把历史学期顶掉。
CREATE TABLE schedule_semester (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    semester_code VARCHAR(16) NOT NULL,
    semester_name VARCHAR(64),
    last_synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_schedule_semester_user UNIQUE (user_id, semester_code)
);