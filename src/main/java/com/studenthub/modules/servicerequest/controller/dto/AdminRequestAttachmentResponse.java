package com.studenthub.modules.servicerequest.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端申请详情-工单附件（现场/完工照片，已签名）")
public record AdminRequestAttachmentResponse(
        @Schema(description = "附件名称，如「现场照片」「完工照片」") String label,
        @Schema(description = "附件类别：ARRIVAL_PHOTO / COMPLETION_PHOTO") String category,
        @Schema(description = "短时签名访问地址") String url) {
}
