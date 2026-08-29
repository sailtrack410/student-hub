CREATE TABLE community_direct_message (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_direct_message_pair (campus_id, sender_id, receiver_id, created_at),
    INDEX idx_direct_message_receiver (campus_id, receiver_id, created_at)
);
