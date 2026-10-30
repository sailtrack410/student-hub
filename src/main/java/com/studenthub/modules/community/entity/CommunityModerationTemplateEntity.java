package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 社区处理模板：举报处理说明 / 申诉回复 / 处罚理由（V71）。
 * campus_id=0 为平台内置模板（只读），>0 为校区自定义模板。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_moderation_template")
public class CommunityModerationTemplateEntity extends BaseEntity {

    public static final String TYPE_REPORT_NOTE = "REPORT_NOTE";
    public static final String TYPE_APPEAL_REPLY = "APPEAL_REPLY";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private String templateType;
    private String templateName;
    private String reasonCode;
    private String content;
    private Integer sortOrder;
    private String status;
}
