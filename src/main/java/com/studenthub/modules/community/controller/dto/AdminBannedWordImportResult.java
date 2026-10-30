package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 屏蔽词 Excel 导入结果 */
@Schema(description = "屏蔽词导入结果")
public record AdminBannedWordImportResult(
        @Schema(description = "读取到的数据行数") int total,
        @Schema(description = "新增数量") int imported,
        @Schema(description = "已存在跳过数量") int duplicated,
        @Schema(description = "格式无效数量（空词/超长）") int invalid) {
}
