package com.studenthub.modules.push.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 创作者档案（C10）：认证 / 等级 / 当前状态。
 *
 * <p>status 是处罚的派生结果（NORMAL/MUTED/BANNED），由处罚创建、撤销、过期时重算，
 * 不允许在页面直接改写，避免与 creator_penalty 记录不一致。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("creator_profile")
public class CreatorProfileEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private Long userId;
    private String displayName;
    /** NONE / PENDING / APPROVED / REJECTED / DISABLED */
    private String certificationStatus;
    private String certificationNote;
    /** 创作者等级 1-5 */
    private Integer level;
    private Integer score;
    /** NORMAL / MUTED / BANNED（派生字段） */
    private String status;
    private LocalDateTime mutedUntil;
    private String bannedReason;
    private LocalDateTime lastActiveAt;
    private String remark;
}
