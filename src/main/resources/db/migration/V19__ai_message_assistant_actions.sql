-- AI 助手结构化回复：在消息上挂可选的动作卡片（JSON 数组），仅 ASSISTANT 消息使用。
-- 去掉 AFTER 子句：MySQL 方言，H2（集成测试库）解析失败
ALTER TABLE ai_message ADD COLUMN actions TEXT NULL COMMENT '动作卡片 JSON 数组，如 [{"type":"SERVICE_APPLY","label":"去请假","url":"/pages/service/apply?itemId=1"}]';