package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_section")
public class CommunitySectionEntity extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long campusId;
    private String sectionCode;
    private String sectionName;
    private String icon;
    private String description;
    private String bannerTitle;
    private String bannerSubtitle;
    private String bannerImage;
    private Integer sortOrder;
    private String status;
}
