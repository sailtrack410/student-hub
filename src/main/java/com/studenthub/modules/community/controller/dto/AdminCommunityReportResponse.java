package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端社区举报详情")
public record AdminCommunityReportResponse(
        @Schema(description = "举报 ID") Long id,
        @Schema(description = "举报对象类型") String targetType,
        @Schema(description = "举报对象 ID") Long targetId,
        @Schema(description = "被举报内容摘要（帖子标题或评论/私信内容）") String targetTitle,
        @Schema(description = "被举报内容作者 ID") Long targetAuthorId,
        @Schema(description = "被举报内容作者昵称") String targetAuthorName,
        @Schema(description = "举报人 ID") Long reporterId,
        @Schema(description = "举报人昵称") String reporterName,
        @Schema(description = "举报原因编码") String reasonCode,
        @Schema(description = "举报补充说明") String reasonDetail,
        @Schema(description = "举报时刻的内容快照（JSON，作者删改后仍可追溯）") String snapshot,
        @Schema(description = "状态：PENDING/ACCEPTED/REJECTED") String status,
        @Schema(description = "处理说明") String handleNote,
        @Schema(description = "处理人 ID") Long handledBy,
        @Schema(description = "处理时间") String handledAt,
        @Schema(description = "举报时间") String createdAt) {
}
