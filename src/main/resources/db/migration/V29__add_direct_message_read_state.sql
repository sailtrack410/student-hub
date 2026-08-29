ALTER TABLE community_direct_message ADD COLUMN read_at TIMESTAMP NULL;
CREATE INDEX idx_direct_message_unread ON community_direct_message (campus_id, receiver_id, read_at, created_at);
