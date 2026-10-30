package com.studenthub.modules.push.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 创作者处罚与申诉（C10）：WARN / MUTE / BAN。
 *
 * <p>状态机：ACTIVE → EXPIRED（到期）或 REVOKED（撤销）；
 * 申诉：NONE → PENDING → APPROVED / REJECTED，APPROVED 时自动撤销处罚。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("creator_penalty")
public class CreatorPenaltyEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private Long creatorId;
    private Long userId;
    /** WARN / MUTE / BAN */
    private String penaltyType;
    private String reason;
    private String evidenceUrl;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    /** ACTIVE / EXPIRED / REVOKED */
    private String status;
    /** NONE / PENDING / APPROVED / REJECTED */
    private String appealStatus;
    private String appealReason;
    private LocalDateTime appealAt;
    private String appealResult;
    private Long handledBy;
    private LocalDateTime handledAt;
}
