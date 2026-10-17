-- AI 助手图文消息：USER 消息可携带图片文件 ID（JSON 数组），供识图与历史回显。
-- 去掉 AFTER 子句：MySQL 方言，H2（集成测试库）解析失败
ALTER TABLE ai_message ADD COLUMN images TEXT NULL COMMENT '用户消息图片文件 ID 数组 JSON，如 [123,456]';