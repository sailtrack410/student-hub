package com.studenthub.modules.push.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 内容运营位（A03/C09）：Banner / 弹窗 / 推荐位 / 专题 / 活动。
 *
 * <p>投放生效判定以 status=ONLINE 且当前时间落在 [startAt, endAt] 为准；
 * 未填时间视为长期有效。slot_code 在同一校区内唯一。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("content_slot")
public class ContentSlotEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private String slotCode;
    private String slotName;
    /** BANNER / POPUP / RECOMMEND / TOPIC / ACTIVITY */
    private String slotType;
    /** 展示位置：HOME_TOP / HOME_POPUP / SERVICE_HALL / COMMUNITY_FEED / PROFILE */
    private String position;
    private String title;
    private String subtitle;
    private String imageUrl;
    /** POST / ACTIVITY / TOPIC / URL / NONE */
    private String targetType;
    private String targetValue;
    private Integer weight;
    private Integer sortOrder;
    /** DRAFT / ONLINE / OFFLINE */
    private String status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String remark;
}
