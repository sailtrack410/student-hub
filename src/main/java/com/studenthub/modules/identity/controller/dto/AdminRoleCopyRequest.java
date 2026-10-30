package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 管理端复制角色模板（U02）：以现有角色为模板复制权限集合，生成新角色。
 */
@Schema(description = "管理端复制角色请求")
public record AdminRoleCopyRequest(
        @Schema(description = "新角色编码，留空时自动生成 源编码_COPY[_n]")
        @Size(max = 64)
        @Pattern(regexp = "^$|^[A-Z][A-Z0-9_-]{1,63}$", message = "角色编码需以大写字母开头，仅允许 A-Z 0-9 _ -")
        String roleCode,
        @Schema(description = "新角色名称，留空时自动生成「源角色名 副本」")
        @Size(max = 128) String roleName) {
}
