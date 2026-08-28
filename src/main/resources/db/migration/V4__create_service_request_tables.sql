CREATE TABLE service_request (
    id BIGINT NOT NULL,
    request_no VARCHAR(48) NOT NULL,
    campus_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    item_name VARCHAR(128) NOT NULL,
    category_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    form_data TEXT,
    status VARCHAR(32) NOT NULL,
    handler_organization_id BIGINT,
    idempotency_key VARCHAR(64),
    submitted_at TIMESTAMP,
    completed_at TIMESTAMP,
    reject_reason VARCHAR(512),
    cancel_reason VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_service_request_no UNIQUE (request_no),
    CONSTRAINT uk_service_request_idempotency UNIQUE (requester_id, idempotency_key),
    CONSTRAINT fk_service_request_item FOREIGN KEY (item_id) REFERENCES service_item (id),
    CONSTRAINT fk_service_request_requester FOREIGN KEY (requester_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_service_request_requester ON service_request (requester_id, status);
CREATE INDEX idx_service_request_handler_org ON service_request (handler_organization_id, status);

CREATE TABLE work_order (
    id BIGINT NOT NULL,
    order_no VARCHAR(48) NOT NULL,
    request_id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    assigned_organization_id BIGINT,
    assignee_id BIGINT,
    assignee_name VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    complete_remark VARCHAR(512),
    assigned_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_work_order_no UNIQUE (order_no),
    CONSTRAINT uk_work_order_request UNIQUE (request_id),
    CONSTRAINT fk_work_order_request FOREIGN KEY (request_id) REFERENCES service_request (id),
    CONSTRAINT fk_work_order_org FOREIGN KEY (assigned_organization_id) REFERENCES sys_organization (id),
    CONSTRAINT fk_work_order_assignee FOREIGN KEY (assignee_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_work_order_assignee ON work_order (assignee_id, status);
CREATE INDEX idx_work_order_org_status ON work_order (assigned_organization_id, status);

CREATE TABLE service_request_log (
    id BIGINT NOT NULL,
    request_id BIGINT NOT NULL,
    work_order_id BIGINT,
    action VARCHAR(32) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(128) NOT NULL,
    comment VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_service_request_log_request FOREIGN KEY (request_id) REFERENCES service_request (id)
);

CREATE INDEX idx_service_request_log_request ON service_request_log (request_id, created_at);
