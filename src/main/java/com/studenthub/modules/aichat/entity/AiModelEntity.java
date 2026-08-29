package com.studenthub.modules.aichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

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
}
