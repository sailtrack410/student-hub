package com.studenthub.modules.dict.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "字典项")
public record DictItemResponse(
        @Schema(description = "主键") Long id,
        @Schema(description = "所属字典编码") String typeCode,
        @Schema(description = "字典项编码") String itemCode,
        @Schema(description = "显示名称") String itemLabel,
        @Schema(description = "字典值") String itemValue,
        @Schema(description = "排序（升序）") Integer sortOrder,
        @Schema(description = "状态：ENABLED/DISABLED") String status,
        @Schema(description = "扩展 JSON，如 {\"emergency\":true}") String extra,
        @Schema(description = "备注") String remark) {
}
