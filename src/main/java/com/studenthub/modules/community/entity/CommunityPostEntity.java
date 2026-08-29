package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_post")
public class CommunityPostEntity extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long campusId;
    private Long sectionId;
    private Long authorId;
    private String title;
    private String content;
    private String imageFileIds;
    private String topics;
    private Integer likeCount;
    private Integer commentCount;
    private String status;
}
