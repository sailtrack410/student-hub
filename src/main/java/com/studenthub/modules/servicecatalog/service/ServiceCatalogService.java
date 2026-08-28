package com.studenthub.modules.servicecatalog.service;

import com.studenthub.modules.servicecatalog.controller.dto.CatalogCategoryResponse;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceCategoryCreateRequest;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceItemDetailResponse;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceItemUpsertRequest;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface ServiceCatalogService {

    List<CatalogCategoryResponse> categoryTree(Long campusId);

    ServiceItemDetailResponse itemDetail(Long itemId);

    ServiceItemEntity requireEnabledItem(Long itemId);

    /**
     * 校验用户提交的表单数据是否满足服务项的表单定义，并按表单定义顺序输出规范化 JSON 字符串。
     */
    String validateAndNormalizeFormData(Long itemId, JsonNode formData);

    Long createCategory(ServiceCategoryCreateRequest request);

    Long upsertItem(ServiceItemUpsertRequest request);
}
