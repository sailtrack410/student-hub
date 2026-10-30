-- 全新库引导数据：V8 及后续迁移依赖校区、组织、角色先存在。
-- V15 也会 INSERT IGNORE 这些角色；本迁移在 V1 建表后、V8 之前执行，
-- 使生产库（不加载 db/devdata）也能完成 Flyway 初始化。使用 INSERT IGNORE 保证幂等。
INSERT IGNORE INTO sys_campus
    (id, campus_code, campus_name, status, created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (1, 'MAIN', '主校区', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT IGNORE INTO sys_organization
    (id, campus_id, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (10, 1, NULL, 'ROOT', '学校', 'CAMPUS', 0, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (11, 1, 10, 'LOGISTICS', '后勤保障处', 'DEPARTMENT', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (12, 1, 11, 'REPAIR_TEAM', '维修班组', 'TEAM', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT IGNORE INTO sys_role
    (id, role_code, role_name, role_type, status, created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (1, 'PLATFORM_ADMIN', '平台管理员', 'ADMIN', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (2, 'REQUESTER', '服务申请人', 'REQUESTER', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (3, 'WORKER', '服务人员', 'WORKER', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (4, 'DISPATCHER', '调度员', 'DISPATCHER', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);
