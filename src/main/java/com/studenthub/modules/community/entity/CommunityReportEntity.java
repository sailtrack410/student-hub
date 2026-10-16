package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 社区举报记录：保存举报时刻的内容快照，作者删改后仍可追溯 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_report")
public class CommunityReportEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private Long reporterId;
    /** POST/COMMENT/MESSAGE/USER */
    private String targetType;
    private Long targetId;
    /** 被举报内容作者，便于按作者聚合与处罚 */
    private Long targetAuthorId;
    /** AD/ABUSE/PORN/POLITICS/PRIVACY/OTHER */
    private String reasonCode;
    private String reasonDetail;
    /** 举报时刻的内容快照（JSON） */
    private String snapshot;
    /** PENDING/ACCEPTED/REJECTED */
    private String status;
    private Long handledBy;
    private LocalDateTime handledAt;
    private String handleNote;
}
