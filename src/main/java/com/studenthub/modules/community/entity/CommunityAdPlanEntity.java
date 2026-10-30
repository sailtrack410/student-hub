package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 投放计划（C13）：一个计划下可挂多条直投广告，统一排期/预算/状态。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_ad_plan")
public class CommunityAdPlanEntity extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    /** 关联广告主（可空，历史/自投计划可不绑定） */
    private Long advertiserId;
    private String planName;
    /** 投放目标：AWARENESS/TRAFFIC/CONVERSION */
    private String objective;
    /** 广告位：FEED/BANNER/SPLASH/SERVICE_HALL */
    private String slot;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    /** 总预算（分，可空表示不限） */
    private Long budgetCents;
    /** 日预算（分，可空） */
    private Long dailyBudgetCents;
    /** DRAFT/ENABLED/DISABLED */
    private String status;
    private String remark;
}
