CREATE TABLE sys_campus (
    id BIGINT NOT NULL,
    campus_code VARCHAR(64) NOT NULL,
    campus_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_campus_code UNIQUE (campus_code)
);

CREATE TABLE sys_organization (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    parent_id BIGINT,
    organization_code VARCHAR(64) NOT NULL,
    organization_name VARCHAR(128) NOT NULL,
    organization_type VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_organization_code UNIQUE (campus_id, organization_code),
    CONSTRAINT fk_sys_organization_campus FOREIGN KEY (campus_id) REFERENCES sys_campus (id),
    CONSTRAINT fk_sys_organization_parent FOREIGN KEY (parent_id) REFERENCES sys_organization (id)
);

CREATE INDEX idx_sys_organization_parent ON sys_organization (parent_id);

CREATE TABLE sys_user (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    default_organization_id BIGINT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100),
    display_name VARCHAR(128) NOT NULL,
    phone VARCHAR(32),
    avatar_url VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_username UNIQUE (username),
    CONSTRAINT fk_sys_user_campus FOREIGN KEY (campus_id) REFERENCES sys_campus (id),
    CONSTRAINT fk_sys_user_default_org FOREIGN KEY (default_organization_id) REFERENCES sys_organization (id)
);

CREATE TABLE sys_role (
    id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    role_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_code UNIQUE (role_code)
);

CREATE TABLE sys_permission (
    id BIGINT NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    permission_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_permission_code UNIQUE (permission_code)
);

CREATE TABLE sys_user_organization (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    primary_membership TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_organization UNIQUE (user_id, organization_id),
    CONSTRAINT fk_sys_user_org_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_user_org_org FOREIGN KEY (organization_id) REFERENCES sys_organization (id)
);

CREATE TABLE sys_user_role (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    organization_id BIGINT,
    data_scope VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_role UNIQUE (user_id, role_id, organization_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_sys_user_role_org FOREIGN KEY (organization_id) REFERENCES sys_organization (id)
);

CREATE INDEX idx_sys_user_role_user ON sys_user_role (user_id);

CREATE TABLE sys_role_permission (
    id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_permission UNIQUE (role_id, permission_id),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
);
