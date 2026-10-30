-- SYS06：定时任务启停状态；未写入的 key 默认启用，不改变已有调度行为。
CREATE TABLE IF NOT EXISTS sys_scheduled_task_state (
    task_key VARCHAR(255) NOT NULL PRIMARY KEY,
    enabled TINYINT NOT NULL DEFAULT 1,
    note VARCHAR(255) NULL,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
