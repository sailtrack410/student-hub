package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 社区数据概览（管理端社区详情页）。
 *
 * <p>时间范围 days 仅支持 7 / 30 天：trend 返回对应天数；活跃用户、分区帖子数、话题 Top N
 * 均按所选区间统计；举报/申诉/隐藏/屏蔽词为当前快照，不随区间变化（导出文件中已标注口径）。</p>
 */
@Schema(description = "社区数据概览")
public record AdminCommunityStatsResponse(
        @Schema(description = "帖子总数（正常展示）") long totalPosts,
        @Schema(description = "今日新增帖子") long todayPosts,
        @Schema(description = "评论总数（正常展示）") long totalComments,
        @Schema(description = "今日新增评论") long todayComments,
        @Schema(description = "近 7 天活跃用户（历史字段，兼容保留；值与 activeUsers 相同）") long activeUsers7d,
        @Schema(description = "待处理举报") long pendingReports,
        @Schema(description = "隐藏帖子") long hiddenPosts,
        @Schema(description = "隐藏评论") long hiddenComments,
        @Schema(description = "待处理申诉") long pendingAppeals,
        @Schema(description = "屏蔽词数量") long bannedWordCount,
        @Schema(description = "发帖趋势（按所选区间逐日）") List<DayCount> trend,
        @Schema(description = "分区帖子分布（按所选区间）") List<SectionCount> sections,
        @Schema(description = "统计区间天数（7/30）") int days,
        @Schema(description = "活跃用户（所选区间内发帖/评论去重，含今日）") long activeUsers,
        @Schema(description = "区间开始日期 yyyy-MM-dd（含）") String rangeStart,
        @Schema(description = "区间结束日期 yyyy-MM-dd（含）") String rangeEnd,
        @Schema(description = "区间内发帖数（正常展示）") long rangePosts,
        @Schema(description = "区间内帖子话题 Top N（按帖子 topics 标签统计）") List<TopicCount> topics) {

    /** 单日发帖数（date=yyyy-MM-dd） */
    public record DayCount(String date, long count) {
    }

    /** 分区帖子数 */
    public record SectionCount(Long sectionId, String sectionName, long count) {
    }

    /** 话题帖子数 */
    public record TopicCount(String topic, long count) {
    }
}
