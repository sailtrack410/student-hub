package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 社区屏蔽词：发布/评论时命中即自动隐藏内容（HIDDEN），作者可申诉。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_banned_word")
public class CommunityBannedWordEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private String word;
    private String remark;
    private String status;
    /** 匹配方式（V71）：LITERAL 字面量 / REGEX 正则 */
    private String matchType;
    /** 命中次数（V71） */
    private Integer hitCount;
    /** 最近命中时间（V71） */
    private LocalDateTime lastHitAt;
}
