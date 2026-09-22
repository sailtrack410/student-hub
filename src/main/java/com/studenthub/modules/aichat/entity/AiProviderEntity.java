package com.studenthub.modules.aichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_provider")
public class AiProviderEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String providerCode;
    private String providerName;

    /** 供应商图标标识（lobe-icons 名，如 openai / anthropic / deepseek） */
    private String icon;

    private String baseUrl;

    /** 调用协议：OPENAI_CHAT / ANTHROPIC_MESSAGES / OPENAI_RESPONSES */
    private String protocol;

    /** 支持流式输出（SSE） */
    private Boolean streaming;

    private String apiKey;
    private Boolean enabled;
    private LocalDateTime lastCheckAt;
    private Boolean lastCheckOk;
    private String lastCheckResult;
}
