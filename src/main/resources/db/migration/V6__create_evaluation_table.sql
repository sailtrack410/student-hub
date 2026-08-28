CREATE TABLE service_evaluation (
    id BIGINT NOT NULL,
    request_id BIGINT NOT NULL,
    work_order_id BIGINT,
    evaluator_id BIGINT NOT NULL,
    assignee_id BIGINT,
    assignee_name VARCHAR(128),
    rating INT NOT NULL,
    tags VARCHAR(256),
    comment VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_service_evaluation_request UNIQUE (request_id),
    CONSTRAINT fk_service_evaluation_request FOREIGN KEY (request_id) REFERENCES service_request (id)
);

CREATE INDEX idx_service_evaluation_assignee ON service_evaluation (assignee_id);
