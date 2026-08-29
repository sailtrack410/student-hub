package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 回复附带的动作/入口卡片，供小程序渲染为可点击跳转入口。
 */
@Schema(description = "AI 回复动作卡片")
public record AiActionResponse(
        @Schema(description = "动作类型 SERVICE_APPLY/REQUEST_DETAIL/REQUEST_LIST/SCHEDULE") String type,
        @Schema(description = "卡片文案") String label,
        @Schema(description = "服务项编码，可空") String itemCode,
        @Schema(description = "服务项 ID，可空") Long itemId,
        @Schema(description = "小程序跳转路径，如 /pages/service/apply?itemId=1") String url) {
}