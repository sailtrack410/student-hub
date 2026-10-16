package com.studenthub.modules.servicecatalog.controller.admin;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.Result;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.servicecatalog.controller.dto.CatalogCategoryResponse;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceCategoryCreateRequest;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceItemAdminResponse;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceItemStatusUpdateRequest;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceItemUpsertRequest;
import com.studenthub.modules.servicecatalog.service.ServiceCatalogService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/catalog")
@Tag(name = "管理端-服务目录")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('catalog:manage')")
public class AdminServiceCatalogController {

    /** 当前仅单校区，与创建分类逻辑保持一致。 */
    private static final Long DEFAULT_CAMPUS_ID = 1L;

    private final ServiceCatalogService serviceCatalogService;

    @GetMapping("/categories")
    @Operation(summary = "查询服务分类", description = "返回全部服务分类（含已停用）及其服务项，按排序值升序。",
            operationId = "listServiceCategories")
    public Result<List<CatalogCategoryResponse>> listCategories() {
        return Result.success(serviceCatalogService.adminCategoryTree(DEFAULT_CAMPUS_ID));
    }

    @GetMapping("/items")
    @Operation(summary = "查询服务项列表", description = "返回服务项列表，可按分类过滤，含已停用服务项。",
            operationId = "listServiceItems")
    public Result<List<ServiceItemAdminResponse>> listItems(@RequestParam(value = "categoryId", required = false) Long categoryId) {
        return Result.success(serviceCatalogService.listItems(categoryId));
    }

    @PostMapping("/categories")
    @Operation(summary = "创建服务分类", description = "创建一个启用状态的服务分类。", operationId = "createServiceCategory")
    public Result<Long> createCategory(@RequestBody @Valid ServiceCategoryCreateRequest request) {
        return Result.success(serviceCatalogService.createCategory(request));
    }

    @PostMapping("/items")
    @Operation(summary = "创建服务项", description = "按表单字段定义创建一个启用状态的服务项。", operationId = "createServiceItem")
    public Result<Long> createItem(@RequestBody @Valid ServiceItemUpsertRequest request) {
        return Result.success(serviceCatalogService.upsertItem(request));
    }

    @PostMapping("/items/update")
    @Operation(summary = "更新服务项", description = "按 itemId 更新服务项定义。", operationId = "updateServiceItem")
    public Result<Long> updateItem(@RequestBody @Valid ServiceItemUpsertRequest request) {
        if (request.itemId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "更新服务项必须提供 itemId");
        }
        return Result.success(serviceCatalogService.upsertItem(request));
    }

    @PostMapping("/items/{itemId}/status")
    @Operation(summary = "更新服务项状态", description = "上架或下架服务项。", operationId = "updateServiceItemStatus")
    public Result<Void> updateItemStatus(@PathVariable Long itemId,
                                         @RequestBody @Valid ServiceItemStatusUpdateRequest request) {
        serviceCatalogService.updateItemStatus(itemId, request.status());
        return Result.success();
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "删除服务项", description = "逻辑删除服务项。", operationId = "deleteServiceItem")
    public Result<Void> deleteItem(@PathVariable Long itemId) {
        serviceCatalogService.deleteItem(itemId);
        return Result.success();
    }
}
