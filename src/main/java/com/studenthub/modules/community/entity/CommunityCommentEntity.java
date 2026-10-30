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
    /** 隐藏原因：BANNED_WORD / REPORT_LIMIT / MANUAL；正常为 null */
    private String hiddenReason;
    private Integer reportCount;
    /** 排序权重：点赞 +1、举报 -3（热门排序用） */
    private Integer weight;
}
