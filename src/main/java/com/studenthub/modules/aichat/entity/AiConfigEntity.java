package com.studenthub.modules.aichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 动态配置：单行表（id 恒为 1），由管理后台读写，保存后即时生效。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_config")
public class AiConfigEntity extends BaseEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Boolean enabled;
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String model;
    private Integer connectTimeoutSeconds;
    private Integer readTimeoutSeconds;
    private Integer maxInputCharacters;
    private Integer hourlyMessageLimit;
    private Integer historyWindow;
    private Integer maxMessageCharacters;
}