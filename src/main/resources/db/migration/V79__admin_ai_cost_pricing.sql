-- V79：AI 成本看板（I01）所需模型单价。
-- 单位：每 1K tokens 的配置金额（币种以供应商定价为准，本平台不做汇率换算）；
-- 未配置（NULL）时成本接口返回 null，不伪造金额。字段 nullable，H2/MySQL 兼容。
ALTER TABLE ai_model ADD COLUMN input_price_per_1k DECIMAL(12, 6) NULL;
ALTER TABLE ai_model ADD COLUMN output_price_per_1k DECIMAL(12, 6) NULL;
