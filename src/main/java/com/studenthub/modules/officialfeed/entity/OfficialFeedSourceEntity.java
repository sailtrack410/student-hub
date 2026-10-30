package com.studenthub.modules.officialfeed.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 官方通知抓取来源：列表页地址、发布分区与发布主体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("official_feed_source")
public class OfficialFeedSourceEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long campusId;
    private String sourceName;
    private String listUrl;
    private Long sectionId;
    private Long authorId;
    /** 列表页最多翻页数（含首页） */
    private Integer maxPages;
    /** 单次同步最多发布篇数，避免首次回填刷屏 */
    private Integer maxArticles;
    /** 超过该天数的旧通知只记录不发布 */
    private Integer maxAgeDays;
    private Boolean enabled;
    private LocalDateTime lastFetchAt;
    private String lastStatus;
}
