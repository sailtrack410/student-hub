package com.studenthub.modules.push.content.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Schema(description = "管理端内容日历创建/更新")
public record ContentCalendarRequest(
        @Schema(description = "绑定运营位 ID（可选）") Long slotId,
        @Schema(description = "类型：ACTIVITY/TOPIC/RECOMMEND/PROMOTION") @NotBlank String entryType,
        @Schema(description = "标题") @NotBlank @Size(max = 200) String title,
        @Schema(description = "摘要") @Size(max = 1000) String summary,
        @Schema(description = "封面 URL") @Size(max = 500) String coverUrl,
        @Schema(description = "开始时间") LocalDateTime startAt,
        @Schema(description = "结束时间") LocalDateTime endAt,
        @Schema(description = "负责人") @Size(max = 64) String owner,
        @Schema(description = "备注") @Size(max = 500) String remark) {
}
