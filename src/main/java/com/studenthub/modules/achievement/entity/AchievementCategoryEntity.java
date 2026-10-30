package com.studenthub.modules.achievement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 勋章分类字典：分类名与排序随字典下发，改文案/顺序不需要发版 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("achievement_category")
public class AchievementCategoryEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String categoryCode;
    private String categoryName;
    private Integer sortOrder;
    private Boolean enabled;
}
