package com.studenthub.modules.servicecatalog.controller.admin;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.Result;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceCategoryCreateRequest;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceItemUpsertRequest;
import com.studenthub.modules.servicecatalog.service.ServiceCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/catalog")
@Tag(name = "管理端-服务目录")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('catalog:manage')")
public class AdminServiceCatalogController {

    private final ServiceCatalogService serviceCatalogService;

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
}
