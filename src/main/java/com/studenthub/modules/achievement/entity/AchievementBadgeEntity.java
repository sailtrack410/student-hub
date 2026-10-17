package com.studenthub.modules.achievement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 勋章字典：目录与规则数据化存表，新增勋章只需插一行数据 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("achievement_badge")
public class AchievementBadgeEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String badgeCode;
    private String badgeName;
    private String category;
    private String rarity;
    private String description;
    private String metricCode;
    private Integer metricThreshold;
    private Integer rewardExp;
    private Integer sortOrder;
    private Boolean enabled;
}
