package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 广告素材（C13）：图片/视频/文本素材独立入库与审核，直投广告可引用已通过素材。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_ad_material")
public class CommunityAdMaterialEntity extends BaseEntity {

    public static final String REVIEW_PENDING = "PENDING";
    public static final String REVIEW_APPROVED = "APPROVED";
    public static final String REVIEW_REJECTED = "REJECTED";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private String materialName;
    /** IMAGE/VIDEO/TEXT */
    private String materialType;
    private String fileUrl;
    private String coverUrl;
    private String title;
    private String description;
    private Integer width;
    private Integer height;
    private Long sizeBytes;
    /** PENDING/APPROVED/REJECTED */
    private String reviewStatus;
    private String reviewReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    /** ENABLED/DISABLED */
    private String status;
}
