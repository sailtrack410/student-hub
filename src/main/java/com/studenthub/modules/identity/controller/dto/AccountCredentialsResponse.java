package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录账号与密码状态")
public record AccountCredentialsResponse(
        @Schema(description = "当前登录账号") String username,
        @Schema(description = "是否已设置密码") boolean passwordSet,
        @Schema(description = "校园邮箱") String email,
        @Schema(description = "是否已绑定 QQ") boolean qqBound) {
}
