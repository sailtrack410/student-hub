package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 批量处置结果：请求数 / 成功数 / 跳过数 / 逐条跳过原因（最多回传 20 条）。 */
@Schema(description = "批量处置结果")
public record AdminCommunityBatchResult(
        @Schema(description = "请求条数") int requested,
        @Schema(description = "成功条数") int updated,
        @Schema(description = "跳过条数") int skipped,
        @Schema(description = "跳过原因（最多 20 条）") List<String> skippedReasons) {
}
