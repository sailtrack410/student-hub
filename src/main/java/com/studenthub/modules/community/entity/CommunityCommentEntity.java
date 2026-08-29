package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_comment")
public class CommunityCommentEntity extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long postId;
    private Long authorId;
    private Long parentCommentId;
    private String content;
    private String imageFileIds;
    private Integer likeCount;
    private Integer pinned;
    private String status;
}
