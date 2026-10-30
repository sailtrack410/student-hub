ALTER TABLE ai_knowledge_document ADD COLUMN index_token VARCHAR(36) NULL;
ALTER TABLE ai_knowledge_document ADD COLUMN index_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE ai_knowledge_document ADD COLUMN processed_chunks INT NOT NULL DEFAULT 0;
ALTER TABLE ai_knowledge_document ADD COLUMN total_chunks INT NOT NULL DEFAULT 0;
ALTER TABLE ai_knowledge_document ADD COLUMN index_lease_until TIMESTAMP NULL;
ALTER TABLE ai_knowledge_document ADD COLUMN next_attempt_at TIMESTAMP NULL;
CREATE INDEX idx_ai_document_index_queue ON ai_knowledge_document(status, next_attempt_at, index_lease_until, deleted);
-- Existing interrupted synchronous jobs resume from their stored original file.
UPDATE ai_knowledge_document SET status='QUEUED', next_attempt_at=CURRENT_TIMESTAMP WHERE status IN ('PENDING', 'PROCESSING') AND deleted=0;
