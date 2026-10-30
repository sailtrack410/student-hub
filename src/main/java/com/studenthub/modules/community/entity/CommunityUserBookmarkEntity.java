package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户帖子收藏：服务端权威存储，唯一键(user_id, post_id)防重，取消=删行。 */
@Data
@TableName("community_user_bookmark")
public class CommunityUserBookmarkEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campusId;
    private Long userId;
    private Long postId;
    private LocalDateTime createdAt;
}
