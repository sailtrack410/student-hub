-- 校园应用分类：承载课表等非表单类功能入口（由目录接口动态下发）

INSERT INTO service_category
    (id, campus_id, category_code, category_name, icon, description, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (500, 1, 'APP', '校园应用', 'cat-guide', '课表查询等校园工具应用', 90, 'ENABLED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);

INSERT INTO service_item
    (id, campus_id, category_id, item_code, item_name, summary, description, icon, form_schema,
     handler_organization_id, sla_hours, sort_order, status,
     created_at, updated_at, created_by, updated_by, deleted, version)
VALUES
    (5001, 1, 500, 'SCHEDULE-VIEW', '我的课表', '查看每周课程安排', '从教务系统同步的个人课表，支持按周查看上课时间与地点。',
     'cat-schedule', NULL, 10, NULL, 10, 'ENABLED',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0);
