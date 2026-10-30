package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 处罚 / 理由 / 申诉回复模板新建与更新请求（C03/C04）。 */
@Schema(description = "社区处理模板请求")
public record AdminModerationTemplateRequest(
        @Schema(description = "模板类型：REPORT_NOTE 举报处理说明 / APPEAL_REPLY 申诉回复")
        @NotBlank String templateType,
        @Schema(description = "模板名称") @NotBlank @Size(max = 64) String templateName,
        @Schema(description = "关联举报原因编码（可空）：AD/ABUSE/PORN/POLITICS/PRIVACY/OTHER")
        @Size(max = 32) String reasonCode,
        @Schema(description = "模板正文，插入到处理说明/回复中") @NotBlank @Size(max = 1000) String content,
        @Schema(description = "排序值，越小越靠前") Integer sortOrder,
        @Schema(description = "状态：ENABLED/DISABLED") String status) {
}
