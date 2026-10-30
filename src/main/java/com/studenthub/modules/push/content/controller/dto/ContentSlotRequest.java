package com.studenthub.modules.push.content.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Schema(description = "管理端内容运营位创建/更新")
public record ContentSlotRequest(
        @Schema(description = "运营位编码，创建后不可修改")
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "编码需以大写字母开头，仅含大写字母/数字/下划线")
        String slotCode,
        @Schema(description = "名称") @NotBlank @Size(max = 100) String slotName,
        @Schema(description = "类型：BANNER/POPUP/RECOMMEND/TOPIC/ACTIVITY") @NotBlank String slotType,
        @Schema(description = "展示位置：HOME_TOP/HOME_POPUP/SERVICE_HALL/COMMUNITY_FEED/PROFILE") @NotBlank String position,
        @Schema(description = "主标题") @NotBlank @Size(max = 200) String title,
        @Schema(description = "副标题") @Size(max = 500) String subtitle,
        @Schema(description = "图片 URL（BANNER/POPUP 建议必填）") @Size(max = 500) String imageUrl,
        @Schema(description = "跳转类型：POST/ACTIVITY/TOPIC/URL/NONE") @NotBlank String targetType,
        @Schema(description = "跳转目标：URL 或业务 ID") @Size(max = 200) String targetValue,
        @Schema(description = "权重（越大越靠前）") Integer weight,
        @Schema(description = "排序值") Integer sortOrder,
        @Schema(description = "开始时间，留空表示立即生效") LocalDateTime startAt,
        @Schema(description = "结束时间，留空表示长期") LocalDateTime endAt,
        @Schema(description = "备注") @Size(max = 500) String remark) {
}
