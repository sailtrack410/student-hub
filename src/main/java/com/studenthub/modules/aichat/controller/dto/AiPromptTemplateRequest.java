package com.studenthub.modules.aichat.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "AI 提示词模板创建/更新（内容变更请新建版本）")
public record AiPromptTemplateRequest(
        @Schema(description = "提示词编码，创建后不可修改；同名多版本")
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "编码需以大写字母开头，仅含大写字母/数字/下划线")
        String promptCode,
        @Schema(description = "名称") @NotBlank @Size(max = 100) String promptName,
        @Schema(description = "场景：CAMPUS_ASSISTANT/SERVICE_GUIDE/DOC_SUMMARY") @NotBlank @Size(max = 32) String scene,
        @Schema(description = "系统提示词正文") @NotBlank @Size(max = 20000) String systemPrompt,
        @Schema(description = "用户模板（可选，支持 {变量}）") @Size(max = 4000) String userPromptTemplate,
        @Schema(description = "声明的变量名") List<String> variables,
        @Schema(description = "变更说明") @Size(max = 200) String changeNote,
        @Schema(description = "备注") @Size(max = 500) String remark) {
}
