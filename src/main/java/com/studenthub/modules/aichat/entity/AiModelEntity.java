package com.studenthub.modules.aichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_model")
public class AiModelEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long providerId;
    private String providerModelId;
    /** CHAT 聊天模型 / VISION 图片识别 / ASR 语音识别 */
    private String modelType;
    /** 能力标签，逗号分隔：stream/vision/audio/tools/structured/reasoning */
    private String capabilities;
    private Boolean enabled;
    /** CHAT 模型上下文窗口（tokens），空则回退全局 max_context_tokens */
    private Integer contextWindowTokens;
    /** CHAT 模型最大输出（tokens），空则回退全局 max_output_tokens */
    private Integer maxOutputTokens;
    /** 成本估算：每 1K prompt tokens 单价；NULL 表示未配置（成本返回 null） */
    @TableField("input_price_per_1k")
    private BigDecimal inputPricePer1k;
    /** 成本估算：每 1K completion tokens 单价；NULL 表示未配置（成本返回 null） */
    @TableField("output_price_per_1k")
    private BigDecimal outputPricePer1k;
}
