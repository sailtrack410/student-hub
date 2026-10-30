package com.studenthub.modules.identity.controller.admin;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.controller.dto.AdminCampusRequest;
import com.studenthub.modules.identity.controller.dto.AdminCampusResponse;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.identity.service.AdminCampusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/campuses")
@Tag(name = "管理端-校区", description = "多校区数据范围的校区目录；所有业务数据通过 campus_id 隔离")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:organization:manage')")
public class AdminCampusController {

    private final AdminCampusService adminCampusService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "校区列表", operationId = "adminListCampuses")
    public Result<List<AdminCampusResponse>> list() {
        return Result.success(adminCampusService.list());
    }

    @PostMapping
    @Operation(summary = "新建校区", operationId = "adminCreateCampus")
    public Result<AdminCampusResponse> create(@RequestBody @Valid AdminCampusRequest request) {
        return Result.success(adminCampusService.create(currentUserProvider.currentUserIdOrSystem(), request));
    }

    @PostMapping("/{campusId}")
    @Operation(summary = "更新校区", operationId = "adminUpdateCampus")
    public Result<AdminCampusResponse> update(@PathVariable Long campusId,
                                              @RequestBody @Valid AdminCampusRequest request) {
        return Result.success(adminCampusService.update(
                currentUserProvider.currentUserIdOrSystem(), campusId, request));
    }
}
