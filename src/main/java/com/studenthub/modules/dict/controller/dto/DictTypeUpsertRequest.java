package com.studenthub.modules.dict.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "字典类型新建/更新请求")
public record DictTypeUpsertRequest(
        @Schema(description = "字典编码（创建后不可修改）：大写字母开头，字母/数字/下划线，2-64 位")
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$",
                message = "字典编码需为大写字母开头的字母/数字/下划线组合")
        String typeCode,
        @Schema(description = "字典名称") @NotBlank @Size(max = 64) String typeName,
        @Schema(description = "备注") @Size(max = 255) String remark,
        @Schema(description = "状态：ENABLED/DISABLED，缺省 ENABLED") String status) {
}
