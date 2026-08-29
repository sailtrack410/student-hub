package com.studenthub.modules.aichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_message")
public class AiMessageEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long conversationId;
    /** USER / ASSISTANT */
    private String role;
    private String content;
    /** 动作卡片 JSON 数组（仅 ASSISTANT 消息，可空） */
    private String actions;
    private String model;
    private String promptVersion;
    private Integer tokenUsage;
    private Integer costMs;
    private String errorCode;
    /** OK / FAILED */
    private String status;
}
