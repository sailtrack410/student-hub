package com.studenthub.modules.officialfeed.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.studenthub.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 已抓取通知：source_url 唯一用于去重，post_id 指向生成的社区帖子。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("official_feed_article")
public class OfficialFeedArticleEntity extends BaseEntity {

    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_FAILED = "FAILED";
    /** 单篇 URL 导入后的草稿（C08）：可预览/编辑/定时，确认版权后才能发布 */
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String MODE_CRAWL = "CRAWL";
    public static final String MODE_MANUAL_URL = "MANUAL_URL";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sourceId;
    private String sourceUrl;
    private String title;
    private LocalDateTime publishedAt;
    private String contentHash;
    private Long postId;
    private String postType;
    private Integer imageCount;
    private String status;
    private String errorMessage;
    private LocalDateTime fetchedAt;
    /** 图文块 JSON（与社区文章 contentBlocks 同格式；V71） */
    private String contentBlocks;
    /** 纯文本正文（预览/编辑/发布使用；V71） */
    private String plainText;
    /** 封面地址（V71） */
    private String coverUrl;
    /** 导入方式：CRAWL 定时抓取 / MANUAL_URL 单篇 URL 导入（V71） */
    private String importMode;
    /** 版权确认（V71）：未确认不允许发布 */
    private Boolean copyrightConfirmed;
    /** 定时发布时间（V71） */
    private LocalDateTime scheduledAt;
    /** 重试次数（V71） */
    private Integer retryCount;
}
