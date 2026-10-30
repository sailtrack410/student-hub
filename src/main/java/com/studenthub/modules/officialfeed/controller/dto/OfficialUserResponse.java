package com.studenthub.modules.officialfeed.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "可作为发布主体的官方账号")
public record OfficialUserResponse(
        Long userId,
        String displayName,
        String officialTitle) {
}
