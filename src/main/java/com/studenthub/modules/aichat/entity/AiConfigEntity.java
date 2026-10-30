package com.studenthub.modules.aichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 全局设置：单行表（id 恒为 1），只保留总开关与超时/限流；
 * 供应商与模型连接信息由 V17 起的 ai_provider/ai_model/ai_default_model 承载。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_config")
public class AiConfigEntity extends BaseEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Boolean enabled;
    private Integer connectTimeoutSeconds;
    private Integer readTimeoutSeconds;
    private Integer maxInputCharacters;
    private Integer hourlyMessageLimit;
    private Integer historyWindow;
    private Integer maxMessageCharacters;

    // ---- 上下文策略（V54）----
    private Integer maxContextTokens;
    private Integer contextSafetyMarginTokens;
    private Integer maxOutputTokens;
    private java.math.BigDecimal historyTokenRatio;
    private java.math.BigDecimal ragTokenRatio;
    private java.math.BigDecimal attachmentTokenRatio;
    private Boolean summaryEnabled;
    private java.math.BigDecimal summaryTriggerRatio;
    private Integer summaryKeepRecentTurns;
    private java.math.BigDecimal tokenEstimateCharsPerToken;
}
