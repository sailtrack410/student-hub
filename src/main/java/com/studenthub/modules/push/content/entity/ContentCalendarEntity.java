package com.studenthub.modules.push.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 内容日历（A03/C09）：活动 / 专题 / 推荐 / 促销排期。
 *
 * <p>与运营位的关系：一个排期可选绑定一个运营位（slot_id），也可独立存在（如纯活动预告）。
 * 状态：PLANNED（已排期）→ PUBLISHED（已上线）→ OFFLINE（已下线）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("content_calendar")
public class ContentCalendarEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private Long slotId;
    /** ACTIVITY / TOPIC / RECOMMEND / PROMOTION */
    private String entryType;
    private String title;
    private String summary;
    private String coverUrl;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    /** PLANNED / PUBLISHED / OFFLINE */
    private String status;
    private String owner;
    private String remark;
}
