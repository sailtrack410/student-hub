package com.studenthub.modules.achievement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 用户勋章进度/获得记录：unlocked_at 非空即已解锁；current_progress 未达成也刷新供展示 x/threshold */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_achievement_badge")
public class UserAchievementBadgeEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private String badgeCode;
    private Integer currentProgress;
    private LocalDateTime unlockedAt;
    private Boolean notified;
}
