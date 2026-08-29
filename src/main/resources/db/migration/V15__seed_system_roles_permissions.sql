-- 系统角色/权限种子：正式环境也加载，保证管理台所需的角色与权限码存在（不依赖 devdata）。
-- 使用 INSERT IGNORE 保证重复执行幂等（devdata 可能已插入部分角色/权限/绑定）。

INSERT IGNORE INTO sys_role
    (id, role_code, role_name, role_type, status, created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (1, 'PLATFORM_ADMIN', '平台管理员', 'ADMIN', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (2, 'REQUESTER', '服务申请人', 'REQUESTER', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (3, 'WORKER', '服务人员', 'WORKER', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (4, 'DISPATCHER', '调度员', 'DISPATCHER', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT IGNORE INTO sys_permission
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
    (8, 'work-order:dispatch', '派发工单', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (23, 'ai:manage', 'AI 配置管理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, created_at, created_by)
VALUES
    (1101, 1, 1, CURRENT_TIMESTAMP, 0),
    (1102, 1, 2, CURRENT_TIMESTAMP, 0),
    (1103, 1, 3, CURRENT_TIMESTAMP, 0),
    (1104, 1, 4, CURRENT_TIMESTAMP, 0),
    (1105, 1, 5, CURRENT_TIMESTAMP, 0),
    (1106, 1, 6, CURRENT_TIMESTAMP, 0),
    (1107, 1, 7, CURRENT_TIMESTAMP, 0),
    (1108, 1, 8, CURRENT_TIMESTAMP, 0),
    (1109, 1, 23, CURRENT_TIMESTAMP, 0),
    (1241, 2, 4, CURRENT_TIMESTAMP, 0),
    (1242, 2, 5, CURRENT_TIMESTAMP, 0),
    (1341, 3, 4, CURRENT_TIMESTAMP, 0),
    (1342, 3, 5, CURRENT_TIMESTAMP, 0),
    (1343, 3, 6, CURRENT_TIMESTAMP, 0),
    (1344, 3, 7, CURRENT_TIMESTAMP, 0),
    (1441, 4, 4, CURRENT_TIMESTAMP, 0),
    (1442, 4, 5, CURRENT_TIMESTAMP, 0),
    (1443, 4, 6, CURRENT_TIMESTAMP, 0),
    (1444, 4, 7, CURRENT_TIMESTAMP, 0),
    (1445, 4, 8, CURRENT_TIMESTAMP, 0);