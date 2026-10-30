package com.studenthub.modules.preference.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.preference.controller.dto.UpdateUserPreferenceRequest;
import com.studenthub.modules.preference.controller.dto.UserPreferenceResponse;
import com.studenthub.modules.preference.service.UserPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/users/me/preferences")
@Tag(name = "小程序-用户偏好", description = "推送/提醒/展示开关持久化")
@RequiredArgsConstructor
public class AppUserPreferenceController {
    private final UserPreferenceService preferenceService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "查询用户偏好", operationId = "getUserPreferences")
    public Result<UserPreferenceResponse> get() {
        return Result.success(preferenceService.get(currentUserProvider.requireCurrentUser().userId()));
    }

    @PutMapping
    @Operation(summary = "更新用户偏好", operationId = "updateUserPreferences")
    public Result<UserPreferenceResponse> update(@RequestBody UpdateUserPreferenceRequest request) {
        return Result.success(preferenceService.update(
                currentUserProvider.requireCurrentUser().userId(), request));
    }
}
