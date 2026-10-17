-- 供应商图标、调用协议与流式支持
-- protocol 决定 chat 端点：OPENAI_CHAT=OpenAI 兼容 /chat/completions，
-- ANTHROPIC_MESSAGES=Anthropic /messages，OPENAI_RESPONSES=OpenAI /responses
-- 拆成单子句 ALTER 并去掉 AFTER 子句：多子句与 AFTER 均为 MySQL 方言，H2（集成测试库）解析失败
ALTER TABLE ai_provider ADD COLUMN icon VARCHAR(64) NULL COMMENT '供应商图标标识（lobe-icons 名）';
ALTER TABLE ai_provider ADD COLUMN protocol VARCHAR(32) NOT NULL DEFAULT 'OPENAI_CHAT' COMMENT '调用协议';
ALTER TABLE ai_provider ADD COLUMN streaming TINYINT(1) NOT NULL DEFAULT 1 COMMENT '支持流式输出（SSE）';
