package com.studenthub.modules.screen.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "公告创建/更新请求")
public record NoticeUpsertRequest(
        @Schema(description = "类型：NOTICE/NEWS/ACTIVITY", allowableValues = {"NOTICE", "NEWS", "ACTIVITY"})
        @NotBlank String noticeType,
        @Schema(description = "标题") @NotBlank @Size(max = 128) String title,
        @Schema(description = "摘要", nullable = true) @Size(max = 512) String summary,
        @Schema(description = "正文（纯文本，保留换行；最大 20000 字）", nullable = true) @Size(max = 20000) String content,
        @Schema(description = "封面图地址（先调 POST /api/admin/notices/cover 上传）", nullable = true)
        @Size(max = 512) String coverImageUrl,
        @Schema(description = "发布日期 yyyy-MM-dd（公告列表展示用）", nullable = true) LocalDate publishDate,
        @Schema(description = "投放渠道：SCREEN 终端大屏 / APP 校园公告", allowableValues = {"SCREEN", "APP"})
        @NotBlank String channel,
        @Schema(description = "发布动作：PUBLISH 立即发布 / SCHEDULE 定时 / DRAFT 存草稿 / OFFLINE 下线；缺省 PUBLISH",
                nullable = true) String action,
        @Schema(description = "定时发布时间（action=SCHEDULE 时必填，到点自动上线）", nullable = true) LocalDateTime publishAt,
        @Schema(description = "排序值（终端大屏轮播顺序）", nullable = true) Integer sortOrder,
        @Schema(description = "发布方署名，如 保卫处", nullable = true) @Size(max = 64) String author,
        @Schema(description = "是否启用（下线开关，仅 SCREEN 渠道轮播用）", nullable = true) Boolean enabled) {
}
