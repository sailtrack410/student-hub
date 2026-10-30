package com.studenthub.modules.identity.controller.admin;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.identity.controller.dto.AdminSsoStatusResponse;
import com.studenthub.modules.identity.controller.dto.AdminUserAuthorizationRequest;
import com.studenthub.modules.identity.controller.dto.AdminUserDetailResponse;
import com.studenthub.modules.identity.controller.dto.AdminUserDeviceResponse;
import com.studenthub.modules.identity.controller.dto.AdminUserImportResult;
import com.studenthub.modules.identity.controller.dto.AdminUserOfficialRequest;
import com.studenthub.modules.identity.controller.dto.AdminUserPageQuery;
import com.studenthub.modules.identity.controller.dto.AdminUserRoleUpdateRequest;
import com.studenthub.modules.identity.controller.dto.AdminUserRoleBindingsRequest;
import com.studenthub.modules.identity.controller.dto.AdminUserStatusRequest;
import com.studenthub.modules.identity.controller.dto.AdminUserSummaryResponse;
import com.studenthub.modules.identity.controller.dto.WorkerCandidateResponse;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.identity.service.AdminUserImportService;
import com.studenthub.modules.identity.service.AdminUserInsightService;
import com.studenthub.modules.identity.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "管理端-用户管理", description = "用户分页、启停、授权审核与角色调整")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('system:user:manage')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AdminUserInsightService adminUserInsightService;
    private final AdminUserImportService adminUserImportService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "分页查询用户", description = "支持关键词（用户名/昵称/手机号）与状态、身份、授权状态、角色过滤。",
            operationId = "adminPageUsers")
    public Result<PageResult<AdminUserSummaryResponse>> page(AdminUserPageQuery query) {
        return Result.success(adminUserService.page(query));
    }

    @PostMapping("/{userId}/status")
    @Operation(summary = "启用/停用账号", description = "停用后该用户全部在线会话立即失效；不能操作自己。",
            operationId = "adminUpdateUserStatus")
    public Result<Void> updateStatus(@PathVariable Long userId,
                                     @RequestBody @Valid AdminUserStatusRequest request) {
        adminUserService.updateStatus(currentUserProvider.currentUserIdOrSystem(), userId, request.status());
        return Result.success();
    }

    @PostMapping("/{userId}/authorization")
    @Operation(summary = "授权审核（U04 增强）", description = "APPROVE 置为已授权，REJECT 置为已拒绝；拒绝需提供"
            + "原因模板或自定义原因。会写入审核备注/审核人/独立审核记录，并向申请人发送站内通知；用户在线会话原地刷新。",
            operationId = "adminReviewUserAuthorization")
    public Result<Void> reviewAuthorization(@PathVariable Long userId,
                                            @RequestBody @Valid AdminUserAuthorizationRequest request) {
        adminUserService.reviewAuthorization(currentUserProvider.currentUserIdOrSystem(), userId,
                request.action(), request.reasonCode(), request.reason());
        return Result.success();
    }

    @PutMapping("/{userId}/roles")
    @Operation(summary = "调整用户角色", description = "整体覆盖角色绑定；授予服务角色会联动身份为后勤并标记已授权，"
            + "撤销后回落；用户在线会话原地刷新。", operationId = "adminUpdateUserRoles")
    public Result<AdminUserSummaryResponse> updateRoles(@PathVariable Long userId,
                                                        @RequestBody @Valid AdminUserRoleUpdateRequest request) {
        return Result.success(adminUserService.updateRoles(currentUserProvider.currentUserIdOrSystem(),
                userId, request.roleIds()));
    }

    @PutMapping("/{userId}/role-bindings")
    @Operation(summary = "调整用户角色绑定", description = "整体覆盖角色绑定，可为每个角色指定组织与数据范围。",
            operationId = "adminUpdateUserRoleBindings")
    public Result<AdminUserSummaryResponse> updateRoleBindings(@PathVariable Long userId,
                                                               @RequestBody @Valid AdminUserRoleBindingsRequest request) {
        return Result.success(adminUserService.updateRoleBindings(currentUserProvider.currentUserIdOrSystem(),
                userId, request.bindings()));
    }

    @PostMapping("/{userId}/official")
    @Operation(summary = "设置/取消官方账号", description = "官方账号在社区展示「官方」标识而非等级，仅作为官方通知发布主体。",
            operationId = "adminUpdateUserOfficial")
    public Result<AdminUserSummaryResponse> updateOfficial(@PathVariable Long userId,
                                                           @RequestBody @Valid AdminUserOfficialRequest request) {
        return Result.success(adminUserService.updateOfficial(currentUserProvider.currentUserIdOrSystem(),
                userId, request.official(), request.officialTitle()));
    }

    @GetMapping("/workers")
    @Operation(summary = "查询可派单的服务人员", description = "返回绑定服务人员角色且账号启用的用户，支持关键词过滤。",
            operationId = "adminListWorkerCandidates")
    public Result<List<WorkerCandidateResponse>> workers(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(adminUserService.workerCandidates(keyword));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "用户详情", description = "资料、脱敏手机号、默认组织、身份、账号状态、角色绑定（含组织与数据范围）、"
            + "官方账号信息、最近登录时间；不返回密码、凭证与第三方 openId。",
            operationId = "adminGetUserDetail")
    public Result<AdminUserDetailResponse> detail(@PathVariable Long userId) {
        return Result.success(adminUserInsightService.detail(userId));
    }

    @GetMapping("/{userId}/devices")
    @Operation(summary = "用户登录设备", description = "设备名/系统/IP/最近活跃；在线状态由会话存储实时探测（ONLINE/OFFLINE），"
            + "不是按时间推测。", operationId = "adminListUserDevices")
    public Result<List<AdminUserDeviceResponse>> devices(@PathVariable Long userId) {
        return Result.success(adminUserInsightService.devices(userId));
    }

    @PostMapping("/{userId}/devices/{deviceId}/revoke")
    @Operation(summary = "下线指定登录设备", description = "删除该设备对应的服务端会话与设备登记行；设备不存在返回 404。",
            operationId = "adminRevokeUserDevice")
    public Result<Integer> revokeDevice(@PathVariable Long userId, @PathVariable String deviceId) {
        return Result.success(adminUserInsightService.revokeDevice(
                currentUserProvider.currentUserIdOrSystem(), userId, deviceId));
    }

    @PostMapping("/{userId}/devices/revoke-all")
    @Operation(summary = "下线用户全部登录设备", description = "删除该用户全部在线会话与设备登记行；"
            + "管理员不能对自己执行（请走退出登录）。", operationId = "adminRevokeAllUserDevices")
    public Result<Integer> revokeAllDevices(@PathVariable Long userId) {
        return Result.success(adminUserInsightService.revokeAllDevices(
                currentUserProvider.currentUserIdOrSystem(), userId));
    }

    @GetMapping("/import-template")
    @Operation(summary = "下载用户导入 CSV 模板", description = "UTF-8 + BOM；列：username, displayName, phone, email, "
            + "studentNo, college, identityType, organizationCode, roleCodes。",
            operationId = "adminDownloadUserImportTemplate")
    public ResponseEntity<byte[]> importTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"users-import-template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(adminUserImportService.templateCsv());
    }

    @PostMapping("/import/preview")
    @Operation(summary = "预览用户导入", description = "解析 CSV 并返回逐行将执行的动作（CREATE/UPDATE/SKIP/ERROR），不写库。"
            + "strategy 支持 SKIP（默认）/UPDATE。", operationId = "adminPreviewUserImport")
    public Result<AdminUserImportResult> previewImport(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "strategy", required = false) String strategy) {
        return Result.success(adminUserImportService.preview(file, strategy));
    }

    @PostMapping("/import")
    @Operation(summary = "提交用户导入", description = "同步导入（非异步任务）：单次最多 500 行/2MB，逐行返回结果；"
            + "UPDATE 策略仅覆盖资料字段，角色仅追加不删除。", operationId = "adminImportUsers")
    public Result<AdminUserImportResult> importUsers(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "strategy", required = false) String strategy) {
        return Result.success(adminUserImportService.execute(
                currentUserProvider.currentUserIdOrSystem(), file, strategy));
    }

    @GetMapping("/sso-status")
    @Operation(summary = "SSO/花名册接入状态", description = "只读返回学校 SSO/花名册接入状态；学校未提供接口时返回 "
            + "NOT_CONFIGURED，不伪造同步成功。", operationId = "adminGetSsoStatus")
    public Result<AdminSsoStatusResponse> ssoStatus() {
        return Result.success(adminUserInsightService.ssoStatus());
    }
}
