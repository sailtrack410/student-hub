-- 宿舍报修表单对齐学校官方报修应用（my.mycc.edu.cn/xsssbxsq）字典：
--   报修类型 12 类，其中 停电/停水/漏水 标记为紧急（工单创建时自动提优先级，见 WorkOrderServiceImpl）
--   宿舍楼 4 社区 21 栋，按社区排序拼接成单个下拉选项（现有动态表单渲染器只支持字符串 options）
-- 沿用 V30 整段覆盖 form_schema 的方式：单条 UPDATE，MySQL 与 H2 均兼容。
UPDATE service_item
SET form_schema = '[{"key":"contactPhone","label":"联系方式","type":"input","required":true,"max":32,"placeholder":"请输入手机号"},{"key":"dormBuilding","label":"宿舍楼","type":"select","required":true,"options":["博宁社区-博梦1","博宁社区-博梦2","博宁社区-博梦3","博宁社区-博宁1","博宁社区-博宁2","博宁社区-博宁3","博宁社区-博宁4","博宁社区-博爱C","博宁社区-博爱D","博育社区-博宁5","博育社区-博宁6","博育社区-博宁7","博育社区-博宁8","博育社区-博宁9","博安社区-博安1","博安社区-博安2","博安社区-D1","博安社区-D2","博韵社区-博华2","博韵社区-博慧3","博韵社区-博慧4"]},{"key":"address","label":"房间号","type":"input","required":true,"max":32,"placeholder":"如：302"},{"key":"faultType","label":"报修类型","type":"select","required":true,"options":["门窗","墙皮","地砖","桌子","椅子","床","风扇","空调","灯","停电","停水","漏水"]},{"key":"description","label":"问题描述","type":"textarea","required":true,"max":500,"placeholder":"请描述故障现象、发生时间等；停电/停水/漏水将按紧急工单处理"},{"key":"expectDate","label":"期望上门日期","type":"date","required":false},{"key":"photos","label":"故障照片","type":"image","required":false,"maxCount":3}]'
WHERE item_code = 'REPAIR-DORM' AND deleted = 0;
