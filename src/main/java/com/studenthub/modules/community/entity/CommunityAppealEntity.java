package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 社区申诉：帖子/评论被隐藏、或账号被处罚后由本人提交，管理端人工复核。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_appeal")
public class CommunityAppealEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private Long userId;
    /** POST / COMMENT / SANCTION */
    private String targetType;
    private Long targetId;
    private String reason;
    /** 复核原因编码：MISJUDGED / FIXED / ACCOUNT / OTHER */
    private String reasonCode;
    /** 证明材料文件 ID（JSON 数组字符串） */
    private String evidenceFileIds;
    /** PENDING / APPROVED / REJECTED */
    private String status;
    private Long handledBy;
    private LocalDateTime handledAt;
    private String handleNote;
    /** 处理时限（V71）：为空时业务侧按 createdAt + 24h 计算 */
    private LocalDateTime dueAt;
}
