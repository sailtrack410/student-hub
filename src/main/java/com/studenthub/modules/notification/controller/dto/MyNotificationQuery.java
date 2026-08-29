package com.studenthub.modules.notification.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "我的通知分页查询")
public record MyNotificationQuery(
        @Schema(description = "页码，从 1 开始") @Min(1) Integer pageNum,
        @Schema(description = "每页条数，最大 50") @Min(1) @Max(50) Integer pageSize,
        @Schema(description = "是否只看未读", nullable = true) Boolean unreadOnly,
        @Schema(description = "是否只看社区通知", nullable = true) Boolean communityOnly) {

    public int pageNumOrDefault() {
        return pageNum == null ? 1 : pageNum;
    }

    public int pageSizeOrDefault() {
        return pageSize == null ? 10 : pageSize;
    }
}
