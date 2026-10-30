package com.studenthub.modules.systemconfig.controller.dto;

import com.studenthub.modules.systemconfig.entity.SysConfigEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "管理端配置项")
public record AdminConfigItemResponse(
        @Schema(description = "配置 ID") Long id,
        @Schema(description = "作用域校区 ID，0 为平台级") Long campusId,
        @Schema(description = "配置分组") String group,
        @Schema(description = "配置键") String key,
        @Schema(description = "配置值；密钥类为 null") String value,
        @Schema(description = "值类型") String valueType,
        @Schema(description = "是否密钥类") boolean secret,
        @Schema(description = "是否已配置") boolean configured,
        @Schema(description = "说明") String description,
        @Schema(description = "状态") String status,
        @Schema(description = "有效值来源：DATABASE/ENVIRONMENT/UNSET/ERROR") String source,
        @Schema(description = "配置解析错误（如主密钥未配置/解密失败）") String error,
        @Schema(description = "最后修改人 ID") Long updatedBy,
        @Schema(description = "最后修改时间") LocalDateTime updatedAt) {

    public static AdminConfigItemResponse from(SysConfigEntity e) {
        boolean secret = Boolean.TRUE.equals(e.getIsSecret());
        boolean configured = e.getConfigValue() != null && !e.getConfigValue().isBlank();
        return new AdminConfigItemResponse(e.getId(), e.getCampusId(), e.getConfigGroup(),
                e.getConfigKey(), secret ? null : e.getConfigValue(), e.getValueType(), secret,
                configured, e.getDescription(), e.getStatus(), "DATABASE", null,
                e.getUpdatedBy(), e.getUpdatedAt());
    }

    /**
     * 用“有效值”构造响应：非密钥返回实际生效值，密钥永远返回 null；
     * source 供管理端展示当前值来自数据库、环境变量还是未配置。
     */
    public static AdminConfigItemResponse from(SysConfigEntity e, String effectiveValue,
                                               String source, boolean configured, String error) {
        boolean secret = Boolean.TRUE.equals(e.getIsSecret());
        return new AdminConfigItemResponse(e.getId(), e.getCampusId(), e.getConfigGroup(),
                e.getConfigKey(), secret ? null : effectiveValue, e.getValueType(), secret,
                configured, e.getDescription(), e.getStatus(), source, error,
                e.getUpdatedBy(), e.getUpdatedAt());
    }
}
