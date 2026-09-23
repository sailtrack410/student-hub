package com.studenthub.modules.servicecatalog.service;

import com.studenthub.modules.servicecatalog.controller.dto.CatalogCategoryResponse;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceCategoryCreateRequest;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceItemAdminResponse;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceItemDetailResponse;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceItemUpsertRequest;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface ServiceCatalogService {

    List<CatalogCategoryResponse> categoryTree(Long campusId);

    /**
     * 管理端目录：返回全部分类（含已停用）及其服务项，不按上线状态过滤。
     */
    List<CatalogCategoryResponse> adminCategoryTree(Long campusId);

    /**
     * 管理端服务项列表：不按上线状态过滤，可按分类过滤。
     */
    List<ServiceItemAdminResponse> listItems(Long categoryId);

    ServiceItemDetailResponse itemDetail(Long itemId);

    ServiceItemEntity requireEnabledItem(Long itemId);

    /**
     * 校验用户提交的表单数据是否满足服务项的表单定义，并按表单定义顺序输出规范化 JSON 字符串。
     */
    String validateAndNormalizeFormData(Long itemId, JsonNode formData);

    Long createCategory(ServiceCategoryCreateRequest request);

    Long upsertItem(ServiceItemUpsertRequest request);

    /**
     * 逻辑删除服务项；已产生申请或工单的服务项禁止删除，只能停用。
     */
    void deleteItem(Long itemId);

    /**
     * 更新服务项上线状态（ENABLED / DISABLED）。
     */
    void updateItemStatus(Long itemId, String status);
}
