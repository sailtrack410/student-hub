package com.studenthub.modules.servicerequest.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 催单结果（需求清单 7.1）：urged=false 表示处于 2 小时限频冷却期。
 */
@Schema(description = "服务申请催单结果")
public record UrgeResponse(
        @Schema(description = "申请 ID") Long requestId,
        @Schema(description = "本次是否催单成功（false=冷却中）") boolean urged,
        @Schema(description = "距下次可催单秒数；urged=true 时为冷却周期 7200") Long nextUrgeAvailableSeconds,
        @Schema(description = "提示文案") String message) {
}
