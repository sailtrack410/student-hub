package com.studenthub.modules.screen.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 公开大屏 · 单号查进度：仅含脱敏后的办理时间线，
 * 不返回任何个人表单数据与操作人身份。
 */
@Schema(description = "公开单号进度")
public record ScreenOrderProgressResponse(
        String orderNo,
        String requestNo,
        String itemName,
        String status,
        String statusText,
        String submittedAt,
        List<TimelinePoint> timeline) {

    public record TimelinePoint(String time, String actionText, String note) {
    }
}