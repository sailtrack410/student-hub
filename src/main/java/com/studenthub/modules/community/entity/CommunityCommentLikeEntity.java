package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("community_comment_like")
public class CommunityCommentLikeEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long commentId;
    private Long userId;
}
