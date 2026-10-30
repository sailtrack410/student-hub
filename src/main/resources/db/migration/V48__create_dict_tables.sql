-- 通用字典管理：字典类型 + 字典项，供管理端维护、App/后台按 typeCode 读取。
-- 种子：学校官方报修应用的两份字典（报修类型 12 项、宿舍楼 21 栋），运维可在管理端继续增改。
-- MySQL/H2 双兼容：单子句 DDL，不使用列 COMMENT / IF NOT EXISTS。
CREATE TABLE sys_dict_type (
    id BIGINT NOT NULL,
    type_code VARCHAR(64) NOT NULL,
    type_name VARCHAR(64) NOT NULL,
    remark VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    builtin TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_dict_type_code ON sys_dict_type (type_code);

CREATE TABLE sys_dict_item (
    id BIGINT NOT NULL,
    type_code VARCHAR(64) NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_label VARCHAR(128) NOT NULL,
    item_value VARCHAR(255) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    extra TEXT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_dict_item_type ON sys_dict_item (type_code, sort_order);

-- 字典管理权限点（默认绑定平台管理员角色）
INSERT IGNORE INTO sys_permission
    (id, permission_code, permission_name, permission_type, status, created_at, updated_at, created_by, updated_by,
     deleted, version)
VALUES
    (26, 'system:dict:manage', '字典管理', 'API', 'ENABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, created_at, created_by)
VALUES
    (1602, 1, 26, CURRENT_TIMESTAMP, 0);

-- 字典类型：宿舍报修类型 / 宿舍楼栋（builtin=1，禁止删除，可改名称与停用）
INSERT IGNORE INTO sys_dict_type
    (id, type_code, type_name, remark, status, builtin, created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (1, 'REPAIR_FAULT_TYPE', '宿舍报修类型', '来源：学校官方报修应用；extra.emergency=true 表示紧急类型',
     'ENABLED', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (2, 'DORM_BUILDING', '宿舍楼栋', '来源：学校官方报修应用；item_code 为楼号，extra.community 为社区',
     'ENABLED', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

-- 报修类型（学校字典 12 类，停电/停水/漏水为紧急）
INSERT IGNORE INTO sys_dict_item
    (id, type_code, item_code, item_label, item_value, sort_order, status, extra, remark,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (1001, 'REPAIR_FAULT_TYPE', 'DOOR_WINDOW', '门窗', '门窗', 1, 'ENABLED', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1002, 'REPAIR_FAULT_TYPE', 'WALL', '墙皮', '墙皮', 2, 'ENABLED', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1003, 'REPAIR_FAULT_TYPE', 'FLOOR_TILE', '地砖', '地砖', 3, 'ENABLED', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1004, 'REPAIR_FAULT_TYPE', 'DESK', '桌子', '桌子', 4, 'ENABLED', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1005, 'REPAIR_FAULT_TYPE', 'CHAIR', '椅子', '椅子', 5, 'ENABLED', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1006, 'REPAIR_FAULT_TYPE', 'BED', '床', '床', 6, 'ENABLED', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1007, 'REPAIR_FAULT_TYPE', 'FAN', '风扇', '风扇', 7, 'ENABLED', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1008, 'REPAIR_FAULT_TYPE', 'AIR_CONDITIONER', '空调', '空调', 8, 'ENABLED', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1009, 'REPAIR_FAULT_TYPE', 'LIGHT', '灯', '灯', 9, 'ENABLED', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1010, 'REPAIR_FAULT_TYPE', 'POWER_OUTAGE', '停电', '停电', 10, 'ENABLED', '{"emergency":true}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1011, 'REPAIR_FAULT_TYPE', 'WATER_OUTAGE', '停水', '停水', 11, 'ENABLED', '{"emergency":true}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1012, 'REPAIR_FAULT_TYPE', 'WATER_LEAK', '漏水', '漏水', 12, 'ENABLED', '{"emergency":true}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

-- 宿舍楼栋（4 社区 21 栋，sort_order 按社区分段：博宁 1x / 博育 2x / 博安 3x / 博韵 4x）
INSERT IGNORE INTO sys_dict_item
    (id, type_code, item_code, item_label, item_value, sort_order, status, extra, remark,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (1101, 'DORM_BUILDING', '0110', '博宁社区-博梦1', '博梦1', 1, 'ENABLED', '{"community":"博宁社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1102, 'DORM_BUILDING', '0111', '博宁社区-博梦2', '博梦2', 2, 'ENABLED', '{"community":"博宁社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1103, 'DORM_BUILDING', '0112', '博宁社区-博梦3', '博梦3', 3, 'ENABLED', '{"community":"博宁社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1104, 'DORM_BUILDING', '0101', '博宁社区-博宁1', '博宁1', 4, 'ENABLED', '{"community":"博宁社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1105, 'DORM_BUILDING', '0102', '博宁社区-博宁2', '博宁2', 5, 'ENABLED', '{"community":"博宁社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1106, 'DORM_BUILDING', '0103', '博宁社区-博宁3', '博宁3', 6, 'ENABLED', '{"community":"博宁社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1107, 'DORM_BUILDING', '0104', '博宁社区-博宁4', '博宁4', 7, 'ENABLED', '{"community":"博宁社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1108, 'DORM_BUILDING', '0113', '博宁社区-博爱C', '博爱C', 8, 'ENABLED', '{"community":"博宁社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1109, 'DORM_BUILDING', '0114', '博宁社区-博爱D', '博爱D', 9, 'ENABLED', '{"community":"博宁社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1110, 'DORM_BUILDING', '0105', '博育社区-博宁5', '博宁5', 11, 'ENABLED', '{"community":"博育社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1111, 'DORM_BUILDING', '0106', '博育社区-博宁6', '博宁6', 12, 'ENABLED', '{"community":"博育社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1112, 'DORM_BUILDING', '0107', '博育社区-博宁7', '博宁7', 13, 'ENABLED', '{"community":"博育社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1113, 'DORM_BUILDING', '0108', '博育社区-博宁8', '博宁8', 14, 'ENABLED', '{"community":"博育社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1114, 'DORM_BUILDING', '0109', '博育社区-博宁9', '博宁9', 15, 'ENABLED', '{"community":"博育社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1115, 'DORM_BUILDING', '0207', '博安社区-博安1', '博安1', 21, 'ENABLED', '{"community":"博安社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1116, 'DORM_BUILDING', '0208', '博安社区-博安2', '博安2', 22, 'ENABLED', '{"community":"博安社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1117, 'DORM_BUILDING', '0209', '博安社区-D1', 'D1', 23, 'ENABLED', '{"community":"博安社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1118, 'DORM_BUILDING', '0210', '博安社区-D2', 'D2', 24, 'ENABLED', '{"community":"博安社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1119, 'DORM_BUILDING', '0202', '博韵社区-博华2', '博华2', 31, 'ENABLED', '{"community":"博韵社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1120, 'DORM_BUILDING', '0205', '博韵社区-博慧3', '博慧3', 32, 'ENABLED', '{"community":"博韵社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0),
    (1121, 'DORM_BUILDING', '0206', '博韵社区-博慧4', '博慧4', 33, 'ENABLED', '{"community":"博韵社区"}', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);
