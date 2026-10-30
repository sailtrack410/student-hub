-- AI 对话上下文策略 + 知识库供 AI 检索（2026-10-19）
-- 说明：全部为单子句 ALTER（H2/MySQL 双兼容）；默认值即推荐值，可在管理端修改。

-- 1) 全局上下文策略
ALTER TABLE ai_config ADD COLUMN max_context_tokens INT NOT NULL DEFAULT 60000;
ALTER TABLE ai_config ADD COLUMN context_safety_margin_tokens INT NOT NULL DEFAULT 512;
ALTER TABLE ai_config ADD COLUMN max_output_tokens INT NOT NULL DEFAULT 2048;
ALTER TABLE ai_config ADD COLUMN history_token_ratio DECIMAL(4,3) NOT NULL DEFAULT 0.400;
ALTER TABLE ai_config ADD COLUMN rag_token_ratio DECIMAL(4,3) NOT NULL DEFAULT 0.250;
ALTER TABLE ai_config ADD COLUMN attachment_token_ratio DECIMAL(4,3) NOT NULL DEFAULT 0.150;
ALTER TABLE ai_config ADD COLUMN summary_enabled TINYINT NOT NULL DEFAULT 1;
ALTER TABLE ai_config ADD COLUMN summary_trigger_ratio DECIMAL(4,3) NOT NULL DEFAULT 0.800;
ALTER TABLE ai_config ADD COLUMN summary_keep_recent_turns INT NOT NULL DEFAULT 4;
ALTER TABLE ai_config ADD COLUMN token_estimate_chars_per_token DECIMAL(4,2) NOT NULL DEFAULT 1.50;

-- 2) 模型能力（CHAT 使用；未填则回退全局 max_context_tokens/max_output_tokens）
ALTER TABLE ai_model ADD COLUMN context_window_tokens INT NULL;
ALTER TABLE ai_model ADD COLUMN max_output_tokens INT NULL;

-- 3) 知识库：与 visibility 解耦，默认允许 App AI 检索（公开接口仍只查 PUBLIC）
ALTER TABLE ai_knowledge_base ADD COLUMN assistant_enabled TINYINT NOT NULL DEFAULT 1;
ALTER TABLE ai_knowledge_base ADD COLUMN rag_token_budget INT NOT NULL DEFAULT 3000;
ALTER TABLE ai_knowledge_base ADD COLUMN assistant_priority INT NOT NULL DEFAULT 0;

-- 4) 会话摘要与上下文计量
ALTER TABLE ai_conversation ADD COLUMN summary VARCHAR(4000) NULL;
ALTER TABLE ai_conversation ADD COLUMN summary_until_message_id BIGINT NULL;
ALTER TABLE ai_conversation ADD COLUMN summary_updated_at DATETIME NULL;
ALTER TABLE ai_conversation ADD COLUMN last_prompt_tokens INT NULL;
ALTER TABLE ai_conversation ADD COLUMN last_context_tokens INT NULL;
ALTER TABLE ai_conversation ADD COLUMN compact_count INT NOT NULL DEFAULT 0;

-- 5) 消息级 token 计量与引用来源
ALTER TABLE ai_message ADD COLUMN prompt_tokens INT NULL;
ALTER TABLE ai_message ADD COLUMN completion_tokens INT NULL;
ALTER TABLE ai_message ADD COLUMN sources VARCHAR(4000) NULL;
