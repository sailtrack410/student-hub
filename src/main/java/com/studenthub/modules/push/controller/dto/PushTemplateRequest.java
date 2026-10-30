package com.studenthub.modules.push.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "管理端推送模板创建/更新")
public record PushTemplateRequest(
        @Schema(description = "模板编码，创建后不可修改，大写字母/数字/下划线") 
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "模板编码需以大写字母开头，仅含大写字母/数字/下划线")
        String templateCode,
        @Schema(description = "模板名称") @NotBlank @Size(max = 100) String templateName,
        @Schema(description = "场景：SYSTEM/ACTIVITY/WORK_ORDER/ANNOUNCEMENT/AI_TODO") @NotBlank @Size(max = 32) String scene,
        @Schema(description = "标题模板，支持 {变量}") @NotBlank @Size(max = 200) String titleTemplate,
        @Schema(description = "正文模板，支持 {变量}") @NotBlank @Size(max = 2000) String contentTemplate,
        @Schema(description = "模板声明的变量名（可选，留空自动从标题/正文提取）") List<String> variables,
        @Schema(description = "默认渠道：LOG/WS/FCM/APNS/WEBPUSH") List<String> defaultChannels,
        @Schema(description = "状态：ENABLED/DISABLED，缺省 ENABLED") @Size(max = 16) String status,
        @Schema(description = "备注") @Size(max = 500) String remark) {
}
