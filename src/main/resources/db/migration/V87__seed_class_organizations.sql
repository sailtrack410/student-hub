-- V87：把 687 个班级补进组织树，挂在所属专业下（学校 → 学院 → 专业 → 班级）。
-- 班级校区取教务课表多数归属；3 个跨校区班按主校区（游仙）挂载，父专业为具体/跨校区均兼容。
-- 仅当对应校区与专业组织存在时插入；H2 测试库只有「主校区」，这些语句自然插入 0 行。

INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30001, c.id, 'CAMPUS', m.id, 'CLASS_0001', '人智2601', 'CLASS', 1, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '人工智能'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30002, c.id, 'CAMPUS', m.id, 'CLASS_0002', '人智2602', 'CLASS', 2, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '人工智能'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30003, c.id, 'CAMPUS', m.id, 'CLASS_0003', '人智2603', 'CLASS', 3, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '人工智能'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30004, c.id, 'CAMPUS', m.id, 'CLASS_0004', '人智2604', 'CLASS', 4, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '人工智能'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30005, c.id, 'CAMPUS', m.id, 'CLASS_0005', '电信2601', 'CLASS', 5, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子信息工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30006, c.id, 'CAMPUS', m.id, 'CLASS_0006', '计科2601', 'CLASS', 6, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30007, c.id, 'CAMPUS', m.id, 'CLASS_0007', '计科2602', 'CLASS', 7, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30008, c.id, 'CAMPUS', m.id, 'CLASS_0008', '计科2603', 'CLASS', 8, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30009, c.id, 'CAMPUS', m.id, 'CLASS_0009', '计科2604', 'CLASS', 9, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30010, c.id, 'CAMPUS', m.id, 'CLASS_0010', '计科2605', 'CLASS', 10, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30011, c.id, 'CAMPUS', m.id, 'CLASS_0011', '软工2601', 'CLASS', 11, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30012, c.id, 'CAMPUS', m.id, 'CLASS_0012', '土木2601', 'CLASS', 12, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '土木工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30013, c.id, 'CAMPUS', m.id, 'CLASS_0013', '地科2601', 'CLASS', 13, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '地理信息科学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30014, c.id, 'CAMPUS', m.id, 'CLASS_0014', '地科2602', 'CLASS', 14, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '地理信息科学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30015, c.id, 'CAMPUS', m.id, 'CLASS_0015', '工造2601', 'CLASS', 15, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程造价'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30016, c.id, 'CAMPUS', m.id, 'CLASS_0016', '能源2601', 'CLASS', 16, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新能源发电工程技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30017, c.id, 'CAMPUS', m.id, 'CLASS_0017', '能源2602', 'CLASS', 17, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新能源发电工程技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30018, c.id, 'CAMPUS', m.id, 'CLASS_0018', '无应2601', 'CLASS', 18, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '无人机系统应用技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30019, c.id, 'CAMPUS', m.id, 'CLASS_0019', '无应2602', 'CLASS', 19, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '无人机系统应用技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30020, c.id, 'CAMPUS', m.id, 'CLASS_0020', '无应2603', 'CLASS', 20, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '无人机系统应用技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30021, c.id, 'CAMPUS', m.id, 'CLASS_0021', '智制2601', 'CLASS', 21, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '智能制造工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30022, c.id, 'CAMPUS', m.id, 'CLASS_0022', '智制2602', 'CLASS', 22, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '智能制造工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30023, c.id, 'CAMPUS', m.id, 'CLASS_0023', '机工2601', 'CLASS', 23, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机器人工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30024, c.id, 'CAMPUS', m.id, 'CLASS_0024', '机自2601', 'CLASS', 24, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30025, c.id, 'CAMPUS', m.id, 'CLASS_0025', '机自2602', 'CLASS', 25, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30026, c.id, 'CAMPUS', m.id, 'CLASS_0026', '机自2603', 'CLASS', 26, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30027, c.id, 'CAMPUS', m.id, 'CLASS_0027', '机自2604', 'CLASS', 27, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30028, c.id, 'CAMPUS', m.id, 'CLASS_0028', '机自2605', 'CLASS', 28, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30029, c.id, 'CAMPUS', m.id, 'CLASS_0029', '机自2606', 'CLASS', 29, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30030, c.id, 'CAMPUS', m.id, 'CLASS_0030', '机自2607', 'CLASS', 30, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30031, c.id, 'CAMPUS', m.id, 'CLASS_0031', '机自2608', 'CLASS', 31, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30032, c.id, 'CAMPUS', m.id, 'CLASS_0032', '测绘2601', 'CLASS', 32, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '测绘工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30033, c.id, 'CAMPUS', m.id, 'CLASS_0033', '电气2601', 'CLASS', 33, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30034, c.id, 'CAMPUS', m.id, 'CLASS_0034', '电气2602', 'CLASS', 34, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30035, c.id, 'CAMPUS', m.id, 'CLASS_0035', '电气2603', 'CLASS', 35, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30036, c.id, 'CAMPUS', m.id, 'CLASS_0036', '电气2604', 'CLASS', 36, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30037, c.id, 'CAMPUS', m.id, 'CLASS_0037', '电气2605', 'CLASS', 37, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30038, c.id, 'CAMPUS', m.id, 'CLASS_0038', '电气2606', 'CLASS', 38, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30039, c.id, 'CAMPUS', m.id, 'CLASS_0039', '电气2607', 'CLASS', 39, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30040, c.id, 'CAMPUS', m.id, 'CLASS_0040', '电气2608', 'CLASS', 40, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30041, c.id, 'CAMPUS', m.id, 'CLASS_0041', '电气2609', 'CLASS', 41, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30042, c.id, 'CAMPUS', m.id, 'CLASS_0042', '电气2610', 'CLASS', 42, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30043, c.id, 'CAMPUS', m.id, 'CLASS_0043', '电气2611', 'CLASS', 43, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30044, c.id, 'CAMPUS', m.id, 'CLASS_0044', '电气2612', 'CLASS', 44, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30045, c.id, 'CAMPUS', m.id, 'CLASS_0045', '能环2601', 'CLASS', 45, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '能源与环境系统工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30046, c.id, 'CAMPUS', m.id, 'CLASS_0046', '自动2601', 'CLASS', 46, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30047, c.id, 'CAMPUS', m.id, 'CLASS_0047', '自动2602', 'CLASS', 47, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30048, c.id, 'CAMPUS', m.id, 'CLASS_0048', '自动2603', 'CLASS', 48, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30049, c.id, 'CAMPUS', m.id, 'CLASS_0049', '休体2601', 'CLASS', 49, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '休闲体育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30050, c.id, 'CAMPUS', m.id, 'CLASS_0050', '休体2602', 'CLASS', 50, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '休闲体育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30051, c.id, 'CAMPUS', m.id, 'CLASS_0051', '体教2601', 'CLASS', 51, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30052, c.id, 'CAMPUS', m.id, 'CLASS_0052', '体教2602', 'CLASS', 52, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30053, c.id, 'CAMPUS', m.id, 'CLASS_0053', '体教2603', 'CLASS', 53, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30054, c.id, 'CAMPUS', m.id, 'CLASS_0054', '体教2604', 'CLASS', 54, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30055, c.id, 'CAMPUS', m.id, 'CLASS_0055', '体教2605', 'CLASS', 55, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30056, c.id, 'CAMPUS', m.id, 'CLASS_0056', '体教2606', 'CLASS', 56, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30057, c.id, 'CAMPUS', m.id, 'CLASS_0057', '体教2607', 'CLASS', 57, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30058, c.id, 'CAMPUS', m.id, 'CLASS_0058', '体教2608', 'CLASS', 58, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30059, c.id, 'CAMPUS', m.id, 'CLASS_0059', '体教2609', 'CLASS', 59, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30060, c.id, 'CAMPUS', m.id, 'CLASS_0060', '体教2610', 'CLASS', 60, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30061, c.id, 'CAMPUS', m.id, 'CLASS_0061', '体教2611', 'CLASS', 61, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30062, c.id, 'CAMPUS', m.id, 'CLASS_0062', '体教2612', 'CLASS', 62, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30063, c.id, 'CAMPUS', m.id, 'CLASS_0063', '体教2613', 'CLASS', 63, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30064, c.id, 'CAMPUS', m.id, 'CLASS_0064', '体教2614', 'CLASS', 64, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30065, c.id, 'CAMPUS', m.id, 'CLASS_0065', '体教2615', 'CLASS', 65, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30066, c.id, 'CAMPUS', m.id, 'CLASS_0066', '体教2616', 'CLASS', 66, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30067, c.id, 'CAMPUS', m.id, 'CLASS_0067', '体教2617', 'CLASS', 67, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30068, c.id, 'CAMPUS', m.id, 'CLASS_0068', '体教2618', 'CLASS', 68, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30069, c.id, 'CAMPUS', m.id, 'CLASS_0069', '体教2619', 'CLASS', 69, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30070, c.id, 'CAMPUS', m.id, 'CLASS_0070', '体教2620', 'CLASS', 70, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30071, c.id, 'CAMPUS', m.id, 'CLASS_0071', '体教2621', 'CLASS', 71, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30072, c.id, 'CAMPUS', m.id, 'CLASS_0072', '体教2622', 'CLASS', 72, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30073, c.id, 'CAMPUS', m.id, 'CLASS_0073', '体教2623', 'CLASS', 73, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30074, c.id, 'CAMPUS', m.id, 'CLASS_0074', '体教2624', 'CLASS', 74, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30075, c.id, 'CAMPUS', m.id, 'CLASS_0075', '体教2625', 'CLASS', 75, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30076, c.id, 'CAMPUS', m.id, 'CLASS_0076', '体教2626', 'CLASS', 76, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30077, c.id, 'CAMPUS', m.id, 'CLASS_0077', '体教2627', 'CLASS', 77, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30078, c.id, 'CAMPUS', m.id, 'CLASS_0078', '体教2628', 'CLASS', 78, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30079, c.id, 'CAMPUS', m.id, 'CLASS_0079', '体教2629', 'CLASS', 79, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30080, c.id, 'CAMPUS', m.id, 'CLASS_0080', '体教2630', 'CLASS', 80, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30081, c.id, 'CAMPUS', m.id, 'CLASS_0081', '体教2631', 'CLASS', 81, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30082, c.id, 'CAMPUS', m.id, 'CLASS_0082', '体教2632', 'CLASS', 82, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30083, c.id, 'CAMPUS', m.id, 'CLASS_0083', '健服2601', 'CLASS', 83, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '健康服务与管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30084, c.id, 'CAMPUS', m.id, 'CLASS_0084', '健服2602', 'CLASS', 84, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '健康服务与管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30085, c.id, 'CAMPUS', m.id, 'CLASS_0085', '健服2603', 'CLASS', 85, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '健康服务与管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30086, c.id, 'CAMPUS', m.id, 'CLASS_0086', '健服2604', 'CLASS', 86, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '健康服务与管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30087, c.id, 'CAMPUS', m.id, 'CLASS_0087', '学教2601', 'CLASS', 87, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30088, c.id, 'CAMPUS', m.id, 'CLASS_0088', '学教2602', 'CLASS', 88, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30089, c.id, 'CAMPUS', m.id, 'CLASS_0089', '学教2603', 'CLASS', 89, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30090, c.id, 'CAMPUS', m.id, 'CLASS_0090', '学教2604', 'CLASS', 90, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30091, c.id, 'CAMPUS', m.id, 'CLASS_0091', '数应2601', 'CLASS', 91, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数学与应用数学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30092, c.id, 'CAMPUS', m.id, 'CLASS_0092', '数应2602', 'CLASS', 92, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数学与应用数学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30093, c.id, 'CAMPUS', m.id, 'CLASS_0093', '英语2601', 'CLASS', 93, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30094, c.id, 'CAMPUS', m.id, 'CLASS_0094', '英语2602', 'CLASS', 94, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30095, c.id, 'CAMPUS', m.id, 'CLASS_0095', '英语2603', 'CLASS', 95, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30096, c.id, 'CAMPUS', m.id, 'CLASS_0096', '运训Z2601', 'CLASS', 96, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '运动训练Z'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30097, c.id, 'CAMPUS', m.id, 'CLASS_0097', '商数Z2601', 'CLASS', 97, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '商务数据分析与应用Z'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30098, c.id, 'CAMPUS', m.id, 'CLASS_0098', '大会Z2601', 'CLASS', 98, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '大数据与会计Z'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30099, c.id, 'CAMPUS', m.id, 'CLASS_0099', '工商2601', 'CLASS', 99, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30100, c.id, 'CAMPUS', m.id, 'CLASS_0100', '工商2602', 'CLASS', 100, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30101, c.id, 'CAMPUS', m.id, 'CLASS_0101', '物流2601', 'CLASS', 101, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '物流工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30102, c.id, 'CAMPUS', m.id, 'CLASS_0102', '电商2601', 'CLASS', 102, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子商务'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30103, c.id, 'CAMPUS', m.id, 'CLASS_0103', '电商2602', 'CLASS', 103, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子商务'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30104, c.id, 'CAMPUS', m.id, 'CLASS_0104', '财管2601', 'CLASS', 104, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30105, c.id, 'CAMPUS', m.id, 'CLASS_0105', '财管2602', 'CLASS', 105, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30106, c.id, 'CAMPUS', m.id, 'CLASS_0106', '财管2603', 'CLASS', 106, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30107, c.id, 'CAMPUS', m.id, 'CLASS_0107', '财管2604', 'CLASS', 107, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30108, c.id, 'CAMPUS', m.id, 'CLASS_0108', '财管2605', 'CLASS', 108, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30109, c.id, 'CAMPUS', m.id, 'CLASS_0109', '财管2606', 'CLASS', 109, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30110, c.id, 'CAMPUS', m.id, 'CLASS_0110', '财管2607', 'CLASS', 110, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30111, c.id, 'CAMPUS', m.id, 'CLASS_0111', '财管2608', 'CLASS', 111, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30112, c.id, 'CAMPUS', m.id, 'CLASS_0112', '财管2609', 'CLASS', 112, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30113, c.id, 'CAMPUS', m.id, 'CLASS_0113', '财管2610', 'CLASS', 113, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30114, c.id, 'CAMPUS', m.id, 'CLASS_0114', '财管2611', 'CLASS', 114, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30115, c.id, 'CAMPUS', m.id, 'CLASS_0115', '财管2612', 'CLASS', 115, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30116, c.id, 'CAMPUS', m.id, 'CLASS_0116', '财管2613', 'CLASS', 116, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30117, c.id, 'CAMPUS', m.id, 'CLASS_0117', '财管2614', 'CLASS', 117, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30118, c.id, 'CAMPUS', m.id, 'CLASS_0118', '财管2615', 'CLASS', 118, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30119, c.id, 'CAMPUS', m.id, 'CLASS_0119', '财管2616', 'CLASS', 119, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30120, c.id, 'CAMPUS', m.id, 'CLASS_0120', '财管2617', 'CLASS', 120, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30121, c.id, 'CAMPUS', m.id, 'CLASS_0121', '金融2601', 'CLASS', 121, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '金融工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30122, c.id, 'CAMPUS', m.id, 'CLASS_0122', '产设2601', 'CLASS', 122, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '产品设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30123, c.id, 'CAMPUS', m.id, 'CLASS_0123', '产设2602', 'CLASS', 123, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '产品设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30124, c.id, 'CAMPUS', m.id, 'CLASS_0124', '产设2603', 'CLASS', 124, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '产品设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30125, c.id, 'CAMPUS', m.id, 'CLASS_0125', '产设2604', 'CLASS', 125, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '产品设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30126, c.id, 'CAMPUS', m.id, 'CLASS_0126', '城规2601', 'CLASS', 126, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '城乡规划'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30127, c.id, 'CAMPUS', m.id, 'CLASS_0127', '数媒2601', 'CLASS', 127, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30128, c.id, 'CAMPUS', m.id, 'CLASS_0128', '数媒2602', 'CLASS', 128, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30129, c.id, 'CAMPUS', m.id, 'CLASS_0129', '新媒2601', 'CLASS', 129, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30130, c.id, 'CAMPUS', m.id, 'CLASS_0130', '新媒2602', 'CLASS', 130, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30131, c.id, 'CAMPUS', m.id, 'CLASS_0131', '新媒2603', 'CLASS', 131, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30132, c.id, 'CAMPUS', m.id, 'CLASS_0132', '新媒2604', 'CLASS', 132, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30133, c.id, 'CAMPUS', m.id, 'CLASS_0133', '新媒2605', 'CLASS', 133, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30134, c.id, 'CAMPUS', m.id, 'CLASS_0134', '新媒2606', 'CLASS', 134, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30135, c.id, 'CAMPUS', m.id, 'CLASS_0135', '智创2601', 'CLASS', 135, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '智能工程与创意设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30136, c.id, 'CAMPUS', m.id, 'CLASS_0136', '艺设2601', 'CLASS', 136, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '艺术设计学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30137, c.id, 'CAMPUS', m.id, 'CLASS_0137', '风园2601', 'CLASS', 137, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '风景园林'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30138, c.id, 'CAMPUS', m.id, 'CLASS_0138', '电信2501', 'CLASS', 138, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子信息工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30139, c.id, 'CAMPUS', m.id, 'CLASS_0139', '电信2502', 'CLASS', 139, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子信息工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30140, c.id, 'CAMPUS', m.id, 'CLASS_0140', '电信2503', 'CLASS', 140, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子信息工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30141, c.id, 'CAMPUS', m.id, 'CLASS_0141', '计科2501', 'CLASS', 141, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30142, c.id, 'CAMPUS', m.id, 'CLASS_0142', '计科2502', 'CLASS', 142, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30143, c.id, 'CAMPUS', m.id, 'CLASS_0143', '计科2503', 'CLASS', 143, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30144, c.id, 'CAMPUS', m.id, 'CLASS_0144', '计科2504', 'CLASS', 144, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30145, c.id, 'CAMPUS', m.id, 'CLASS_0145', '计科2505', 'CLASS', 145, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30146, c.id, 'CAMPUS', m.id, 'CLASS_0146', '计科2506', 'CLASS', 146, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30147, c.id, 'CAMPUS', m.id, 'CLASS_0147', '计科2507', 'CLASS', 147, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30148, c.id, 'CAMPUS', m.id, 'CLASS_0148', '计科2508', 'CLASS', 148, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30149, c.id, 'CAMPUS', m.id, 'CLASS_0149', '软工2501', 'CLASS', 149, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30150, c.id, 'CAMPUS', m.id, 'CLASS_0150', '软工2502', 'CLASS', 150, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30151, c.id, 'CAMPUS', m.id, 'CLASS_0151', '土木2501', 'CLASS', 151, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '土木工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30152, c.id, 'CAMPUS', m.id, 'CLASS_0152', '地科2501', 'CLASS', 152, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '地理信息科学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30153, c.id, 'CAMPUS', m.id, 'CLASS_0153', '地科2502', 'CLASS', 153, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '地理信息科学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30154, c.id, 'CAMPUS', m.id, 'CLASS_0154', '地科2503', 'CLASS', 154, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '地理信息科学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30155, c.id, 'CAMPUS', m.id, 'CLASS_0155', '工程2501', 'CLASS', 155, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30156, c.id, 'CAMPUS', m.id, 'CLASS_0156', '工造2501', 'CLASS', 156, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程造价'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30157, c.id, 'CAMPUS', m.id, 'CLASS_0157', '智制2501', 'CLASS', 157, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '智能制造工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30158, c.id, 'CAMPUS', m.id, 'CLASS_0158', '智制2502', 'CLASS', 158, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '智能制造工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30159, c.id, 'CAMPUS', m.id, 'CLASS_0159', '智制2503', 'CLASS', 159, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '智能制造工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30160, c.id, 'CAMPUS', m.id, 'CLASS_0160', '智制2504', 'CLASS', 160, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '智能制造工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30161, c.id, 'CAMPUS', m.id, 'CLASS_0161', '机自2501', 'CLASS', 161, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30162, c.id, 'CAMPUS', m.id, 'CLASS_0162', '机自2502', 'CLASS', 162, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30163, c.id, 'CAMPUS', m.id, 'CLASS_0163', '机自2503', 'CLASS', 163, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30164, c.id, 'CAMPUS', m.id, 'CLASS_0164', '机自2504', 'CLASS', 164, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30165, c.id, 'CAMPUS', m.id, 'CLASS_0165', '机自2505', 'CLASS', 165, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30166, c.id, 'CAMPUS', m.id, 'CLASS_0166', '机自2506', 'CLASS', 166, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30167, c.id, 'CAMPUS', m.id, 'CLASS_0167', '机自2507', 'CLASS', 167, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30168, c.id, 'CAMPUS', m.id, 'CLASS_0168', '机自2508', 'CLASS', 168, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30169, c.id, 'CAMPUS', m.id, 'CLASS_0169', '测绘2501', 'CLASS', 169, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '测绘工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30170, c.id, 'CAMPUS', m.id, 'CLASS_0170', '电气2501', 'CLASS', 170, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30171, c.id, 'CAMPUS', m.id, 'CLASS_0171', '电气2502', 'CLASS', 171, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30172, c.id, 'CAMPUS', m.id, 'CLASS_0172', '电气2503', 'CLASS', 172, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30173, c.id, 'CAMPUS', m.id, 'CLASS_0173', '电气2504', 'CLASS', 173, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30174, c.id, 'CAMPUS', m.id, 'CLASS_0174', '电气2505', 'CLASS', 174, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30175, c.id, 'CAMPUS', m.id, 'CLASS_0175', '电气2506', 'CLASS', 175, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30176, c.id, 'CAMPUS', m.id, 'CLASS_0176', '电气2507', 'CLASS', 176, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30177, c.id, 'CAMPUS', m.id, 'CLASS_0177', '电气2508', 'CLASS', 177, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30178, c.id, 'CAMPUS', m.id, 'CLASS_0178', '电气2509', 'CLASS', 178, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30179, c.id, 'CAMPUS', m.id, 'CLASS_0179', '电气2510', 'CLASS', 179, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30180, c.id, 'CAMPUS', m.id, 'CLASS_0180', '电气2511', 'CLASS', 180, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30181, c.id, 'CAMPUS', m.id, 'CLASS_0181', '电气2512', 'CLASS', 181, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30182, c.id, 'CAMPUS', m.id, 'CLASS_0182', '电气2513', 'CLASS', 182, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30183, c.id, 'CAMPUS', m.id, 'CLASS_0183', '电气2514', 'CLASS', 183, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30184, c.id, 'CAMPUS', m.id, 'CLASS_0184', '电气2515', 'CLASS', 184, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30185, c.id, 'CAMPUS', m.id, 'CLASS_0185', '电气2516', 'CLASS', 185, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30186, c.id, 'CAMPUS', m.id, 'CLASS_0186', '能环2501', 'CLASS', 186, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '能源与环境系统工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30187, c.id, 'CAMPUS', m.id, 'CLASS_0187', '自动2501', 'CLASS', 187, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30188, c.id, 'CAMPUS', m.id, 'CLASS_0188', '自动2502', 'CLASS', 188, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30189, c.id, 'CAMPUS', m.id, 'CLASS_0189', '自动2503', 'CLASS', 189, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30190, c.id, 'CAMPUS', m.id, 'CLASS_0190', '自动2504', 'CLASS', 190, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30191, c.id, 'CAMPUS', m.id, 'CLASS_0191', '休体2501', 'CLASS', 191, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '休闲体育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30192, c.id, 'CAMPUS', m.id, 'CLASS_0192', '体教2501', 'CLASS', 192, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30193, c.id, 'CAMPUS', m.id, 'CLASS_0193', '体教2502', 'CLASS', 193, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30194, c.id, 'CAMPUS', m.id, 'CLASS_0194', '体教2503', 'CLASS', 194, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30195, c.id, 'CAMPUS', m.id, 'CLASS_0195', '体教2504', 'CLASS', 195, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30196, c.id, 'CAMPUS', m.id, 'CLASS_0196', '体教2505', 'CLASS', 196, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30197, c.id, 'CAMPUS', m.id, 'CLASS_0197', '体教2506', 'CLASS', 197, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30198, c.id, 'CAMPUS', m.id, 'CLASS_0198', '体教2507', 'CLASS', 198, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30199, c.id, 'CAMPUS', m.id, 'CLASS_0199', '体教2508', 'CLASS', 199, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30200, c.id, 'CAMPUS', m.id, 'CLASS_0200', '体教2509', 'CLASS', 200, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30201, c.id, 'CAMPUS', m.id, 'CLASS_0201', '体教2510', 'CLASS', 201, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30202, c.id, 'CAMPUS', m.id, 'CLASS_0202', '体教2511', 'CLASS', 202, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30203, c.id, 'CAMPUS', m.id, 'CLASS_0203', '体教2512', 'CLASS', 203, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30204, c.id, 'CAMPUS', m.id, 'CLASS_0204', '体教2513', 'CLASS', 204, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30205, c.id, 'CAMPUS', m.id, 'CLASS_0205', '体教2514', 'CLASS', 205, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30206, c.id, 'CAMPUS', m.id, 'CLASS_0206', '体教2515', 'CLASS', 206, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30207, c.id, 'CAMPUS', m.id, 'CLASS_0207', '体教2516', 'CLASS', 207, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30208, c.id, 'CAMPUS', m.id, 'CLASS_0208', '体教2517', 'CLASS', 208, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30209, c.id, 'CAMPUS', m.id, 'CLASS_0209', '体教2518', 'CLASS', 209, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30210, c.id, 'CAMPUS', m.id, 'CLASS_0210', '体教2519', 'CLASS', 210, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30211, c.id, 'CAMPUS', m.id, 'CLASS_0211', '体教2520', 'CLASS', 211, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30212, c.id, 'CAMPUS', m.id, 'CLASS_0212', '体教2521', 'CLASS', 212, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30213, c.id, 'CAMPUS', m.id, 'CLASS_0213', '体教2522', 'CLASS', 213, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30214, c.id, 'CAMPUS', m.id, 'CLASS_0214', '体教2523', 'CLASS', 214, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30215, c.id, 'CAMPUS', m.id, 'CLASS_0215', '体教2524', 'CLASS', 215, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30216, c.id, 'CAMPUS', m.id, 'CLASS_0216', '体教2525', 'CLASS', 216, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30217, c.id, 'CAMPUS', m.id, 'CLASS_0217', '体教2526', 'CLASS', 217, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30218, c.id, 'CAMPUS', m.id, 'CLASS_0218', '体教2527', 'CLASS', 218, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30219, c.id, 'CAMPUS', m.id, 'CLASS_0219', '健服2501', 'CLASS', 219, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '健康服务与管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30220, c.id, 'CAMPUS', m.id, 'CLASS_0220', '健服2502', 'CLASS', 220, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '健康服务与管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30221, c.id, 'CAMPUS', m.id, 'CLASS_0221', '健服2503', 'CLASS', 221, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '健康服务与管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30222, c.id, 'CAMPUS', m.id, 'CLASS_0222', '健服2504', 'CLASS', 222, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '健康服务与管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30223, c.id, 'CAMPUS', m.id, 'CLASS_0223', '学教2501', 'CLASS', 223, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30224, c.id, 'CAMPUS', m.id, 'CLASS_0224', '学教2502', 'CLASS', 224, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30225, c.id, 'CAMPUS', m.id, 'CLASS_0225', '学教2503', 'CLASS', 225, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30226, c.id, 'CAMPUS', m.id, 'CLASS_0226', '学教2504', 'CLASS', 226, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30227, c.id, 'CAMPUS', m.id, 'CLASS_0227', '学教2505', 'CLASS', 227, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30228, c.id, 'CAMPUS', m.id, 'CLASS_0228', '数应2501', 'CLASS', 228, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数学与应用数学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30229, c.id, 'CAMPUS', m.id, 'CLASS_0229', '数应2502', 'CLASS', 229, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数学与应用数学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30230, c.id, 'CAMPUS', m.id, 'CLASS_0230', '英语2501', 'CLASS', 230, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30231, c.id, 'CAMPUS', m.id, 'CLASS_0231', '英语2502', 'CLASS', 231, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30232, c.id, 'CAMPUS', m.id, 'CLASS_0232', '英语2503', 'CLASS', 232, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30233, c.id, 'CAMPUS', m.id, 'CLASS_0233', '英语2504', 'CLASS', 233, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30234, c.id, 'CAMPUS', m.id, 'CLASS_0234', '运训Z2501', 'CLASS', 234, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '运动训练Z'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30235, c.id, 'CAMPUS', m.id, 'CLASS_0235', '运训Z2502', 'CLASS', 235, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '运动训练Z'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30236, c.id, 'CAMPUS', m.id, 'CLASS_0236', '商数Z2501', 'CLASS', 236, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '商务数据分析与应用Z'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30237, c.id, 'CAMPUS', m.id, 'CLASS_0237', '商数Z2502', 'CLASS', 237, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '商务数据分析与应用Z'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30238, c.id, 'CAMPUS', m.id, 'CLASS_0238', '工商2501', 'CLASS', 238, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30239, c.id, 'CAMPUS', m.id, 'CLASS_0239', '工商2502', 'CLASS', 239, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30240, c.id, 'CAMPUS', m.id, 'CLASS_0240', '工商2503', 'CLASS', 240, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30241, c.id, 'CAMPUS', m.id, 'CLASS_0241', '工商2504', 'CLASS', 241, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30242, c.id, 'CAMPUS', m.id, 'CLASS_0242', '工商2505', 'CLASS', 242, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30243, c.id, 'CAMPUS', m.id, 'CLASS_0243', '工商2506', 'CLASS', 243, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30244, c.id, 'CAMPUS', m.id, 'CLASS_0244', '物流2501', 'CLASS', 244, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '物流工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30245, c.id, 'CAMPUS', m.id, 'CLASS_0245', '电商2501', 'CLASS', 245, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子商务'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30246, c.id, 'CAMPUS', m.id, 'CLASS_0246', '财管2501', 'CLASS', 246, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30247, c.id, 'CAMPUS', m.id, 'CLASS_0247', '财管2502', 'CLASS', 247, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30248, c.id, 'CAMPUS', m.id, 'CLASS_0248', '财管2503', 'CLASS', 248, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30249, c.id, 'CAMPUS', m.id, 'CLASS_0249', '财管2504', 'CLASS', 249, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30250, c.id, 'CAMPUS', m.id, 'CLASS_0250', '财管2505', 'CLASS', 250, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30251, c.id, 'CAMPUS', m.id, 'CLASS_0251', '财管2506', 'CLASS', 251, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30252, c.id, 'CAMPUS', m.id, 'CLASS_0252', '财管2507', 'CLASS', 252, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30253, c.id, 'CAMPUS', m.id, 'CLASS_0253', '财管2508', 'CLASS', 253, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30254, c.id, 'CAMPUS', m.id, 'CLASS_0254', '财管2509', 'CLASS', 254, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30255, c.id, 'CAMPUS', m.id, 'CLASS_0255', '财管2510', 'CLASS', 255, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30256, c.id, 'CAMPUS', m.id, 'CLASS_0256', '财管2511', 'CLASS', 256, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30257, c.id, 'CAMPUS', m.id, 'CLASS_0257', '财管2512', 'CLASS', 257, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30258, c.id, 'CAMPUS', m.id, 'CLASS_0258', '财管2513', 'CLASS', 258, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30259, c.id, 'CAMPUS', m.id, 'CLASS_0259', '财管2514', 'CLASS', 259, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30260, c.id, 'CAMPUS', m.id, 'CLASS_0260', '财管2515', 'CLASS', 260, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30261, c.id, 'CAMPUS', m.id, 'CLASS_0261', '财管2516', 'CLASS', 261, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30262, c.id, 'CAMPUS', m.id, 'CLASS_0262', '金融2501', 'CLASS', 262, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '金融工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30263, c.id, 'CAMPUS', m.id, 'CLASS_0263', '金科2501', 'CLASS', 263, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '金融科技'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30264, c.id, 'CAMPUS', m.id, 'CLASS_0264', '产设2501', 'CLASS', 264, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '产品设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30265, c.id, 'CAMPUS', m.id, 'CLASS_0265', '产设2502', 'CLASS', 265, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '产品设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30266, c.id, 'CAMPUS', m.id, 'CLASS_0266', '产设2503', 'CLASS', 266, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '产品设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30267, c.id, 'CAMPUS', m.id, 'CLASS_0267', '城规2501', 'CLASS', 267, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '城乡规划'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30268, c.id, 'CAMPUS', m.id, 'CLASS_0268', '数媒2501', 'CLASS', 268, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30269, c.id, 'CAMPUS', m.id, 'CLASS_0269', '数媒2502', 'CLASS', 269, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30270, c.id, 'CAMPUS', m.id, 'CLASS_0270', '数媒2503', 'CLASS', 270, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30271, c.id, 'CAMPUS', m.id, 'CLASS_0271', '数媒2504', 'CLASS', 271, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30272, c.id, 'CAMPUS', m.id, 'CLASS_0272', '新媒2501', 'CLASS', 272, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30273, c.id, 'CAMPUS', m.id, 'CLASS_0273', '新媒2502', 'CLASS', 273, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30274, c.id, 'CAMPUS', m.id, 'CLASS_0274', '新媒2503', 'CLASS', 274, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30275, c.id, 'CAMPUS', m.id, 'CLASS_0275', '新媒2504', 'CLASS', 275, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30276, c.id, 'CAMPUS', m.id, 'CLASS_0276', '新媒2505', 'CLASS', 276, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30277, c.id, 'CAMPUS', m.id, 'CLASS_0277', '智创2501', 'CLASS', 277, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '智能工程与创意设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30278, c.id, 'CAMPUS', m.id, 'CLASS_0278', '艺设2501', 'CLASS', 278, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '艺术设计学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30279, c.id, 'CAMPUS', m.id, 'CLASS_0279', '艺设2502', 'CLASS', 279, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '艺术设计学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30280, c.id, 'CAMPUS', m.id, 'CLASS_0280', '风园2501', 'CLASS', 280, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '风景园林'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30281, c.id, 'CAMPUS', m.id, 'CLASS_0281', '物联B2401', 'CLASS', 281, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '物联网工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30282, c.id, 'CAMPUS', m.id, 'CLASS_0282', '电信2401', 'CLASS', 282, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子信息工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30283, c.id, 'CAMPUS', m.id, 'CLASS_0283', '电信2402', 'CLASS', 283, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子信息工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30284, c.id, 'CAMPUS', m.id, 'CLASS_0284', '电信2403', 'CLASS', 284, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子信息工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30285, c.id, 'CAMPUS', m.id, 'CLASS_0285', '电信B2404', 'CLASS', 285, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子信息工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30286, c.id, 'CAMPUS', m.id, 'CLASS_0286', '电信B2405', 'CLASS', 286, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子信息工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30287, c.id, 'CAMPUS', m.id, 'CLASS_0287', '计科2401', 'CLASS', 287, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30288, c.id, 'CAMPUS', m.id, 'CLASS_0288', '计科2402', 'CLASS', 288, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30289, c.id, 'CAMPUS', m.id, 'CLASS_0289', '计科2403', 'CLASS', 289, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30290, c.id, 'CAMPUS', m.id, 'CLASS_0290', '计科2404', 'CLASS', 290, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30291, c.id, 'CAMPUS', m.id, 'CLASS_0291', '计科2405', 'CLASS', 291, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30292, c.id, 'CAMPUS', m.id, 'CLASS_0292', '计科2406', 'CLASS', 292, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30293, c.id, 'CAMPUS', m.id, 'CLASS_0293', '计科2407', 'CLASS', 293, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30294, c.id, 'CAMPUS', m.id, 'CLASS_0294', '计科2408', 'CLASS', 294, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30295, c.id, 'CAMPUS', m.id, 'CLASS_0295', '计科2409', 'CLASS', 295, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30296, c.id, 'CAMPUS', m.id, 'CLASS_0296', '计科2410', 'CLASS', 296, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30297, c.id, 'CAMPUS', m.id, 'CLASS_0297', '计科2411', 'CLASS', 297, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30298, c.id, 'CAMPUS', m.id, 'CLASS_0298', '计科B2412', 'CLASS', 298, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30299, c.id, 'CAMPUS', m.id, 'CLASS_0299', '计科B2413', 'CLASS', 299, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30300, c.id, 'CAMPUS', m.id, 'CLASS_0300', '计科B2414', 'CLASS', 300, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30301, c.id, 'CAMPUS', m.id, 'CLASS_0301', '计科B2415', 'CLASS', 301, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30302, c.id, 'CAMPUS', m.id, 'CLASS_0302', '计科B2416', 'CLASS', 302, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30303, c.id, 'CAMPUS', m.id, 'CLASS_0303', '计科B2417', 'CLASS', 303, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30304, c.id, 'CAMPUS', m.id, 'CLASS_0304', '计科B2418', 'CLASS', 304, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30305, c.id, 'CAMPUS', m.id, 'CLASS_0305', '计科B2419', 'CLASS', 305, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30306, c.id, 'CAMPUS', m.id, 'CLASS_0306', '软工2401', 'CLASS', 306, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30307, c.id, 'CAMPUS', m.id, 'CLASS_0307', '软工2402', 'CLASS', 307, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30308, c.id, 'CAMPUS', m.id, 'CLASS_0308', '软工2403', 'CLASS', 308, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30309, c.id, 'CAMPUS', m.id, 'CLASS_0309', '软工2404', 'CLASS', 309, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30310, c.id, 'CAMPUS', m.id, 'CLASS_0310', '软工2405', 'CLASS', 310, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30311, c.id, 'CAMPUS', m.id, 'CLASS_0311', '软工2406', 'CLASS', 311, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30312, c.id, 'CAMPUS', m.id, 'CLASS_0312', '软工B2407', 'CLASS', 312, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30313, c.id, 'CAMPUS', m.id, 'CLASS_0313', '软工B2408', 'CLASS', 313, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30314, c.id, 'CAMPUS', m.id, 'CLASS_0314', '软工B2409', 'CLASS', 314, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30315, c.id, 'CAMPUS', m.id, 'CLASS_0315', '通信B2401', 'CLASS', 315, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '通信工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30316, c.id, 'CAMPUS', m.id, 'CLASS_0316', '交通B2401', 'CLASS', 316, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '交通工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30317, c.id, 'CAMPUS', m.id, 'CLASS_0317', '交通B2402', 'CLASS', 317, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '交通工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30318, c.id, 'CAMPUS', m.id, 'CLASS_0318', '交通B2403', 'CLASS', 318, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '交通工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30319, c.id, 'CAMPUS', m.id, 'CLASS_0319', '交通B2404', 'CLASS', 319, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '交通工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30320, c.id, 'CAMPUS', m.id, 'CLASS_0320', '交通B2405', 'CLASS', 320, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '交通工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30321, c.id, 'CAMPUS', m.id, 'CLASS_0321', '土木2401', 'CLASS', 321, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '土木工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30322, c.id, 'CAMPUS', m.id, 'CLASS_0322', '土木B2402', 'CLASS', 322, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '土木工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30323, c.id, 'CAMPUS', m.id, 'CLASS_0323', '土木B2403', 'CLASS', 323, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '土木工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30324, c.id, 'CAMPUS', m.id, 'CLASS_0324', '地科2401', 'CLASS', 324, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '地理信息科学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30325, c.id, 'CAMPUS', m.id, 'CLASS_0325', '地科2402', 'CLASS', 325, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '地理信息科学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30326, c.id, 'CAMPUS', m.id, 'CLASS_0326', '地科B2403', 'CLASS', 326, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '地理信息科学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30327, c.id, 'CAMPUS', m.id, 'CLASS_0327', '工程2401', 'CLASS', 327, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30328, c.id, 'CAMPUS', m.id, 'CLASS_0328', '工程B2402', 'CLASS', 328, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30329, c.id, 'CAMPUS', m.id, 'CLASS_0329', '工造2401', 'CLASS', 329, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程造价'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30330, c.id, 'CAMPUS', m.id, 'CLASS_0330', '工造2402', 'CLASS', 330, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程造价'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30331, c.id, 'CAMPUS', m.id, 'CLASS_0331', '工造2403', 'CLASS', 331, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程造价'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30332, c.id, 'CAMPUS', m.id, 'CLASS_0332', '工造B2404', 'CLASS', 332, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程造价'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30333, c.id, 'CAMPUS', m.id, 'CLASS_0333', '智制2401', 'CLASS', 333, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '智能制造工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30334, c.id, 'CAMPUS', m.id, 'CLASS_0334', '机自2401', 'CLASS', 334, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30335, c.id, 'CAMPUS', m.id, 'CLASS_0335', '机自2402', 'CLASS', 335, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30336, c.id, 'CAMPUS', m.id, 'CLASS_0336', '机自2403', 'CLASS', 336, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30337, c.id, 'CAMPUS', m.id, 'CLASS_0337', '机自2404', 'CLASS', 337, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30338, c.id, 'CAMPUS', m.id, 'CLASS_0338', '机自2405', 'CLASS', 338, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30339, c.id, 'CAMPUS', m.id, 'CLASS_0339', '机自B2406', 'CLASS', 339, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30340, c.id, 'CAMPUS', m.id, 'CLASS_0340', '机自B2407', 'CLASS', 340, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30341, c.id, 'CAMPUS', m.id, 'CLASS_0341', '机自B2408', 'CLASS', 341, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30342, c.id, 'CAMPUS', m.id, 'CLASS_0342', '机自B2409', 'CLASS', 342, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30343, c.id, 'CAMPUS', m.id, 'CLASS_0343', '测绘2401', 'CLASS', 343, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '测绘工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30344, c.id, 'CAMPUS', m.id, 'CLASS_0344', '测绘B2402', 'CLASS', 344, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '测绘工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30345, c.id, 'CAMPUS', m.id, 'CLASS_0345', '电气2401', 'CLASS', 345, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30346, c.id, 'CAMPUS', m.id, 'CLASS_0346', '电气2402', 'CLASS', 346, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30347, c.id, 'CAMPUS', m.id, 'CLASS_0347', '电气2403', 'CLASS', 347, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30348, c.id, 'CAMPUS', m.id, 'CLASS_0348', '电气2404', 'CLASS', 348, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30349, c.id, 'CAMPUS', m.id, 'CLASS_0349', '电气2405', 'CLASS', 349, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30350, c.id, 'CAMPUS', m.id, 'CLASS_0350', '电气2406', 'CLASS', 350, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30351, c.id, 'CAMPUS', m.id, 'CLASS_0351', '电气2407', 'CLASS', 351, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30352, c.id, 'CAMPUS', m.id, 'CLASS_0352', '电气2408', 'CLASS', 352, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30353, c.id, 'CAMPUS', m.id, 'CLASS_0353', '电气2409', 'CLASS', 353, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30354, c.id, 'CAMPUS', m.id, 'CLASS_0354', '电气2410', 'CLASS', 354, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30355, c.id, 'CAMPUS', m.id, 'CLASS_0355', '电气2411', 'CLASS', 355, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30356, c.id, 'CAMPUS', m.id, 'CLASS_0356', '电气2412', 'CLASS', 356, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30357, c.id, 'CAMPUS', m.id, 'CLASS_0357', '电气2413', 'CLASS', 357, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30358, c.id, 'CAMPUS', m.id, 'CLASS_0358', '电气2414', 'CLASS', 358, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30359, c.id, 'CAMPUS', m.id, 'CLASS_0359', '电气2415', 'CLASS', 359, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30360, c.id, 'CAMPUS', m.id, 'CLASS_0360', '电气2416', 'CLASS', 360, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30361, c.id, 'CAMPUS', m.id, 'CLASS_0361', '电气2417', 'CLASS', 361, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30362, c.id, 'CAMPUS', m.id, 'CLASS_0362', '电气2418', 'CLASS', 362, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30363, c.id, 'CAMPUS', m.id, 'CLASS_0363', '电气B2419', 'CLASS', 363, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30364, c.id, 'CAMPUS', m.id, 'CLASS_0364', '电气B2420', 'CLASS', 364, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30365, c.id, 'CAMPUS', m.id, 'CLASS_0365', '电气B2421', 'CLASS', 365, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30366, c.id, 'CAMPUS', m.id, 'CLASS_0366', '能环2401', 'CLASS', 366, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '能源与环境系统工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30367, c.id, 'CAMPUS', m.id, 'CLASS_0367', '能环2402', 'CLASS', 367, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '能源与环境系统工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30368, c.id, 'CAMPUS', m.id, 'CLASS_0368', '自动B2401', 'CLASS', 368, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30369, c.id, 'CAMPUS', m.id, 'CLASS_0369', '休体2401(户外)', 'CLASS', 369, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '休闲体育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30370, c.id, 'CAMPUS', m.id, 'CLASS_0370', '休体2402(健美)', 'CLASS', 370, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '休闲体育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30371, c.id, 'CAMPUS', m.id, 'CLASS_0371', '休体2403(户外)', 'CLASS', 371, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '休闲体育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30372, c.id, 'CAMPUS', m.id, 'CLASS_0372', '体教2401(篮球)', 'CLASS', 372, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30373, c.id, 'CAMPUS', m.id, 'CLASS_0373', '体教2402(篮球)', 'CLASS', 373, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30374, c.id, 'CAMPUS', m.id, 'CLASS_0374', '体教2403(篮球)', 'CLASS', 374, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30375, c.id, 'CAMPUS', m.id, 'CLASS_0375', '体教2404(篮球)', 'CLASS', 375, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30376, c.id, 'CAMPUS', m.id, 'CLASS_0376', '体教2405(篮球)', 'CLASS', 376, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30377, c.id, 'CAMPUS', m.id, 'CLASS_0377', '体教2406(田径)', 'CLASS', 377, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30378, c.id, 'CAMPUS', m.id, 'CLASS_0378', '体教2407(田径)', 'CLASS', 378, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30379, c.id, 'CAMPUS', m.id, 'CLASS_0379', '体教2408(排球)', 'CLASS', 379, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30380, c.id, 'CAMPUS', m.id, 'CLASS_0380', '体教2409(排球)', 'CLASS', 380, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30381, c.id, 'CAMPUS', m.id, 'CLASS_0381', '体教2410(排球)', 'CLASS', 381, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30382, c.id, 'CAMPUS', m.id, 'CLASS_0382', '体教2411(武术)', 'CLASS', 382, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30383, c.id, 'CAMPUS', m.id, 'CLASS_0383', '体教2412(羽毛球)', 'CLASS', 383, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30384, c.id, 'CAMPUS', m.id, 'CLASS_0384', '体教2413(羽毛球)', 'CLASS', 384, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30385, c.id, 'CAMPUS', m.id, 'CLASS_0385', '体教2414(乒乓球)', 'CLASS', 385, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30386, c.id, 'CAMPUS', m.id, 'CLASS_0386', '体教2415(健美操)', 'CLASS', 386, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30387, c.id, 'CAMPUS', m.id, 'CLASS_0387', '体教2416(足球)', 'CLASS', 387, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30388, c.id, 'CAMPUS', m.id, 'CLASS_0388', '体教2417(足球)', 'CLASS', 388, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30389, c.id, 'CAMPUS', m.id, 'CLASS_0389', '体教B2418(篮球)', 'CLASS', 389, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30390, c.id, 'CAMPUS', m.id, 'CLASS_0390', '体教B2419(篮球)', 'CLASS', 390, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30391, c.id, 'CAMPUS', m.id, 'CLASS_0391', '体教B2420(排球)', 'CLASS', 391, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30392, c.id, 'CAMPUS', m.id, 'CLASS_0392', '体教B2421(田径)', 'CLASS', 392, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30393, c.id, 'CAMPUS', m.id, 'CLASS_0393', '体教B2422(羽毛球)', 'CLASS', 393, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30394, c.id, 'CAMPUS', m.id, 'CLASS_0394', '体教B2423(足球)', 'CLASS', 394, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30395, c.id, 'CAMPUS', m.id, 'CLASS_0395', '学教2401', 'CLASS', 395, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30396, c.id, 'CAMPUS', m.id, 'CLASS_0396', '学教2402', 'CLASS', 396, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30397, c.id, 'CAMPUS', m.id, 'CLASS_0397', '学教2403', 'CLASS', 397, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30398, c.id, 'CAMPUS', m.id, 'CLASS_0398', '学教2404', 'CLASS', 398, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30399, c.id, 'CAMPUS', m.id, 'CLASS_0399', '学教B2405', 'CLASS', 399, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30400, c.id, 'CAMPUS', m.id, 'CLASS_0400', '学教B2406', 'CLASS', 400, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30401, c.id, 'CAMPUS', m.id, 'CLASS_0401', '学教B2407', 'CLASS', 401, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30402, c.id, 'CAMPUS', m.id, 'CLASS_0402', '学教B2408', 'CLASS', 402, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30403, c.id, 'CAMPUS', m.id, 'CLASS_0403', '学教B2409', 'CLASS', 403, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30404, c.id, 'CAMPUS', m.id, 'CLASS_0404', '学教B2410', 'CLASS', 404, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30405, c.id, 'CAMPUS', m.id, 'CLASS_0405', '学教B2411', 'CLASS', 405, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30406, c.id, 'CAMPUS', m.id, 'CLASS_0406', '学教B2412', 'CLASS', 406, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30407, c.id, 'CAMPUS', m.id, 'CLASS_0407', '学教B2413', 'CLASS', 407, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30408, c.id, 'CAMPUS', m.id, 'CLASS_0408', '学教B2414', 'CLASS', 408, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30409, c.id, 'CAMPUS', m.id, 'CLASS_0409', '学教B2415', 'CLASS', 409, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30410, c.id, 'CAMPUS', m.id, 'CLASS_0410', '学教B2416', 'CLASS', 410, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30411, c.id, 'CAMPUS', m.id, 'CLASS_0411', '学教B2417', 'CLASS', 411, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30412, c.id, 'CAMPUS', m.id, 'CLASS_0412', '学教B2418', 'CLASS', 412, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30413, c.id, 'CAMPUS', m.id, 'CLASS_0413', '学教B2419', 'CLASS', 413, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30414, c.id, 'CAMPUS', m.id, 'CLASS_0414', '学教B2420', 'CLASS', 414, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30415, c.id, 'CAMPUS', m.id, 'CLASS_0415', '学教B2421', 'CLASS', 415, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30416, c.id, 'CAMPUS', m.id, 'CLASS_0416', '学教B2422', 'CLASS', 416, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30417, c.id, 'CAMPUS', m.id, 'CLASS_0417', '英语2401', 'CLASS', 417, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30418, c.id, 'CAMPUS', m.id, 'CLASS_0418', '英语2402', 'CLASS', 418, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30419, c.id, 'CAMPUS', m.id, 'CLASS_0419', '英语2403', 'CLASS', 419, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30420, c.id, 'CAMPUS', m.id, 'CLASS_0420', '英语2404', 'CLASS', 420, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30421, c.id, 'CAMPUS', m.id, 'CLASS_0421', '英语2405', 'CLASS', 421, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30422, c.id, 'CAMPUS', m.id, 'CLASS_0422', '工商2401', 'CLASS', 422, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30423, c.id, 'CAMPUS', m.id, 'CLASS_0423', '工商2402', 'CLASS', 423, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30424, c.id, 'CAMPUS', m.id, 'CLASS_0424', '工商2403', 'CLASS', 424, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30425, c.id, 'CAMPUS', m.id, 'CLASS_0425', '工商B2404', 'CLASS', 425, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30426, c.id, 'CAMPUS', m.id, 'CLASS_0426', '工商B2405', 'CLASS', 426, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30427, c.id, 'CAMPUS', m.id, 'CLASS_0427', '物流2401', 'CLASS', 427, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '物流工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30428, c.id, 'CAMPUS', m.id, 'CLASS_0428', '物流2402', 'CLASS', 428, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '物流工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30429, c.id, 'CAMPUS', m.id, 'CLASS_0429', '电商2401', 'CLASS', 429, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子商务'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30430, c.id, 'CAMPUS', m.id, 'CLASS_0430', '电商B2402', 'CLASS', 430, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子商务'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30431, c.id, 'CAMPUS', m.id, 'CLASS_0431', '财管2401', 'CLASS', 431, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30432, c.id, 'CAMPUS', m.id, 'CLASS_0432', '财管2402', 'CLASS', 432, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30433, c.id, 'CAMPUS', m.id, 'CLASS_0433', '财管2403', 'CLASS', 433, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30434, c.id, 'CAMPUS', m.id, 'CLASS_0434', '财管2404', 'CLASS', 434, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30435, c.id, 'CAMPUS', m.id, 'CLASS_0435', '财管2405', 'CLASS', 435, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30436, c.id, 'CAMPUS', m.id, 'CLASS_0436', '财管2406', 'CLASS', 436, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30437, c.id, 'CAMPUS', m.id, 'CLASS_0437', '财管2407', 'CLASS', 437, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30438, c.id, 'CAMPUS', m.id, 'CLASS_0438', '财管2408', 'CLASS', 438, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30439, c.id, 'CAMPUS', m.id, 'CLASS_0439', '财管2409', 'CLASS', 439, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30440, c.id, 'CAMPUS', m.id, 'CLASS_0440', '财管2410', 'CLASS', 440, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30441, c.id, 'CAMPUS', m.id, 'CLASS_0441', '财管2411', 'CLASS', 441, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30442, c.id, 'CAMPUS', m.id, 'CLASS_0442', '财管2412', 'CLASS', 442, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30443, c.id, 'CAMPUS', m.id, 'CLASS_0443', '财管2413', 'CLASS', 443, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30444, c.id, 'CAMPUS', m.id, 'CLASS_0444', '财管2414', 'CLASS', 444, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30445, c.id, 'CAMPUS', m.id, 'CLASS_0445', '财管2415', 'CLASS', 445, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30446, c.id, 'CAMPUS', m.id, 'CLASS_0446', '财管B2416', 'CLASS', 446, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30447, c.id, 'CAMPUS', m.id, 'CLASS_0447', '财管B2417', 'CLASS', 447, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30448, c.id, 'CAMPUS', m.id, 'CLASS_0448', '财管B2418', 'CLASS', 448, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30449, c.id, 'CAMPUS', m.id, 'CLASS_0449', '财管B2419', 'CLASS', 449, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30450, c.id, 'CAMPUS', m.id, 'CLASS_0450', '财管B2420', 'CLASS', 450, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30451, c.id, 'CAMPUS', m.id, 'CLASS_0451', '金融2401', 'CLASS', 451, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '金融工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30452, c.id, 'CAMPUS', m.id, 'CLASS_0452', '金科2401', 'CLASS', 452, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '金融科技'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30453, c.id, 'CAMPUS', m.id, 'CLASS_0453', '产设2401', 'CLASS', 453, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '产品设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30454, c.id, 'CAMPUS', m.id, 'CLASS_0454', '产设2402', 'CLASS', 454, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '产品设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30455, c.id, 'CAMPUS', m.id, 'CLASS_0455', '产设2403', 'CLASS', 455, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '产品设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30456, c.id, 'CAMPUS', m.id, 'CLASS_0456', '产设2404', 'CLASS', 456, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '产品设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30457, c.id, 'CAMPUS', m.id, 'CLASS_0457', '城规2401', 'CLASS', 457, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '城乡规划'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30458, c.id, 'CAMPUS', m.id, 'CLASS_0458', '城规2402', 'CLASS', 458, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '城乡规划'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30459, c.id, 'CAMPUS', m.id, 'CLASS_0459', '数媒2401', 'CLASS', 459, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30460, c.id, 'CAMPUS', m.id, 'CLASS_0460', '数媒2402', 'CLASS', 460, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30461, c.id, 'CAMPUS', m.id, 'CLASS_0461', '数媒2403', 'CLASS', 461, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30462, c.id, 'CAMPUS', m.id, 'CLASS_0462', '数媒2404', 'CLASS', 462, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30463, c.id, 'CAMPUS', m.id, 'CLASS_0463', '数媒2405', 'CLASS', 463, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30464, c.id, 'CAMPUS', m.id, 'CLASS_0464', '数媒2406', 'CLASS', 464, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30465, c.id, 'CAMPUS', m.id, 'CLASS_0465', '数媒2407', 'CLASS', 465, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30466, c.id, 'CAMPUS', m.id, 'CLASS_0466', '数媒B2408', 'CLASS', 466, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30467, c.id, 'CAMPUS', m.id, 'CLASS_0467', '数媒B2409', 'CLASS', 467, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30468, c.id, 'CAMPUS', m.id, 'CLASS_0468', '数媒B2410', 'CLASS', 468, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30469, c.id, 'CAMPUS', m.id, 'CLASS_0469', '数媒B2411', 'CLASS', 469, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30470, c.id, 'CAMPUS', m.id, 'CLASS_0470', '新媒2401', 'CLASS', 470, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30471, c.id, 'CAMPUS', m.id, 'CLASS_0471', '新媒2402', 'CLASS', 471, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30472, c.id, 'CAMPUS', m.id, 'CLASS_0472', '新媒2403', 'CLASS', 472, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30473, c.id, 'CAMPUS', m.id, 'CLASS_0473', '新媒2404', 'CLASS', 473, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30474, c.id, 'CAMPUS', m.id, 'CLASS_0474', '新媒2405', 'CLASS', 474, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30475, c.id, 'CAMPUS', m.id, 'CLASS_0475', '新媒B2406', 'CLASS', 475, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30476, c.id, 'CAMPUS', m.id, 'CLASS_0476', '智创2401', 'CLASS', 476, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '智能工程与创意设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30477, c.id, 'CAMPUS', m.id, 'CLASS_0477', '艺设2401', 'CLASS', 477, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '艺术设计学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30478, c.id, 'CAMPUS', m.id, 'CLASS_0478', '艺设B2402', 'CLASS', 478, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '艺术设计学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30479, c.id, 'CAMPUS', m.id, 'CLASS_0479', '艺设B2403', 'CLASS', 479, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '艺术设计学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30480, c.id, 'CAMPUS', m.id, 'CLASS_0480', '艺设B2404', 'CLASS', 480, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '艺术设计学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30481, c.id, 'CAMPUS', m.id, 'CLASS_0481', '艺设B2405', 'CLASS', 481, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '艺术设计学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30482, c.id, 'CAMPUS', m.id, 'CLASS_0482', '风园2401', 'CLASS', 482, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '风景园林'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30483, c.id, 'CAMPUS', m.id, 'CLASS_0483', '物联2301', 'CLASS', 483, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '物联网工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30484, c.id, 'CAMPUS', m.id, 'CLASS_0484', '电信2301', 'CLASS', 484, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子信息工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30485, c.id, 'CAMPUS', m.id, 'CLASS_0485', '电信2302', 'CLASS', 485, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子信息工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30486, c.id, 'CAMPUS', m.id, 'CLASS_0486', '电信B2303', 'CLASS', 486, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子信息工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30487, c.id, 'CAMPUS', m.id, 'CLASS_0487', '电信B2304', 'CLASS', 487, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子信息工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30488, c.id, 'CAMPUS', m.id, 'CLASS_0488', '电信B2305', 'CLASS', 488, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子信息工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30489, c.id, 'CAMPUS', m.id, 'CLASS_0489', '电信B2306', 'CLASS', 489, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子信息工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30490, c.id, 'CAMPUS', m.id, 'CLASS_0490', '计科2301', 'CLASS', 490, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30491, c.id, 'CAMPUS', m.id, 'CLASS_0491', '计科2302', 'CLASS', 491, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30492, c.id, 'CAMPUS', m.id, 'CLASS_0492', '计科2303', 'CLASS', 492, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30493, c.id, 'CAMPUS', m.id, 'CLASS_0493', '计科2304', 'CLASS', 493, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30494, c.id, 'CAMPUS', m.id, 'CLASS_0494', '计科2305', 'CLASS', 494, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30495, c.id, 'CAMPUS', m.id, 'CLASS_0495', '计科2306', 'CLASS', 495, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30496, c.id, 'CAMPUS', m.id, 'CLASS_0496', '计科2307', 'CLASS', 496, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30497, c.id, 'CAMPUS', m.id, 'CLASS_0497', '计科B2308', 'CLASS', 497, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30498, c.id, 'CAMPUS', m.id, 'CLASS_0498', '计科B2309', 'CLASS', 498, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30499, c.id, 'CAMPUS', m.id, 'CLASS_0499', '计科B2310', 'CLASS', 499, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30500, c.id, 'CAMPUS', m.id, 'CLASS_0500', '计科B2311', 'CLASS', 500, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30501, c.id, 'CAMPUS', m.id, 'CLASS_0501', '计科B2312', 'CLASS', 501, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30502, c.id, 'CAMPUS', m.id, 'CLASS_0502', '计科B2313', 'CLASS', 502, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30503, c.id, 'CAMPUS', m.id, 'CLASS_0503', '计科B2314', 'CLASS', 503, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30504, c.id, 'CAMPUS', m.id, 'CLASS_0504', '计科B2315', 'CLASS', 504, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30505, c.id, 'CAMPUS', m.id, 'CLASS_0505', '计科B2316', 'CLASS', 505, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30506, c.id, 'CAMPUS', m.id, 'CLASS_0506', '计科B2317', 'CLASS', 506, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30507, c.id, 'CAMPUS', m.id, 'CLASS_0507', '计科B2318', 'CLASS', 507, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30508, c.id, 'CAMPUS', m.id, 'CLASS_0508', '计科B2319', 'CLASS', 508, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30509, c.id, 'CAMPUS', m.id, 'CLASS_0509', '计科B2320', 'CLASS', 509, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '计算机科学与技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30510, c.id, 'CAMPUS', m.id, 'CLASS_0510', '软工2301', 'CLASS', 510, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30511, c.id, 'CAMPUS', m.id, 'CLASS_0511', '软工2302', 'CLASS', 511, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30512, c.id, 'CAMPUS', m.id, 'CLASS_0512', '软工2303', 'CLASS', 512, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30513, c.id, 'CAMPUS', m.id, 'CLASS_0513', '软工2304', 'CLASS', 513, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30514, c.id, 'CAMPUS', m.id, 'CLASS_0514', '软工B2305', 'CLASS', 514, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30515, c.id, 'CAMPUS', m.id, 'CLASS_0515', '软工B2306', 'CLASS', 515, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30516, c.id, 'CAMPUS', m.id, 'CLASS_0516', '软工B2307', 'CLASS', 516, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '软件工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30517, c.id, 'CAMPUS', m.id, 'CLASS_0517', '通信2301', 'CLASS', 517, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '通信工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30518, c.id, 'CAMPUS', m.id, 'CLASS_0518', '通信B2302', 'CLASS', 518, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '通信工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30519, c.id, 'CAMPUS', m.id, 'CLASS_0519', '交通2301', 'CLASS', 519, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '交通工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30520, c.id, 'CAMPUS', m.id, 'CLASS_0520', '交通B2302', 'CLASS', 520, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '交通工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30521, c.id, 'CAMPUS', m.id, 'CLASS_0521', '交通B2303', 'CLASS', 521, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '交通工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30522, c.id, 'CAMPUS', m.id, 'CLASS_0522', '土木2301', 'CLASS', 522, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '土木工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30523, c.id, 'CAMPUS', m.id, 'CLASS_0523', '土木B2302', 'CLASS', 523, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '土木工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30524, c.id, 'CAMPUS', m.id, 'CLASS_0524', '土木B2303', 'CLASS', 524, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '土木工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30525, c.id, 'CAMPUS', m.id, 'CLASS_0525', '土木B2304', 'CLASS', 525, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '土木工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30526, c.id, 'CAMPUS', m.id, 'CLASS_0526', '地科2301', 'CLASS', 526, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '地理信息科学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30527, c.id, 'CAMPUS', m.id, 'CLASS_0527', '地科2302', 'CLASS', 527, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '地理信息科学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30528, c.id, 'CAMPUS', m.id, 'CLASS_0528', '地科2303', 'CLASS', 528, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '地理信息科学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30529, c.id, 'CAMPUS', m.id, 'CLASS_0529', '地科B2304', 'CLASS', 529, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '地理信息科学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30530, c.id, 'CAMPUS', m.id, 'CLASS_0530', '地科B2305', 'CLASS', 530, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '地理信息科学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30531, c.id, 'CAMPUS', m.id, 'CLASS_0531', '地科B2306', 'CLASS', 531, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '地理信息科学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30532, c.id, 'CAMPUS', m.id, 'CLASS_0532', '工程2301', 'CLASS', 532, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30533, c.id, 'CAMPUS', m.id, 'CLASS_0533', '工程B2302', 'CLASS', 533, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30534, c.id, 'CAMPUS', m.id, 'CLASS_0534', '工造2301', 'CLASS', 534, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程造价'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30535, c.id, 'CAMPUS', m.id, 'CLASS_0535', '工造2302', 'CLASS', 535, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程造价'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30536, c.id, 'CAMPUS', m.id, 'CLASS_0536', '工造2303', 'CLASS', 536, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程造价'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30537, c.id, 'CAMPUS', m.id, 'CLASS_0537', '工造2304', 'CLASS', 537, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程造价'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30538, c.id, 'CAMPUS', m.id, 'CLASS_0538', '工造B2305', 'CLASS', 538, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程造价'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30539, c.id, 'CAMPUS', m.id, 'CLASS_0539', '工造B2306', 'CLASS', 539, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程造价'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30540, c.id, 'CAMPUS', m.id, 'CLASS_0540', '工造B2307', 'CLASS', 540, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工程造价'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30541, c.id, 'CAMPUS', m.id, 'CLASS_0541', '智制2301', 'CLASS', 541, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '智能制造工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30542, c.id, 'CAMPUS', m.id, 'CLASS_0542', '智制B2302', 'CLASS', 542, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '智能制造工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30543, c.id, 'CAMPUS', m.id, 'CLASS_0543', '机自2301', 'CLASS', 543, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30544, c.id, 'CAMPUS', m.id, 'CLASS_0544', '机自2302', 'CLASS', 544, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30545, c.id, 'CAMPUS', m.id, 'CLASS_0545', '机自B2303', 'CLASS', 545, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30546, c.id, 'CAMPUS', m.id, 'CLASS_0546', '机自B2304', 'CLASS', 546, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30547, c.id, 'CAMPUS', m.id, 'CLASS_0547', '机自B2305', 'CLASS', 547, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30548, c.id, 'CAMPUS', m.id, 'CLASS_0548', '机自B2306', 'CLASS', 548, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30549, c.id, 'CAMPUS', m.id, 'CLASS_0549', '机自B2307', 'CLASS', 549, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30550, c.id, 'CAMPUS', m.id, 'CLASS_0550', '机自B2308', 'CLASS', 550, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30551, c.id, 'CAMPUS', m.id, 'CLASS_0551', '机自B2309', 'CLASS', 551, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '机械设计制造及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30552, c.id, 'CAMPUS', m.id, 'CLASS_0552', '测绘2301', 'CLASS', 552, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '测绘工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30553, c.id, 'CAMPUS', m.id, 'CLASS_0553', '测绘B2302', 'CLASS', 553, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '测绘工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30554, c.id, 'CAMPUS', m.id, 'CLASS_0554', '电气2301', 'CLASS', 554, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30555, c.id, 'CAMPUS', m.id, 'CLASS_0555', '电气2302', 'CLASS', 555, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30556, c.id, 'CAMPUS', m.id, 'CLASS_0556', '电气2303', 'CLASS', 556, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30557, c.id, 'CAMPUS', m.id, 'CLASS_0557', '电气2304', 'CLASS', 557, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30558, c.id, 'CAMPUS', m.id, 'CLASS_0558', '电气2305', 'CLASS', 558, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30559, c.id, 'CAMPUS', m.id, 'CLASS_0559', '电气B2306', 'CLASS', 559, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30560, c.id, 'CAMPUS', m.id, 'CLASS_0560', '电气B2307', 'CLASS', 560, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30561, c.id, 'CAMPUS', m.id, 'CLASS_0561', '电气B2308', 'CLASS', 561, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电气工程及其自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30562, c.id, 'CAMPUS', m.id, 'CLASS_0562', '能环2301', 'CLASS', 562, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '能源与环境系统工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30563, c.id, 'CAMPUS', m.id, 'CLASS_0563', '自动2301', 'CLASS', 563, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30564, c.id, 'CAMPUS', m.id, 'CLASS_0564', '自动2302', 'CLASS', 564, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30565, c.id, 'CAMPUS', m.id, 'CLASS_0565', '自动B2303', 'CLASS', 565, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '自动化'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30566, c.id, 'CAMPUS', m.id, 'CLASS_0566', '休体2301(健身)', 'CLASS', 566, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '休闲体育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30567, c.id, 'CAMPUS', m.id, 'CLASS_0567', '休体2302(户外)', 'CLASS', 567, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '休闲体育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30568, c.id, 'CAMPUS', m.id, 'CLASS_0568', '休体2303(户外)', 'CLASS', 568, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '休闲体育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30569, c.id, 'CAMPUS', m.id, 'CLASS_0569', '休体2304(户外)', 'CLASS', 569, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '休闲体育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30570, c.id, 'CAMPUS', m.id, 'CLASS_0570', '体教2301(健美操)', 'CLASS', 570, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30571, c.id, 'CAMPUS', m.id, 'CLASS_0571', '体教2302(篮球)', 'CLASS', 571, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30572, c.id, 'CAMPUS', m.id, 'CLASS_0572', '体教2303(篮球)', 'CLASS', 572, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30573, c.id, 'CAMPUS', m.id, 'CLASS_0573', '体教2304(篮球)', 'CLASS', 573, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30574, c.id, 'CAMPUS', m.id, 'CLASS_0574', '体教2305(排球)', 'CLASS', 574, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30575, c.id, 'CAMPUS', m.id, 'CLASS_0575', '体教2306(排球)', 'CLASS', 575, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30576, c.id, 'CAMPUS', m.id, 'CLASS_0576', '体教2307(乒乓球)', 'CLASS', 576, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30577, c.id, 'CAMPUS', m.id, 'CLASS_0577', '体教2308(田径)', 'CLASS', 577, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30578, c.id, 'CAMPUS', m.id, 'CLASS_0578', '体教2309(田径)', 'CLASS', 578, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30579, c.id, 'CAMPUS', m.id, 'CLASS_0579', '体教2310(武术)', 'CLASS', 579, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30580, c.id, 'CAMPUS', m.id, 'CLASS_0580', '体教2311(羽毛球)', 'CLASS', 580, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30581, c.id, 'CAMPUS', m.id, 'CLASS_0581', '体教2312(羽毛球)', 'CLASS', 581, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30582, c.id, 'CAMPUS', m.id, 'CLASS_0582', '体教2313(足球)', 'CLASS', 582, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30583, c.id, 'CAMPUS', m.id, 'CLASS_0583', '体教B2314(篮球)', 'CLASS', 583, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30584, c.id, 'CAMPUS', m.id, 'CLASS_0584', '体教B2315(篮球)', 'CLASS', 584, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30585, c.id, 'CAMPUS', m.id, 'CLASS_0585', '体教B2316(篮球)', 'CLASS', 585, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30586, c.id, 'CAMPUS', m.id, 'CLASS_0586', '体教B2317(篮球)', 'CLASS', 586, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30587, c.id, 'CAMPUS', m.id, 'CLASS_0587', '体教B2318(乒乓球)', 'CLASS', 587, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30588, c.id, 'CAMPUS', m.id, 'CLASS_0588', '体教B2319(田径)', 'CLASS', 588, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30589, c.id, 'CAMPUS', m.id, 'CLASS_0589', '体教B2320(田径)', 'CLASS', 589, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30590, c.id, 'CAMPUS', m.id, 'CLASS_0590', '体教B2321(羽毛球)', 'CLASS', 590, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30591, c.id, 'CAMPUS', m.id, 'CLASS_0591', '体教B2322(羽毛球)', 'CLASS', 591, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30592, c.id, 'CAMPUS', m.id, 'CLASS_0592', '体教B2323(足球)', 'CLASS', 592, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30593, c.id, 'CAMPUS', m.id, 'CLASS_0593', '体教B2324(足球)', 'CLASS', 593, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '体育教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30594, c.id, 'CAMPUS', m.id, 'CLASS_0594', '学教2301', 'CLASS', 594, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30595, c.id, 'CAMPUS', m.id, 'CLASS_0595', '学教2302', 'CLASS', 595, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30596, c.id, 'CAMPUS', m.id, 'CLASS_0596', '学教2303', 'CLASS', 596, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30597, c.id, 'CAMPUS', m.id, 'CLASS_0597', '学教2304', 'CLASS', 597, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30598, c.id, 'CAMPUS', m.id, 'CLASS_0598', '学教2305', 'CLASS', 598, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30599, c.id, 'CAMPUS', m.id, 'CLASS_0599', '学教2306', 'CLASS', 599, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30600, c.id, 'CAMPUS', m.id, 'CLASS_0600', '学教B2307', 'CLASS', 600, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30601, c.id, 'CAMPUS', m.id, 'CLASS_0601', '学教B2308', 'CLASS', 601, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30602, c.id, 'CAMPUS', m.id, 'CLASS_0602', '学教B2309', 'CLASS', 602, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30603, c.id, 'CAMPUS', m.id, 'CLASS_0603', '学教B2310', 'CLASS', 603, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30604, c.id, 'CAMPUS', m.id, 'CLASS_0604', '学教B2311', 'CLASS', 604, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30605, c.id, 'CAMPUS', m.id, 'CLASS_0605', '学教B2312', 'CLASS', 605, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30606, c.id, 'CAMPUS', m.id, 'CLASS_0606', '学教B2313', 'CLASS', 606, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30607, c.id, 'CAMPUS', m.id, 'CLASS_0607', '学教B2314', 'CLASS', 607, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30608, c.id, 'CAMPUS', m.id, 'CLASS_0608', '学教B2315', 'CLASS', 608, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30609, c.id, 'CAMPUS', m.id, 'CLASS_0609', '学教B2316', 'CLASS', 609, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30610, c.id, 'CAMPUS', m.id, 'CLASS_0610', '学教B2317', 'CLASS', 610, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30611, c.id, 'CAMPUS', m.id, 'CLASS_0611', '学教B2318', 'CLASS', 611, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30612, c.id, 'CAMPUS', m.id, 'CLASS_0612', '学教B2319', 'CLASS', 612, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30613, c.id, 'CAMPUS', m.id, 'CLASS_0613', '学教B2320', 'CLASS', 613, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30614, c.id, 'CAMPUS', m.id, 'CLASS_0614', '学教B2321', 'CLASS', 614, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30615, c.id, 'CAMPUS', m.id, 'CLASS_0615', '学教B2322', 'CLASS', 615, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30616, c.id, 'CAMPUS', m.id, 'CLASS_0616', '学教B2323', 'CLASS', 616, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30617, c.id, 'CAMPUS', m.id, 'CLASS_0617', '学教B2324', 'CLASS', 617, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30618, c.id, 'CAMPUS', m.id, 'CLASS_0618', '学教B2325', 'CLASS', 618, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30619, c.id, 'CAMPUS', m.id, 'CLASS_0619', '学教B2326', 'CLASS', 619, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30620, c.id, 'CAMPUS', m.id, 'CLASS_0620', '学教B2327', 'CLASS', 620, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30621, c.id, 'CAMPUS', m.id, 'CLASS_0621', '学教B2328', 'CLASS', 621, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '学前教育'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30622, c.id, 'CAMPUS', m.id, 'CLASS_0622', '英语2301', 'CLASS', 622, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30623, c.id, 'CAMPUS', m.id, 'CLASS_0623', '英语2302', 'CLASS', 623, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30624, c.id, 'CAMPUS', m.id, 'CLASS_0624', '英语2303', 'CLASS', 624, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30625, c.id, 'CAMPUS', m.id, 'CLASS_0625', '英语2304', 'CLASS', 625, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30626, c.id, 'CAMPUS', m.id, 'CLASS_0626', '英语2305', 'CLASS', 626, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '英语'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30627, c.id, 'CAMPUS', m.id, 'CLASS_0627', '工商2301', 'CLASS', 627, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30628, c.id, 'CAMPUS', m.id, 'CLASS_0628', '工商2302', 'CLASS', 628, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30629, c.id, 'CAMPUS', m.id, 'CLASS_0629', '工商B2303', 'CLASS', 629, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30630, c.id, 'CAMPUS', m.id, 'CLASS_0630', '工商B2304', 'CLASS', 630, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30631, c.id, 'CAMPUS', m.id, 'CLASS_0631', '工商B2305', 'CLASS', 631, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30632, c.id, 'CAMPUS', m.id, 'CLASS_0632', '工商B2306', 'CLASS', 632, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '工商管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30633, c.id, 'CAMPUS', m.id, 'CLASS_0633', '物流2301', 'CLASS', 633, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '物流工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30634, c.id, 'CAMPUS', m.id, 'CLASS_0634', '电商2301', 'CLASS', 634, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子商务'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30635, c.id, 'CAMPUS', m.id, 'CLASS_0635', '电商B2302', 'CLASS', 635, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '电子商务'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30636, c.id, 'CAMPUS', m.id, 'CLASS_0636', '财管2301', 'CLASS', 636, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30637, c.id, 'CAMPUS', m.id, 'CLASS_0637', '财管2302', 'CLASS', 637, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30638, c.id, 'CAMPUS', m.id, 'CLASS_0638', '财管2303', 'CLASS', 638, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30639, c.id, 'CAMPUS', m.id, 'CLASS_0639', '财管2304', 'CLASS', 639, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30640, c.id, 'CAMPUS', m.id, 'CLASS_0640', '财管2305', 'CLASS', 640, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30641, c.id, 'CAMPUS', m.id, 'CLASS_0641', '财管2306', 'CLASS', 641, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30642, c.id, 'CAMPUS', m.id, 'CLASS_0642', '财管2307', 'CLASS', 642, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30643, c.id, 'CAMPUS', m.id, 'CLASS_0643', '财管2308', 'CLASS', 643, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30644, c.id, 'CAMPUS', m.id, 'CLASS_0644', '财管2309', 'CLASS', 644, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30645, c.id, 'CAMPUS', m.id, 'CLASS_0645', '财管2310', 'CLASS', 645, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30646, c.id, 'CAMPUS', m.id, 'CLASS_0646', '财管B2311', 'CLASS', 646, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30647, c.id, 'CAMPUS', m.id, 'CLASS_0647', '财管B2312', 'CLASS', 647, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30648, c.id, 'CAMPUS', m.id, 'CLASS_0648', '财管B2313', 'CLASS', 648, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30649, c.id, 'CAMPUS', m.id, 'CLASS_0649', '财管B2314', 'CLASS', 649, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30650, c.id, 'CAMPUS', m.id, 'CLASS_0650', '财管B2315', 'CLASS', 650, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '财务管理'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30651, c.id, 'CAMPUS', m.id, 'CLASS_0651', '金融2301', 'CLASS', 651, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '金融工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30652, c.id, 'CAMPUS', m.id, 'CLASS_0652', '金融2302', 'CLASS', 652, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '金融工程'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30653, c.id, 'CAMPUS', m.id, 'CLASS_0653', '金科2301', 'CLASS', 653, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '金融科技'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '游仙校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30654, c.id, 'CAMPUS', m.id, 'CLASS_0654', '产设2301', 'CLASS', 654, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '产品设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30655, c.id, 'CAMPUS', m.id, 'CLASS_0655', '产设2302', 'CLASS', 655, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '产品设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30656, c.id, 'CAMPUS', m.id, 'CLASS_0656', '产设2303', 'CLASS', 656, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '产品设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30657, c.id, 'CAMPUS', m.id, 'CLASS_0657', '城规2301', 'CLASS', 657, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '城乡规划'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30658, c.id, 'CAMPUS', m.id, 'CLASS_0658', '城规2302', 'CLASS', 658, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '城乡规划'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30659, c.id, 'CAMPUS', m.id, 'CLASS_0659', '数媒2301', 'CLASS', 659, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30660, c.id, 'CAMPUS', m.id, 'CLASS_0660', '数媒2302', 'CLASS', 660, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30661, c.id, 'CAMPUS', m.id, 'CLASS_0661', '数媒2303', 'CLASS', 661, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30662, c.id, 'CAMPUS', m.id, 'CLASS_0662', '数媒2304', 'CLASS', 662, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30663, c.id, 'CAMPUS', m.id, 'CLASS_0663', '数媒2305', 'CLASS', 663, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30664, c.id, 'CAMPUS', m.id, 'CLASS_0664', '数媒2306', 'CLASS', 664, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30665, c.id, 'CAMPUS', m.id, 'CLASS_0665', '数媒B2307', 'CLASS', 665, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30666, c.id, 'CAMPUS', m.id, 'CLASS_0666', '数媒B2308', 'CLASS', 666, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30667, c.id, 'CAMPUS', m.id, 'CLASS_0667', '数媒B2309', 'CLASS', 667, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30668, c.id, 'CAMPUS', m.id, 'CLASS_0668', '数媒B2310', 'CLASS', 668, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30669, c.id, 'CAMPUS', m.id, 'CLASS_0669', '数媒B2311', 'CLASS', 669, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30670, c.id, 'CAMPUS', m.id, 'CLASS_0670', '数媒B2312', 'CLASS', 670, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '数字媒体技术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30671, c.id, 'CAMPUS', m.id, 'CLASS_0671', '新媒2301', 'CLASS', 671, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30672, c.id, 'CAMPUS', m.id, 'CLASS_0672', '新媒2302', 'CLASS', 672, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30673, c.id, 'CAMPUS', m.id, 'CLASS_0673', '新媒2303', 'CLASS', 673, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30674, c.id, 'CAMPUS', m.id, 'CLASS_0674', '新媒2304', 'CLASS', 674, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '新媒体艺术'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30675, c.id, 'CAMPUS', m.id, 'CLASS_0675', '智创2301', 'CLASS', 675, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '智能工程与创意设计'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30676, c.id, 'CAMPUS', m.id, 'CLASS_0676', '艺设2301', 'CLASS', 676, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '艺术设计学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30677, c.id, 'CAMPUS', m.id, 'CLASS_0677', '艺设2302', 'CLASS', 677, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '艺术设计学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30678, c.id, 'CAMPUS', m.id, 'CLASS_0678', '艺设B2303', 'CLASS', 678, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '艺术设计学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30679, c.id, 'CAMPUS', m.id, 'CLASS_0679', '艺设B2304', 'CLASS', 679, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '艺术设计学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30680, c.id, 'CAMPUS', m.id, 'CLASS_0680', '艺设B2305', 'CLASS', 680, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '艺术设计学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30681, c.id, 'CAMPUS', m.id, 'CLASS_0681', '艺设B2306', 'CLASS', 681, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '艺术设计学'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30682, c.id, 'CAMPUS', m.id, 'CLASS_0682', '风园2301', 'CLASS', 682, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '风景园林'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30683, c.id, 'CAMPUS', m.id, 'CLASS_0683', '风园2302', 'CLASS', 683, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '风景园林'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30684, c.id, 'CAMPUS', m.id, 'CLASS_0684', '风园B2303', 'CLASS', 684, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '风景园林'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30685, c.id, 'CAMPUS', m.id, 'CLASS_0685', '风园B2304', 'CLASS', 685, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '风景园林'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30686, c.id, 'CAMPUS', m.id, 'CLASS_0686', '城规2201', 'CLASS', 686, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '城乡规划'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
INSERT IGNORE INTO sys_organization
    (id, campus_id, campus_scope, parent_id, organization_code, organization_name, organization_type, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
SELECT 30687, c.id, 'CAMPUS', m.id, 'CLASS_0687', '城规2202', 'CLASS', 687, 'ENABLED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0
FROM sys_campus c JOIN sys_organization m ON m.organization_name = '城乡规划'
     AND m.organization_type = 'MAJOR' AND m.deleted = 0
WHERE c.campus_name = '安州校区' AND c.deleted = 0 LIMIT 1;
