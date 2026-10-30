package com.studenthub.modules.identity.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.campus.AdminCampusContext;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.controller.dto.AdminCampusOptionResponse;
import com.studenthub.modules.identity.controller.dto.AdminContextResponse;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端启动上下文：当前身份、当前校区、可切换校区。
 * 前端每次加载管理端先取该接口，之后所有请求带 X-Campus-Id。
 */
@RestController
@RequestMapping("/api/admin/context")
@Tag(name = "管理端-会话上下文", description = "当前管理员、当前校区与可切换校区")
@RequiredArgsConstructor
public class AdminContextController {

    private final AdminCampusContextService campusContextService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "获取管理端会话上下文", operationId = "getAdminContext")
    public Result<AdminContextResponse> context() {
        AuthenticatedUser user = currentUserProvider.requireCurrentUser();
        AdminCampusContext.Snapshot snapshot = campusContextService.snapshot();
        List<AdminCampusOptionResponse> campuses = campusContextService.accessibleCampuses();
        String campusName = campuses.stream()
                .filter(item -> item.campusId().equals(snapshot.campusId()))
                .map(AdminCampusOptionResponse::campusName)
                .findFirst()
                .orElse(campusContextService.currentCampus().getCampusName());
        return Result.success(new AdminContextResponse(
                user.userId(),
                user.displayName(),
                user.identityType(),
                user.roleCodes(),
                snapshot.platformAdmin(),
                snapshot.campusId(),
                campusName,
                campuses));
    }
}
