package com.studenthub.modules.systemconfig.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.systemconfig.controller.dto.*;
import com.studenthub.modules.systemconfig.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/system")
@Tag(name = "管理端-系统配置", description = "运行配置、功能开关、外部集成与健康状态")
@RequiredArgsConstructor
public class AdminSystemConfigController {

    private final SystemConfigService systemConfigService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/config")
    @Operation(summary = "配置列表", operationId = "adminListSystemConfig")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:config:view') or hasAuthority('system:config:manage')")
    public Result<List<AdminConfigItemResponse>> list(
            @RequestParam(required = false) String group,
            @RequestParam(required = false) Long campusId) {
        return Result.success(systemConfigService.list(group, campusId));
    }

    @PostMapping("/config")
    @Operation(summary = "批量保存配置", operationId = "adminUpdateSystemConfig")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:config:manage')")
    public Result<List<AdminConfigItemResponse>> update(@Valid @RequestBody AdminConfigUpdateRequest request) {
        AuthenticatedUser user = currentUserProvider.requireCurrentUser();
        return Result.success(systemConfigService.update(request, user.userId(), user.displayName()));
    }

    @GetMapping("/features")
    @Operation(summary = "功能开关", operationId = "adminListFeatureFlags")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:config:view') or hasAuthority('system:config:manage')")
    public Result<List<AdminFeatureFlagResponse>> features(@RequestParam(required = false) Long campusId) {
        return Result.success(systemConfigService.features(campusId));
    }

    @PostMapping("/features/{key}/rollout")
    @Operation(summary = "设置功能开关灰度比例", description = "按 userId 稳定分桶放量，0=全关，100=全量；未配置默认全量。",
            operationId = "adminUpdateFeatureRollout")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:config:manage')")
    public Result<List<AdminFeatureFlagResponse>> rollout(@PathVariable String key,
                                                         @RequestParam(required = false) Long campusId,
                                                         @RequestParam int percent) {
        AuthenticatedUser user = currentUserProvider.requireCurrentUser();
        systemConfigService.updateRollout(key, campusId, percent, user.userId());
        return Result.success(systemConfigService.features(campusId));
    }

    @GetMapping("/health")
    @Operation(summary = "系统健康状态", operationId = "adminGetSystemHealth")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:config:view') or hasAuthority('system:config:manage')")
    public Result<AdminSystemHealthResponse> health() {
        return Result.success(systemConfigService.health());
    }

    @GetMapping("/integrations")
    @Operation(summary = "外部集成状态", operationId = "adminListIntegrations")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:config:view') or hasAuthority('system:config:manage')")
    public Result<List<AdminIntegrationStatusResponse>> integrations() {
        return Result.success(systemConfigService.integrations());
    }

    @PostMapping("/integrations/{code}/test")
    @Operation(summary = "集成连通性测试",
            description = "按管理端当前有效配置测试：微信调用 /cgi-bin/token 验证 AppID/AppSecret，"
                    + "邮箱执行真实 SMTP 连接，QQ 探测 API 主机；mock/未启用时明确返回 DISABLED，不伪造成功。",
            operationId = "adminTestIntegration")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:config:manage')")
    public Result<AdminIntegrationTestResponse> testIntegration(@PathVariable String code) {
        return Result.success(systemConfigService.testIntegration(code));
    }
}
