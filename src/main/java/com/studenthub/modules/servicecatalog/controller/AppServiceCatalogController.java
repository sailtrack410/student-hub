package com.studenthub.modules.servicecatalog.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.servicecatalog.controller.dto.CatalogCategoryResponse;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceItemDetailResponse;
import com.studenthub.modules.servicecatalog.service.ServiceCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/app/services")
@Tag(name = "小程序-服务目录")
@RequiredArgsConstructor
public class AppServiceCatalogController {

    private final ServiceCatalogService serviceCatalogService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "查询服务目录", description = "返回当前校区已上线的服务分类及其服务项，按排序值升序。",
            operationId = "getCatalogTree")
    public Result<List<CatalogCategoryResponse>> catalogTree() {
        Long campusId = currentUserProvider.requireCurrentUser().campusId();
        return Result.success(serviceCatalogService.categoryTree(campusId));
    }

    @GetMapping("/{itemId}")
    @Operation(summary = "查询服务项详情", description = "返回服务项介绍与动态申请表单字段定义。", operationId = "getServiceItemDetail")
    public Result<ServiceItemDetailResponse> itemDetail(@PathVariable Long itemId) {
        return Result.success(serviceCatalogService.itemDetail(itemId));
    }
}
