package com.studenthub.modules.achievement.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 佩戴/卸下勋章请求：action=WEAR/UNWEAR；缺省时按 badgeCode 是否为空推断（兼容旧客户端） */
@Schema(description = "佩戴/卸下勋章请求")
public record WearBadgeRequest(
        @Schema(description = "勋章编码；action=UNWEAR 时可为空", nullable = true) @Size(max = 64) String badgeCode,
        @Schema(description = "动作：WEAR 佩戴 / UNWEAR 卸下；缺省时 badgeCode 非空=佩戴、为空=卸下",
                nullable = true) @Size(max = 16) String action) {
}
