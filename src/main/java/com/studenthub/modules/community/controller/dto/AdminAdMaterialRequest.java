package com.studenthub.modules.community.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 素材新建/更新请求（C13）。 */
@Schema(description = "广告素材请求")
public record AdminAdMaterialRequest(
        @Schema(description = "素材名称") @NotBlank @Size(max = 100) String materialName,
        @Schema(description = "素材类型：IMAGE/VIDEO/TEXT") @NotBlank String materialType,
        @Schema(description = "素材文件地址（图片/视频 URL）") @Size(max = 512) String fileUrl,
        @Schema(description = "封面图地址（视频/文本素材可选）") @Size(max = 512) String coverUrl,
        @Schema(description = "素材标题（文本素材必填）") @Size(max = 100) String title,
        @Schema(description = "素材描述/正文") @Size(max = 500) String description,
        @Schema(description = "宽（像素，可空）") Integer width,
        @Schema(description = "高（像素，可空）") Integer height,
        @Schema(description = "文件大小（字节，可空）") Long sizeBytes) {
}
