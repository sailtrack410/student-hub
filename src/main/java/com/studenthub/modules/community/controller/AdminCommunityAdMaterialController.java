package com.studenthub.modules.community.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.community.controller.dto.AdminAdMaterialPageQuery;
import com.studenthub.modules.community.controller.dto.AdminAdMaterialRequest;
import com.studenthub.modules.community.controller.dto.AdminAdMaterialResponse;
import com.studenthub.modules.community.controller.dto.AdminAdMaterialReviewRequest;
import com.studenthub.modules.community.controller.dto.AdminAdMaterialStatusRequest;
import com.studenthub.modules.community.service.CommunityAdLibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-广告素材库（C13）：图片/视频/文本素材独立入库与审核，直投广告可引用已通过素材。
 */
@RestController
@RequestMapping("/api/admin/community/ad-materials")
@Tag(name = "管理端-广告素材库", description = "素材 CRUD、审核、启停与分页")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('community:moderate') "
        + "or hasAuthority('community:ad:manage')")
public class AdminCommunityAdMaterialController {

    private final CommunityAdLibraryService adLibraryService;

    @GetMapping
    @Operation(summary = "素材分页", description = "按审核状态/素材类型/关键词过滤，附被直投广告引用数。",
            operationId = "adminPageAdMaterials")
    public Result<PageResult<AdminAdMaterialResponse>> page(AdminAdMaterialPageQuery query) {
        return Result.success(adLibraryService.pageMaterials(query));
    }

    @PostMapping
    @Operation(summary = "创建素材", description = "新建素材默认 PENDING 待审核；图片/视频必须填文件地址，文本素材需标题或正文。",
            operationId = "adminCreateAdMaterial")
    public Result<AdminAdMaterialResponse> create(@RequestBody @Valid AdminAdMaterialRequest request) {
        return Result.success(adLibraryService.createMaterial(request));
    }

    @PostMapping("/{materialId}")
    @Operation(summary = "更新素材", description = "仅待审核/已驳回素材可编辑；编辑后重新进入待审核。",
            operationId = "adminUpdateAdMaterial")
    public Result<AdminAdMaterialResponse> update(@PathVariable Long materialId,
                                                  @RequestBody @Valid AdminAdMaterialRequest request) {
        return Result.success(adLibraryService.updateMaterial(materialId, request));
    }

    @PostMapping("/{materialId}/review")
    @PreAuthorize("hasAuthority('platform:all') or hasAuthority('community:ad:review')")
    @Operation(summary = "审核素材", description = "仅 PENDING 素材可审核；REJECT 必须填写原因（需 community:ad:review）。",
            operationId = "adminReviewAdMaterial")
    public Result<AdminAdMaterialResponse> review(@PathVariable Long materialId,
                                                  @RequestBody @Valid AdminAdMaterialReviewRequest request) {
        return Result.success(adLibraryService.reviewMaterial(materialId, request));
    }

    @PostMapping("/{materialId}/status")
    @Operation(summary = "素材启停", operationId = "adminUpdateAdMaterialStatus")
    public Result<AdminAdMaterialResponse> updateStatus(@PathVariable Long materialId,
                                                        @RequestBody @Valid AdminAdMaterialStatusRequest request) {
        return Result.success(adLibraryService.updateMaterialStatus(materialId, request.status()));
    }

    @DeleteMapping("/{materialId}")
    @Operation(summary = "删除素材", description = "被直投广告引用时拒绝删除。",
            operationId = "adminDeleteAdMaterial")
    public Result<Void> delete(@PathVariable Long materialId) {
        adLibraryService.deleteMaterial(materialId);
        return Result.success();
    }
}
