package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "身份审核材料登记（引用真实上传文件 fileId）")
public record IdentityEvidenceRequest(
        @Schema(description = "file_storage 文件 ID；先通过上传接口拿到，再由管理端登记")
        @NotNull Long fileId,
        @Schema(description = "材料类型：STUDENT_CARD/ID_CARD/ENROLLMENT_PROOF/TEACHER_CERT/WORK_BADGE/OTHER")
        @NotBlank @Size(max = 32) String materialType,
        @Schema(description = "材料说明，如「学生证正面」", nullable = true) @Size(max = 128) String materialName,
        @Schema(description = "备注", nullable = true) @Size(max = 255) String remark) {
}
