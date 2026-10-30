package com.studenthub.modules.officialfeed.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 版权确认请求（C08）：管理端确认「可转载/已注明来源」后才能进入发布链路。 */
@Schema(description = "官方通知版权确认请求")
public record AdminOfficialFeedCopyrightRequest(
        @Schema(description = "是否确认版权合规") boolean confirmed) {
}
