package com.studenthub.modules.home.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 首页综合仪表盘（API需求清单 4.1 BFF）：冷启动单请求聚合。
 */
@Schema(description = "首页仪表盘")
public record HomeDashboardResponse(
        @Schema(description = "问候语，如 早安，李同学") String greeting,
        @Schema(description = "学业信息条") AcademicInfo academicInfo,
        @Schema(description = "下一节/当前课程；无课为 null") NextSchedule nextSchedule,
        @Schema(description = "最新一条 APP 校园公告；无公告为 null") AnnouncementCard announcement,
        @Schema(description = "最紧迫的进行中申请；无则为 null") InProgressRequest inProgressRequest,
        @Schema(description = "未读消息总数（流程通知+互动）") long unreadMessageCount) {

    @Schema(description = "学业信息条")
    public record AcademicInfo(
            @Schema(description = "校区名") String campusName,
            @Schema(description = "星期，如 周四") String weekDay,
            @Schema(description = "教学周次（不在学期内为 0）") int academicWeek,
            @Schema(description = "拼接展示串") String formattedDate) {
    }

    @Schema(description = "下一节课程卡")
    public record NextSchedule(
            @Schema(description = "课程名") String courseName,
            @Schema(description = "教师") String teacher,
            @Schema(description = "地点") String location,
            @Schema(description = "起始节") int startSection,
            @Schema(description = "结束节") int endSection,
            @Schema(description = "时间段") String timeRange,
            @Schema(description = "状态：ONGOING 当前节 / UPCOMING 下一节") String status) {
    }

    @Schema(description = "公告卡")
    public record AnnouncementCard(
            @Schema(description = "公告 ID") Long id,
            @Schema(description = "标题") String title,
            @Schema(description = "类型文案：通知/要闻/活动") String tag,
            @Schema(description = "前端跳转路径") String url) {
    }

    @Schema(description = "进行中申请卡")
    public record InProgressRequest(
            @Schema(description = "申请 ID") Long requestId,
            @Schema(description = "工单号（已派单时）") String orderNo,
            @Schema(description = "服务项名") String itemName,
            @Schema(description = "申请状态") String currentStatus,
            @Schema(description = "提交时间") String submittedAt,
            String flowType, String businessStatus, String statusLabel) {
    }
}
