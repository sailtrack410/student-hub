-- AI 助手图文消息：USER 消息可携带图片文件 ID（JSON 数组），供识图与历史回显。
ALTER TABLE ai_message
    ADD COLUMN images TEXT NULL COMMENT '用户消息图片文件 ID 数组 JSON，如 [123,456]' AFTER content;