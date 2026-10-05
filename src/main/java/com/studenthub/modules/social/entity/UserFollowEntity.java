package com.studenthub.modules.social.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户关注关系（API需求清单 2.3）：(follower, followee) 唯一；互关=双向两行。
 */
@Data
@TableName("user_follow")
public class UserFollowEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long campusId;
    private Long followerId;
    private Long followeeId;
    private LocalDateTime createdAt;
}
