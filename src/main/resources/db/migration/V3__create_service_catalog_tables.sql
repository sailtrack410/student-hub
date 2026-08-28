CREATE TABLE service_category (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    category_code VARCHAR(64) NOT NULL,
    category_name VARCHAR(128) NOT NULL,
    icon VARCHAR(128),
    description VARCHAR(512),
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_service_category_code UNIQUE (campus_id, category_code),
    CONSTRAINT fk_service_category_campus FOREIGN KEY (campus_id) REFERENCES sys_campus (id)
);

CREATE TABLE service_item (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(128) NOT NULL,
    summary VARCHAR(512),
    description TEXT,
    icon VARCHAR(128),
    form_schema TEXT,
    handler_organization_id BIGINT,
    sla_hours INT,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_service_item_code UNIQUE (campus_id, item_code),
    CONSTRAINT fk_service_item_category FOREIGN KEY (category_id) REFERENCES service_category (id),
    CONSTRAINT fk_service_item_org FOREIGN KEY (handler_organization_id) REFERENCES sys_organization (id)
);

CREATE INDEX idx_service_item_category ON service_item (category_id, sort_order);
