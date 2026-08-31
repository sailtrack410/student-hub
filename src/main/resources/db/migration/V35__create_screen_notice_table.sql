CREATE TABLE screen_notice (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notice_type VARCHAR(16) NOT NULL DEFAULT 'NOTICE' COMMENT 'NOTICE 通知 / NEWS 要闻 / ACTIVITY 活动',
    title VARCHAR(128) NOT NULL,
    summary VARCHAR(512) NULL,
    publish_date DATE NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_screen_notice_enabled (enabled, sort_order)
);

INSERT INTO screen_notice (notice_type, title, summary, publish_date, sort_order) VALUES
('ACTIVITY', '校园创意集市今天开市', '中心广场 9:00-21:00，68 个学生摊位，手作、文创、闲置好物一站式逛齐；现场终端可扫码体验「码上办」全部服务。', '2026-08-30', 1),
('ACTIVITY', '创意集市「最佳摊位」现场投票', '逛集市别忘投票：扫码进入小程序社区频道，为你喜欢的摊位打 call，今晚 20:00 截止。', '2026-08-30', 2),
('NOTICE', '创意集市第二批摊位即将开放申请', '9 月 2 日 10:00 在小程序「办事大厅-特色服务」开放第二批摊位申请，名额 30 个先到先得。', '2026-08-29', 3),
('NOTICE', '现场服务终端使用指南', '本终端支持 AI 问答、办事指南查询、单号查进度、平台数据一览，无需登录，触摸即可操作。', '2026-08-28', 4),
('NEWS', '「码上办」上线首月成绩单', '上线首月累计受理服务申请破千，平均办结时长 26 小时，宿舍报修成最热服务。', '2026-08-27', 5),
('NOTICE', '学生事务中心周末延时服务', '为方便集市期间办事，事务中心本周六、周日延时开放至 20:00，在读证明、场地申请照常受理。', '2026-08-26', 6),
('NEWS', '小程序新版本上线：AI 助手会听话了', '校园 AI 助手新增按住说话与图文问答，课表、申请进度一句话查到。', '2026-08-25', 7),
('NOTICE', '校园网核心设备维护通知', '9 月 1 日 00:00-06:00 校园网升级维护，期间线上服务可能出现短时波动，请错峰办理。', '2026-08-24', 8);
