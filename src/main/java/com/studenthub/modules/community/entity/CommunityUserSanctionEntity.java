package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 社区用户处罚：限制发言或禁止访问，到期由 end_at 自然失效 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_user_sanction")
public class CommunityUserSanctionEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private Long userId;
    /** MUTE 限制发言 / BAN 禁止访问社区 */
    private String sanctionType;
    private String reason;
    /** 触发处罚的举报 ID */
    private Long reportId;
    private LocalDateTime startAt;
    /** 到期时间，NULL 表示长期有效 */
    private LocalDateTime endAt;
    /** ACTIVE/REVOKED/EXPIRED */
    private String status;
}
