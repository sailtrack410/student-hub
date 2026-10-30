package com.studenthub.modules.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 独立身份审核记录（V77）：每次通过/拒绝写一条，供管理端时间线回溯。
 *
 * <p>与 sys_audit_log 的区别：审计记录通用写操作，本表承载业务语义
 * （前后状态、拒绝原因码、审核时材料快照），可直接展示给运营人员。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_identity_review")
public class UserIdentityReviewEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private Long userId;
    /** APPROVE / REJECT */
    private String action;
    private String fromStatus;
    private String toStatus;
    /** 拒绝原因模板编码（自定义原因时可为空） */
    private String reasonCode;
    private String reason;
    /** 审核当时的材料快照：`材料ID:材料名` 逗号拼接，便于学生申诉时核对 */
    private String evidenceSnapshot;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewedAt;
}
