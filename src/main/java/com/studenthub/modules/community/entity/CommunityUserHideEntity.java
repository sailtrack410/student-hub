package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户帖子隐藏（「不感兴趣」）：仅过滤该用户的信息流；
 * 不影响分享、作者主页、搜索等其他入口进入帖子。取消=删行。
 */
@Data
@TableName("community_user_hide")
public class CommunityUserHideEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long campusId;
    private Long userId;
    private Long postId;
    private LocalDateTime createdAt;
}
