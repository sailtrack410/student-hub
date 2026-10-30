package com.studenthub.modules.push.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "推送触达统计（口径：sent=已交通道；success=通道成功；delivered=确认到达；clicked=点击）")
public record PushStatsResponse(
        @Schema(description = "统计窗口起始时间") String from,
        @Schema(description = "统计窗口结束时间") String to,
        @Schema(description = "投递明细总数（含未配置/跳过）") long total,
        @Schema(description = "成功交给通道数") long success,
        @Schema(description = "失败数") long failed,
        @Schema(description = "确认到达数") long delivered,
        @Schema(description = "点击数") long clicked,
        @Schema(description = "通道未配置数（不计成功/失败）") long notConfigured,
        @Schema(description = "频控跳过数") long skipped,
        @Schema(description = "到达率 = delivered / success，无数据时 null") Double deliverRate,
        @Schema(description = "点击率 = clicked / delivered，无数据时 null") Double clickRate,
        @Schema(description = "按渠道拆分") List<ChannelStat> channels,
        @Schema(description = "通道能力状态（哪些通道真的能发）") List<ChannelCapability> capabilities) {

    @Schema(description = "单渠道统计")
    public record ChannelStat(String channel, long total, long success, long failed,
                              long delivered, long clicked, long notConfigured, long skipped) {
    }

    @Schema(description = "通道能力")
    public record ChannelCapability(String channel, boolean configured, String reason) {
    }
}
