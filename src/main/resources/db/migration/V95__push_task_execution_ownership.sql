ALTER TABLE push_task ADD COLUMN execution_token VARCHAR(36) NULL;
CREATE INDEX idx_push_task_due ON push_task(status, scheduled_at, deleted);
