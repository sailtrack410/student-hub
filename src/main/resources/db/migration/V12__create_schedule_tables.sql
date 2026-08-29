CREATE TABLE schedule_course (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    semester_code VARCHAR(16) NOT NULL,
    semester_name VARCHAR(64),
    course_code VARCHAR(64),
    course_name VARCHAR(255) NOT NULL,
    teacher VARCHAR(128),
    class_code VARCHAR(64),
    course_nature VARCHAR(32),
    location VARCHAR(255),
    day_of_week INT NOT NULL,
    start_section INT NOT NULL,
    end_section INT NOT NULL,
    weeks VARCHAR(64),
    week_parity INT NOT NULL DEFAULT 0,
    raw_text VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_course_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_schedule_course_user ON schedule_course (user_id, semester_code);

CREATE TABLE schedule_class_course (
    id BIGINT NOT NULL,
    class_code VARCHAR(64) NOT NULL,
    semester_code VARCHAR(16) NOT NULL,
    semester_name VARCHAR(64),
    course_code VARCHAR(64),
    course_name VARCHAR(255) NOT NULL,
    teacher VARCHAR(128),
    course_nature VARCHAR(32),
    location VARCHAR(255),
    day_of_week INT NOT NULL,
    start_section INT NOT NULL,
    end_section INT NOT NULL,
    weeks VARCHAR(64),
    week_parity INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_schedule_class_course UNIQUE (class_code, semester_code, course_code, day_of_week, start_section, weeks, week_parity)
);

CREATE INDEX idx_schedule_class_lookup ON schedule_class_course (class_code, semester_code);
