-- V77 管理端身份审核增强（U04）
-- 1) sys_user 增加审核过程字段（最近一次备注 / 申请时间 / 审核人 / 审核时间）
-- 2) user_identity_evidence：身份申请材料（引用 file_storage.id，真实上传文件，无材料则为空）
-- 3) user_identity_review：独立审核记录（通过/拒绝历史，含拒绝原因码与材料快照）
-- 4) identity_reject_reason_template：拒绝原因模板（内置模板真实落库，可增删改，内置不可删除）
--
-- 兼容性：仅新增列/表/索引与配置种子，不改动 V76 及更早迁移；
-- 字段均为 nullable 或带默认值；类型使用 TIMESTAMP/TINYINT，同时兼容 MySQL 8 与 H2 2.x（MODE=MySQL）。
-- 说明：authorization_submitted_at 对历史数据按注册时间回填（可空字段，仅作时间线起点，不伪造审核记录）。

-- ---------------------------------------------------------------------------
-- 1) 审核过程字段
-- ---------------------------------------------------------------------------
ALTER TABLE sys_user ADD COLUMN authorization_submitted_at TIMESTAMP NULL;
ALTER TABLE sys_user ADD COLUMN authorization_note VARCHAR(500) NULL;
ALTER TABLE sys_user ADD COLUMN authorization_reviewed_by BIGINT NULL;
ALTER TABLE sys_user ADD COLUMN authorization_reviewed_at TIMESTAMP NULL;

CREATE INDEX idx_sys_user_authorization ON sys_user (campus_id, authorization_status, authorization_submitted_at);

-- 历史数据回填：已进入授权流程的用户以注册时间作为申请时间（可空，不覆盖已有值）。
UPDATE sys_user
   SET authorization_submitted_at = created_at
 WHERE authorization_submitted_at IS NULL
   AND authorization_status IN ('PENDING', 'AUTHORIZED', 'REJECTED');

-- ---------------------------------------------------------------------------
-- 2) 身份申请材料（只存真实文件引用；file_id 为空的行不允许插入，页面据此显示「未上传」）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_identity_evidence (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    material_type VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    material_name VARCHAR(128) NULL,
    remark VARCHAR(255) NULL,
    uploaded_by BIGINT NOT NULL DEFAULT 0,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_identity_evidence_user ON user_identity_evidence (user_id, deleted, uploaded_at);
CREATE INDEX idx_identity_evidence_campus ON user_identity_evidence (campus_id, material_type);

-- ---------------------------------------------------------------------------
-- 3) 独立审核记录（每次通过/拒绝写一条；材料快照记录审核当时的材料，便于回溯）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_identity_review (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NULL,
    reason_code VARCHAR(64) NULL,
    reason VARCHAR(500) NULL,
    evidence_snapshot VARCHAR(1000) NULL,
    reviewer_id BIGINT NOT NULL DEFAULT 0,
    reviewer_name VARCHAR(64) NULL,
    reviewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_identity_review_user ON user_identity_review (user_id, reviewed_at);
CREATE INDEX idx_identity_review_campus ON user_identity_review (campus_id, action, reviewed_at);

-- ---------------------------------------------------------------------------
-- 4) 拒绝原因模板（campus_id=0 为平台内置，builtin=1 不允许删除）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS identity_reject_reason_template (
    id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL DEFAULT 0,
    template_code VARCHAR(64) NOT NULL,
    template_text VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    builtin TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_identity_reason_code UNIQUE (campus_id, template_code)
);

CREATE INDEX idx_identity_reason_status ON identity_reject_reason_template (status, sort_order);

-- 内置模板：真实持久化，管理端可改文案/停用，但不可删除（builtin=1）。
INSERT INTO identity_reject_reason_template
    (id, campus_id, template_code, template_text, sort_order, status, builtin, remark,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (7701, 0, 'STUDENT_NO_MISMATCH', '学号与姓名不匹配，请核对教务系统登记信息后重新提交', 10, 'ENABLED', 1, '内置模板', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7702, 0, 'EVIDENCE_BLURRY', '证明材料照片模糊或关键信息被遮挡，无法辨认，请重新上传清晰照片', 20, 'ENABLED', 1, '内置模板', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7703, 0, 'EVIDENCE_MISSING', '未提供有效学生证 / 教师工作证 / 在职证明等证明材料', 30, 'ENABLED', 1, '内置模板', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7704, 0, 'IDENTITY_MISMATCH', '所选身份类型与提交材料不符，请按实际身份重新提交', 40, 'ENABLED', 1, '内置模板', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7705, 0, 'EVIDENCE_SUSPICIOUS', '材料存在涂改、拼接或伪造痕迹，请提供原始证件照片', 50, 'ENABLED', 1, '内置模板', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7706, 0, 'OTHER', '其他原因（请在备注中补充说明）', 90, 'ENABLED', 1, '内置模板', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);
