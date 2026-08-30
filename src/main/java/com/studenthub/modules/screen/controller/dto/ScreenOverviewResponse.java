package com.studenthub.modules.screen.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 公开数据大屏聚合视图：仅含脱敏的运营统计与公开动态，
 * 不含任何个人身份数据（无姓名/手机号/学号/openId）。
 */
@Schema(description = "公开大屏运营数据")
public record ScreenOverviewResponse(
        @Schema(description = "生成时间 yyyy-MM-dd HH:mm:ss") String generatedAt,
        @Schema(description = "用户统计") UserStats users,
        @Schema(description = "服务申请统计") RequestStats requests,
        @Schema(description = "工单统计") OrderStats orders,
        @Schema(description = "近 7 日趋势（按天）") List<TrendPoint> trend,
        @Schema(description = "服务热度 TOP（按申请量）") List<ServiceHeat> topServices,
        @Schema(description = "社区互动统计") CommunityStats community,
        @Schema(description = "AI 助手统计") AiStats ai,
        @Schema(description = "最新办结动态（已脱敏）") List<RecentCompleted> recentCompleted) {

    public record UserStats(long total, long students, long todayNew) {
    }

    /** statusDist：SUBMITTED/PROCESSING/COMPLETED/REJECTED/CANCELLED */
    public record RequestStats(long total, long today, long inProgress,
                               java.util.Map<String, Long> statusDist) {
    }

    /** processing = 待派发 + 已派发 + 处理中 */
    public record OrderStats(long total, long completed, long processing, long completedToday) {
    }

    public record TrendPoint(String date, String weekday, long submitted, long completed) {
    }

    public record ServiceHeat(String itemName, long count) {
    }

    public record CommunityStats(long posts, long comments, long likes, long messages) {
    }

    public record AiStats(long conversations, long answers, long todayAnswers) {
    }

    public record RecentCompleted(String itemName, String completedAt) {
    }
}