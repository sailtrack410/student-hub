package com.studenthub.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community_post")
public class CommunityPostEntity extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long campusId;
    private Long sectionId;
    private Long authorId;
    private String title;
    private String content;
    /** DYNAMIC 动态 / ARTICLE 文章 */
    private String postType;
    /** 文章图文混排块 JSON：[{type:TEXT|IMAGE,text,fileId}]，动态帖子为 null */
    private String contentBlocks;
    private String imageFileIds;
    private String topics;
    private Integer likeCount;
    private Integer commentCount;
    private String status;
    /** 隐藏原因：BANNED_WORD / MANUAL / REPORT；正常为 null */
    private String hiddenReason;
    private String locationName;
    private String locationAddress;
    private java.math.BigDecimal latitude;
    private java.math.BigDecimal longitude;
    private String poiId;
    private java.time.LocalDateTime hiddenAt;
    private Integer reportCount;
    /** 置顶（V75 C12）：1 = 置顶，信息流优先展示；列名 feed_pinned 避免与历史 pinned 语义冲突 */
    private Integer feedPinned;
    /** 置顶时间（V75） */
    private java.time.LocalDateTime pinnedAt;
    /** 置顶操作人（V75） */
    private Long pinnedBy;
}
