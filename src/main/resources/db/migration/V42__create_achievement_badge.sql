-- 勋章与等级体系（2026-10-17 定稿：docs/level-and-badge-design.md 19 枚清单 + 定稿方案 memory/community-perf-badge-final-plan）。
-- 勋章=字典/进度分离：achievement_badge 只存目录与规则（新增勋章插一行数据），
-- user_achievement_badge 存获得记录（(user_id,badge_code) 唯一，幂等授予），
-- 佩戴态存 sys_user.worn_badge_code（一列，卸下置 NULL）——社区批量组装零额外查询搭车。
-- 等级经验不落流水：每次评估从业务表现状重算（零侵入、天然幂等），本迁移不建经验表。
CREATE TABLE achievement_badge (
    id BIGINT NOT NULL,
    badge_code VARCHAR(64) NOT NULL COMMENT '勋章编码，前端素材 assets/badges/$code.png 按此匹配',
    badge_name VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL COMMENT '类别：COMMUNITY/SERVICE/WORKER/JW/GROWTH',
    rarity VARCHAR(16) NOT NULL DEFAULT 'COMMON' COMMENT '稀有度：COMMON/RARE/EPIC/LEGENDARY',
    description VARCHAR(255) NOT NULL COMMENT '解锁方式（通知文案使用）',
    metric_code VARCHAR(32) NULL COMMENT '关联指标：POST_COUNT/COMMENT_COUNT/POST_LIKES/REQUEST_COUNT/...；GROWTH 类为 LEVEL',
    metric_threshold INT NULL COMMENT '达成阈值（首帖类为 1）',
    reward_exp INT NOT NULL DEFAULT 0 COMMENT '奖励经验（成长类固定 0，防经验自循环）',
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_achievement_badge_code (badge_code)
);

CREATE TABLE user_achievement_badge (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    badge_code VARCHAR(64) NOT NULL,
    current_progress INT NOT NULL DEFAULT 0 COMMENT '最近一次评估的指标值（未达成也刷新，展示 x/threshold）',
    unlocked_at DATETIME NULL COMMENT '达成时间；NULL=未解锁（仅进度）',
    notified TINYINT NOT NULL DEFAULT 0 COMMENT '解锁通知已发标记（防重复通知）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_badge (user_id, badge_code),
    KEY idx_user_achievement_badge_user (user_id)
);

-- 佩戴列：一列即状态，社区作者勋章搭车（selectBatchIds 整行取出）
ALTER TABLE sys_user ADD COLUMN worn_badge_code VARCHAR(64) NULL COMMENT '佩戴勋章编码（卸下置 NULL，须已在 user_achievement_badge 解锁）';

-- 19 枚目录播种（docs/level-and-badge-design.md 第五节 + docs/badge-catalog.md 命名）
INSERT INTO achievement_badge (id, badge_code, badge_name, category, rarity, description, metric_code, metric_threshold, reward_exp, sort_order, created_by, updated_by) VALUES
(1,  'COMMUNITY_FIRST_POST',    '破冰首帖',   'COMMUNITY', 'COMMON',    '首次在校园社区发布帖子',   'POST_COUNT',          1,  20,  1, 0, 0),
(2,  'COMMUNITY_POST_10',       '社区常客',   'COMMUNITY', 'RARE',      '累计发布 10 篇帖子',       'POST_COUNT',          10, 60,  2, 0, 0),
(3,  'COMMUNITY_POST_50',       '社区栋梁',   'COMMUNITY', 'EPIC',      '累计发布 50 篇帖子',       'POST_COUNT',          50, 200, 3, 0, 0),
(4,  'COMMUNITY_COMMENT_20',    '妙语连珠',   'COMMUNITY', 'RARE',      '累计发表 20 条评论',       'COMMENT_COUNT',       20, 60,  4, 0, 0),
(5,  'COMMUNITY_LIKES_50',      '人气之星',   'COMMUNITY', 'EPIC',      '帖子累计收获 50 次点赞',   'POST_LIKES',          50, 100, 5, 0, 0),
(6,  'COMMUNITY_HOT_POST',      '热帖作者',   'COMMUNITY', 'RARE',      '单篇帖子收获 20 次点赞',   'POST_LIKES_SINGLE',   20, 50,  6, 0, 0),
(7,  'SERVICE_FIRST_REQUEST',   '初次办理',   'SERVICE',   'COMMON',    '首次提交校园服务申请',     'REQUEST_COUNT',       1,  20,  7, 0, 0),
(8,  'SERVICE_COMPLETED_5',     '办事达人',   'SERVICE',   'RARE',      '累计办结 5 件服务申请',    'REQUEST_COMPLETED',   5,  80,  8, 0, 0),
(9,  'SERVICE_EVALUATION_5',    '评价先锋',   'SERVICE',   'RARE',      '累计提交 5 次服务评价',    'EVALUATION_COUNT',    5,  60,  9, 0, 0),
(10, 'SERVICE_FIVE_STAR_10',    '五星好评官', 'SERVICE',   'EPIC',      '累计提交 10 次五星评价',   'EVALUATION_FIVE_STAR',10, 80,  10, 0, 0),
(11, 'WORKER_FIRST_ORDER',     '首单完成',   'WORKER',    'COMMON',    '完成第一个服务工单',       'WORKER_ORDER_COMPLETED', 1, 20, 11, 0, 0),
(12, 'WORKER_ORDER_20',        '服务之星',   'WORKER',    'EPIC',      '累计完成 20 个服务工单',   'WORKER_ORDER_COMPLETED', 20, 150, 12, 0, 0),
(13, 'JW_FIRST_AUTH',          '教务新手',   'JW',        'COMMON',    '首次完成教务系统认证',     'JW_AUTH',             1,  30,  13, 0, 0),
(14, 'SCHEDULE_FIRST_IMPORT',  '课表管家',   'JW',        'COMMON',    '首次导入个人课表',         'SCHEDULE_IMPORTED',   1,  20,  14, 0, 0),
(15, 'PROFILE_COMPLETE',       '资料完善',   'JW',        'COMMON',    '头像与手机号均已填写',     'PROFILE_COMPLETE',    1,  20,  15, 0, 0),
(16, 'LEVEL_3',                '初露锋芒',   'GROWTH',    'RARE',      '等级达到 3 级',            'LEVEL',               3,  0,   16, 0, 0),
(17, 'LEVEL_5',                '校园中坚',   'GROWTH',    'EPIC',      '等级达到 5 级',            'LEVEL',               5,  0,   17, 0, 0),
(18, 'LEVEL_7',                '校园之星',   'GROWTH',    'EPIC',      '等级达到 7 级',            'LEVEL',               7,  0,   18, 0, 0),
(19, 'LEVEL_9',                '校园传奇',   'GROWTH',    'LEGENDARY', '等级达到 9 级（封顶）',    'LEVEL',               9,  0,   19, 0, 0);
