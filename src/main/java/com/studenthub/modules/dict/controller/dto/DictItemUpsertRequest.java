package com.studenthub.modules.dict.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "字典项新建/更新请求")
public record DictItemUpsertRequest(
        @Schema(description = "所属字典编码（创建时必填；更新时不可修改）") @Size(max = 64) String typeCode,
        @Schema(description = "字典项编码（创建后不可修改）") @NotBlank @Size(max = 64) String itemCode,
        @Schema(description = "显示名称") @NotBlank @Size(max = 128) String itemLabel,
        @Schema(description = "字典值，缺省与显示名称一致") @Size(max = 255) String itemValue,
        @Schema(description = "排序，缺省 0") Integer sortOrder,
        @Schema(description = "状态：ENABLED/DISABLED，缺省 ENABLED") String status,
        @Schema(description = "扩展 JSON（可为空；非空时必须是合法 JSON）") String extra,
        @Schema(description = "备注") @Size(max = 255) String remark) {
}
