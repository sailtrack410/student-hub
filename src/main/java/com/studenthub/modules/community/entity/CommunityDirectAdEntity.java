package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 官方直投广告（V66/V68）：不绑定社区账号，由管理端独立投放，按优先级/时间窗插入信息流。
 * status 只存配置态：DRAFT/ENABLED/DISABLED；已过 end_at 的 ENABLED 广告在响应中按 EXPIRED 展示。
 * review_status 存审核态（V68）：DRAFT/PENDING/APPROVED/REJECTED，App 信息流与广告位仅投放 APPROVED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_direct_ad")
public class CommunityDirectAdEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    /** 广告主 ID（V68），历史广告可为空 */
    private Long advertiserId;
    private String title;
    private String brandName;
    private String description;
    private String coverUrl;
    private String targetUrl;
    /** 跳转类型：IN_APP_WEBVIEW / APP_PAGE / EXTERNAL_BROWSER / DEEPLINK */
    private String landingType;
    /** 定向分区；NULL 表示不限分区 */
    private Long sectionId;
    /** 广告位：FEED / BANNER / SPLASH / SERVICE_HALL，当前信息流投放使用 FEED */
    private String slot;
    private Integer priority;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String status;
    /** 审核状态（V68）：DRAFT/PENDING/APPROVED/REJECTED */
    private String reviewStatus;
    /** 驳回原因（仅 REJECTED 时有值） */
    private String reviewReason;
    /** 最近一次提交审核时间 */
    private LocalDateTime submittedAt;
    /** 审核人 ID */
    private Long reviewedBy;
    /** 审核时间 */
    private LocalDateTime reviewedAt;
    /** 跳出统计窗口 N（秒），默认 5；统计接口可用参数覆盖重算 */
    private Integer bounceThresholdSeconds;
    private String ctaText;
    private String sponsoredLabel;
    /** 关联投放计划（V75 C13，可空） */
    private Long planId;
    /** 关联广告素材（V75 C13，可空；引用时要求素材审核通过） */
    private Long materialId;
}
