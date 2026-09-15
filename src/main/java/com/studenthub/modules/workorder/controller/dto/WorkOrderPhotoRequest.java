package com.studenthub.modules.workorder.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "工单拍照打卡请求")
public record WorkOrderPhotoRequest(
        @Schema(description = "已上传的图片文件 ID") @NotNull Long fileId) {
}
