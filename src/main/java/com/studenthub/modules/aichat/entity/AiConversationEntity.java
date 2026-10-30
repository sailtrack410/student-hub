package com.studenthub.modules.aichat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversation")
public class AiConversationEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private String title;
    private String status;

    // ---- 上下文压缩状态（V54）----
    private String summary;
    private Long summaryUntilMessageId;
    private java.time.LocalDateTime summaryUpdatedAt;
    private Integer lastPromptTokens;
    private Integer lastContextTokens;
    private Integer compactCount;
}
