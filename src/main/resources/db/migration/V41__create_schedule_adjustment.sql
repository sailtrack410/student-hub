-- 调课通知：从青果调课监控页（jxap.jxaptzxx_rpt.jsp）抓取的权威变更公告。
-- 原始文本+解析结果双写（约定见 docs/competitor-mycc-adoption.md 3.2）：教务 HTML 形态会有意外，
-- 只存解析结果等于无法回溯与重解析。noticeTime+courseCode+classNo 幂等去重。
-- targetClasses 为班级名 JSON 数组，定向通知按 targetClasses ∩ sys_user.className 匹配。
CREATE TABLE schedule_adjustment (
    id BIGINT NOT NULL,
    semester_code VARCHAR(16) NOT NULL COMMENT '学期编码(学年,学期)',
    notice_time VARCHAR(32) NOT NULL COMMENT '教务公告发布时间(原文)',
    action_type VARCHAR(16) NOT NULL COMMENT '变更类型：补课/停课/调课等(原文)',
    course_code VARCHAR(64) NOT NULL COMMENT '课程代码',
    course_name VARCHAR(128) NOT NULL COMMENT '课程名称',
    class_no VARCHAR(32) NULL COMMENT '教学班号',
    target_classes TEXT NULL COMMENT '受影响班级 JSON 数组，如 ["计科B2314"]',
    detail_json TEXT NULL COMMENT '变更明细 JSON 数组（周次/节次/地点/教师 before-after）',
    summary VARCHAR(500) NULL COMMENT '一行人话摘要，通知正文直接使用',
    raw_text TEXT NULL COMMENT '教务返回的原始 HTML 片段',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_adjustment_natural (semester_code, notice_time, course_code, class_no)
);
