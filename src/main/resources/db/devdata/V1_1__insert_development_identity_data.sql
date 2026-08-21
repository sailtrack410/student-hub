INSERT INTO sys_campus
    (id, campus_code, campus_name, status, created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (1, 'DEMO', '示例大学', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT INTO sys_organization
    (id, campus_id, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (10, 1, NULL, 'ROOT', '示例大学', 'CAMPUS', 0, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (11, 1, 10, 'LOGISTICS', '后勤保障处', 'DEPARTMENT', 10, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (12, 1, 11, 'REPAIR_TEAM', '维修班组', 'TEAM', 20, 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT INTO sys_role
    (id, role_code, role_name, role_type, status, created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (1, 'PLATFORM_ADMIN', '平台管理员', 'ADMIN', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (2, 'REQUESTER', '服务申请人', 'REQUESTER', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (3, 'WORKER', '服务人员', 'WORKER', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (4, 'DISPATCHER', '调度员', 'DISPATCHER', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT INTO sys_permission
    (id, permission_code, permission_name, permission_type, status, created_at, updated_at, created_by, updated_by,
     deleted, version)
VALUES
    (1, 'system:user:manage', '用户管理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (2, 'system:role:manage', '角色管理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (3, 'system:organization:manage', '组织管理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (4, 'service-request:create', '提交服务申请', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (5, 'service-request:view:self', '查看本人服务申请', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (6, 'work-order:view:assigned', '查看被指派工单', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (7, 'work-order:process', '处理工单', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (8, 'work-order:dispatch', '派发工单', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT INTO sys_role_permission (id, role_id, permission_id, created_at, created_by)
VALUES
    (101, 1, 1, CURRENT_TIMESTAMP, 0),
    (102, 1, 2, CURRENT_TIMESTAMP, 0),
    (103, 1, 3, CURRENT_TIMESTAMP, 0),
    (104, 1, 4, CURRENT_TIMESTAMP, 0),
    (105, 1, 5, CURRENT_TIMESTAMP, 0),
    (106, 1, 6, CURRENT_TIMESTAMP, 0),
    (107, 1, 7, CURRENT_TIMESTAMP, 0),
    (108, 1, 8, CURRENT_TIMESTAMP, 0),
    (201, 2, 4, CURRENT_TIMESTAMP, 0),
    (202, 2, 5, CURRENT_TIMESTAMP, 0),
    (301, 3, 4, CURRENT_TIMESTAMP, 0),
    (302, 3, 5, CURRENT_TIMESTAMP, 0),
    (303, 3, 6, CURRENT_TIMESTAMP, 0),
    (304, 3, 7, CURRENT_TIMESTAMP, 0),
    (401, 4, 4, CURRENT_TIMESTAMP, 0),
    (402, 4, 5, CURRENT_TIMESTAMP, 0),
    (403, 4, 6, CURRENT_TIMESTAMP, 0),
    (404, 4, 7, CURRENT_TIMESTAMP, 0),
    (405, 4, 8, CURRENT_TIMESTAMP, 0);

INSERT INTO sys_user
    (id, campus_id, default_organization_id, username, password_hash, display_name, status, created_at, updated_at,
     created_by, updated_by, deleted, version)
VALUES
    (1001, 1, 10, 'admin', '$2y$10$0A8Ea5.Rg.GmW7FXGTGlWONwJqaVE0IfNTma/k2t96C4rtY871dNy', '开发管理员', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1002, 1, 10, 'requester', '$2y$10$0A8Ea5.Rg.GmW7FXGTGlWONwJqaVE0IfNTma/k2t96C4rtY871dNy', '开发申请人', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1003, 1, 12, 'worker', '$2y$10$0A8Ea5.Rg.GmW7FXGTGlWONwJqaVE0IfNTma/k2t96C4rtY871dNy', '开发维修员', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1004, 1, 11, 'dispatcher', '$2y$10$0A8Ea5.Rg.GmW7FXGTGlWONwJqaVE0IfNTma/k2t96C4rtY871dNy', '开发调度员', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT INTO sys_user_organization (id, user_id, organization_id, primary_membership, created_at, created_by)
VALUES
    (10001, 1001, 10, 1, CURRENT_TIMESTAMP, 0),
    (10002, 1002, 10, 1, CURRENT_TIMESTAMP, 0),
    (10003, 1003, 12, 1, CURRENT_TIMESTAMP, 0),
    (10004, 1004, 11, 1, CURRENT_TIMESTAMP, 0);

INSERT INTO sys_user_role (id, user_id, role_id, organization_id, data_scope, created_at, created_by)
VALUES
    (20001, 1001, 1, 10, 'ALL', CURRENT_TIMESTAMP, 0),
    (20002, 1002, 2, 10, 'SELF', CURRENT_TIMESTAMP, 0),
    (20003, 1003, 3, 12, 'ASSIGNED', CURRENT_TIMESTAMP, 0),
    (20004, 1004, 4, 11, 'ORGANIZATION_AND_CHILDREN', CURRENT_TIMESTAMP, 0);
