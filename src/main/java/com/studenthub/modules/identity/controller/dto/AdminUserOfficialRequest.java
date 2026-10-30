package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "设置/取消官方账号")
public record AdminUserOfficialRequest(
        @Schema(description = "是否官方账号") @NotNull Boolean official,
        @Schema(description = "官方头衔（设为官方时必填），如 教务处") @Size(max = 64) String officialTitle) {
}
