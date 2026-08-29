package com.studenthub.modules.aichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_default_model")
public class AiDefaultModelEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** CHAT 聊天 / VISION 图片识别 / ASR 语音识别 */
    private String typeCode;
    private Long modelId;
}
