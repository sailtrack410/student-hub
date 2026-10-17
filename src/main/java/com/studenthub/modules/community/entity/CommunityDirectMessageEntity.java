package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_direct_message")
public class CommunityDirectMessageEntity extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long campusId;
    private Long senderId;
    private Long receiverId;
    private String content;
    private String imageFileIds;
    private String referenceType;
    private Long referenceId;
    private String referenceCard;
    private LocalDateTime readAt;
}
