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
    private String baseUrl;
    private String apiKey;
    private Boolean enabled;
    private LocalDateTime lastCheckAt;
    private Boolean lastCheckOk;
    private String lastCheckResult;
}
