package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 分区批量排序请求（C05）：按数组顺序写入 sortOrder=1..n。 */
@Schema(description = "分区批量排序请求")
public record AdminCommunitySectionReorderRequest(
        @Schema(description = "分区 ID 列表（按目标顺序）") @NotEmpty @Size(max = 100) List<Long> sectionIds) {
}
