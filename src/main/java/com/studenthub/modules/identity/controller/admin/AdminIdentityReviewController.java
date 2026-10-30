package com.studenthub.modules.identity.controller.admin;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.controller.dto.AdminIdentityReviewResponse;
import com.studenthub.modules.identity.controller.dto.IdentityEvidenceRequest;
import com.studenthub.modules.identity.controller.dto.IdentityRejectReasonRequest;
import com.studenthub.modules.identity.controller.dto.IdentityRejectReasonResponse;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.identity.service.IdentityReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端-身份审核增强（U04）：审核详情（材料 + 历史）、材料登记/删除、拒绝原因模板维护。
 *
 * <p>通过/拒绝仍走 `POST /api/admin/users/{userId}/authorization`（同一事务内写审核记录 + 通知），
 * 避免出现两套并行审核入口。写操作由 AdminWriteAuditAspect 自动审计。</p>
 */
@RestController
@RequestMapping("/api/admin/identity-review")
@Tag(name = "管理端-身份审核", description = "身份审核材料、审核历史与拒绝原因模板")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:user:manage')")
public class AdminIdentityReviewController {

    private final IdentityReviewService identityReviewService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/users/{userId}")
    @Operation(summary = "身份审核详情", description = "返回申请人、申请材料（真实文件签名地址）、审核历史与可用拒绝原因模板；"
            + "材料为空表示未上传，不会返回占位图片。", operationId = "adminGetIdentityReview")
    public Result<AdminIdentityReviewResponse> detail(@PathVariable Long userId) {
        return Result.success(identityReviewService.detail(userId));
    }

    @PostMapping("/users/{userId}/evidence")
    @Operation(summary = "登记审核材料", description = "fileId 必须是已上传的真实文件；重复登记同一文件返回 400。",
            operationId = "adminAddIdentityEvidence")
    public Result<AdminIdentityReviewResponse> addEvidence(@PathVariable Long userId,
                                                           @RequestBody @Valid IdentityEvidenceRequest request) {
        Long operatorId = currentUserProvider.currentUserIdOrSystem();
        return Result.success(identityReviewService.addEvidence(operatorId, userId, request));
    }

    @DeleteMapping("/users/{userId}/evidence/{evidenceId}")
    @Operation(summary = "删除审核材料", description = "逻辑删除；审核历史中的材料快照保留。",
            operationId = "adminRemoveIdentityEvidence")
    public Result<AdminIdentityReviewResponse> removeEvidence(@PathVariable Long userId,
                                                              @PathVariable Long evidenceId) {
        Long operatorId = currentUserProvider.currentUserIdOrSystem();
        return Result.success(identityReviewService.removeEvidence(operatorId, userId, evidenceId));
    }

    @GetMapping("/reason-templates")
    @Operation(summary = "拒绝原因模板列表", description = "平台内置（campus_id=0）+ 当前校区自定义；默认全部状态。",
            operationId = "adminListIdentityRejectTemplates")
    public Result<List<IdentityRejectReasonResponse>> listTemplates(
            @RequestParam(required = false) String status) {
        return Result.success(identityReviewService.listRejectTemplates(status));
    }

    @PostMapping("/reason-templates")
    @Operation(summary = "新建拒绝原因模板", description = "编码在当前校区内唯一；写入数据库，供审核弹窗实时读取。",
            operationId = "adminCreateIdentityRejectTemplate")
    public Result<IdentityRejectReasonResponse> createTemplate(
            @RequestBody @Valid IdentityRejectReasonRequest request) {
        Long operatorId = currentUserProvider.currentUserIdOrSystem();
        return Result.success(identityReviewService.createRejectTemplate(operatorId, request));
    }

    @PutMapping("/reason-templates/{templateId}")
    @Operation(summary = "更新拒绝原因模板", description = "仅本校区自定义模板可改；平台内置模板返回 409。",
            operationId = "adminUpdateIdentityRejectTemplate")
    public Result<IdentityRejectReasonResponse> updateTemplate(@PathVariable Long templateId,
                                                               @RequestBody @Valid IdentityRejectReasonRequest request) {
        Long operatorId = currentUserProvider.currentUserIdOrSystem();
        return Result.success(identityReviewService.updateRejectTemplate(operatorId, templateId, request));
    }

    @DeleteMapping("/reason-templates/{templateId}")
    @Operation(summary = "删除拒绝原因模板", description = "平台内置模板不可删除（409）。",
            operationId = "adminDeleteIdentityRejectTemplate")
    public Result<Void> deleteTemplate(@PathVariable Long templateId) {
        Long operatorId = currentUserProvider.currentUserIdOrSystem();
        identityReviewService.deleteRejectTemplate(operatorId, templateId);
        return Result.success();
    }
}
