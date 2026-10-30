package com.studenthub.modules.systemconfig.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.systemconfig.controller.dto.AppFeaturesResponse;
import com.studenthub.modules.systemconfig.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * App 功能开关读取入口。登录后携带 Bearer Token 时优先使用用户校区；
 * 未登录时读取平台默认（campus_id=0），供维护模式等启动检查使用。
 */
@RestController
@RequestMapping("/api/common/features")
@Tag(name = "公共-功能开关", description = "客户端读取管理端配置的有效功能开关")
@RequiredArgsConstructor
public class AppFeatureController {

    private final SystemConfigService systemConfigService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "读取有效功能开关", operationId = "getAppFeatures")
    public Result<AppFeaturesResponse> features(@RequestParam(required = false) Long campusId) {
        Long effectiveCampusId = campusId;
        if (effectiveCampusId == null) {
            effectiveCampusId = currentUserProvider.currentUser()
                    .map(user -> user.campusId())
                    .orElse(0L);
        }
        Long userId = currentUserProvider.currentUser().map(user -> user.userId()).orElse(null);
        return Result.success(new AppFeaturesResponse(effectiveCampusId,
                systemConfigService.effectiveFeatures(effectiveCampusId, userId), LocalDateTime.now()));
    }
}
