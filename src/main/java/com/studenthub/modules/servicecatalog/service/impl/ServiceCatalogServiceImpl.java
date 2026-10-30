package com.studenthub.modules.servicecatalog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.servicecatalog.controller.dto.CatalogCategoryResponse;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceCategoryCreateRequest;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceHandlerOrganizationResponse;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceItemAdminResponse;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceItemDetailResponse;
import com.studenthub.modules.servicecatalog.controller.dto.ServiceItemUpsertRequest;
import com.studenthub.modules.servicecatalog.entity.ServiceCategoryEntity;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import com.studenthub.modules.identity.entity.OrganizationEntity;
import com.studenthub.modules.identity.mapper.OrganizationMapper;
import com.studenthub.modules.servicecatalog.mapper.ServiceCategoryMapper;
import com.studenthub.modules.servicecatalog.mapper.ServiceItemMapper;
import com.studenthub.modules.servicecatalog.service.ServiceCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceCatalogServiceImpl implements ServiceCatalogService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String PRIORITY_NORMAL = "NORMAL";
    private static final String PRIORITY_URGENT = "URGENT";
    private static final String COPY_SUFFIX = "-COPY";

    private final ServiceCategoryMapper categoryMapper;
    private final ServiceItemMapper itemMapper;
    private final OrganizationMapper organizationMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<CatalogCategoryResponse> categoryTree(Long campusId) {
        List<ServiceCategoryEntity> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<ServiceCategoryEntity>()
                        .eq(ServiceCategoryEntity::getCampusId, campusId)
                        .eq(ServiceCategoryEntity::getStatus, STATUS_ENABLED)
                        .orderByAsc(ServiceCategoryEntity::getSortOrder));
        List<ServiceItemEntity> items = itemMapper.selectList(new LambdaQueryWrapper<ServiceItemEntity>()
                .eq(ServiceItemEntity::getCampusId, campusId)
                .eq(ServiceItemEntity::getStatus, STATUS_ENABLED)
                .orderByAsc(ServiceItemEntity::getSortOrder));
        Map<Long, List<ServiceItemEntity>> itemsByCategory = new LinkedHashMap<>();
        for (ServiceItemEntity item : items) {
            itemsByCategory.computeIfAbsent(item.getCategoryId(), key -> new ArrayList<>()).add(item);
        }
        return categories.stream()
                .map(category -> CatalogCategoryResponse.from(
                        category, itemsByCategory.getOrDefault(category.getId(), List.of())))
                .toList();
    }

    @Override
    public List<CatalogCategoryResponse> adminCategoryTree(Long campusId) {
        List<ServiceCategoryEntity> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<ServiceCategoryEntity>()
                        .eq(ServiceCategoryEntity::getCampusId, campusId)
                        .orderByAsc(ServiceCategoryEntity::getSortOrder));
        List<ServiceItemEntity> items = itemMapper.selectList(new LambdaQueryWrapper<ServiceItemEntity>()
                .eq(ServiceItemEntity::getCampusId, campusId)
                .orderByAsc(ServiceItemEntity::getSortOrder));
        Map<Long, List<ServiceItemEntity>> itemsByCategory = new LinkedHashMap<>();
        for (ServiceItemEntity item : items) {
            itemsByCategory.computeIfAbsent(item.getCategoryId(), key -> new ArrayList<>()).add(item);
        }
        return categories.stream()
                .map(category -> CatalogCategoryResponse.from(
                        category, itemsByCategory.getOrDefault(category.getId(), List.of())))
                .toList();
    }

    @Override
    public List<ServiceItemAdminResponse> listItems(Long categoryId) {
        List<ServiceCategoryEntity> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<ServiceCategoryEntity>().orderByAsc(ServiceCategoryEntity::getSortOrder));
        Map<Long, String> categoryNames = new LinkedHashMap<>();
        for (ServiceCategoryEntity category : categories) {
            categoryNames.put(category.getId(), category.getCategoryName());
        }
        LambdaQueryWrapper<ServiceItemEntity> wrapper = new LambdaQueryWrapper<ServiceItemEntity>()
                .orderByAsc(ServiceItemEntity::getSortOrder)
                .orderByDesc(ServiceItemEntity::getUpdatedAt);
        if (categoryId != null) {
            wrapper.eq(ServiceItemEntity::getCategoryId, categoryId);
        }
        return itemMapper.selectList(wrapper).stream()
                .map(item -> ServiceItemAdminResponse.from(item,
                        categoryNames.getOrDefault(item.getCategoryId(), "未分类"),
                        parseSchema(item.getId(), item.getFormSchema()),
                        parseOptionalJson(item.getId(), item.getMaterialList()),
                        parseOptionalJson(item.getId(), item.getFaq())))
                .toList();
    }

    @Override
    public ServiceItemDetailResponse itemDetail(Long itemId) {
        ServiceItemEntity item = requireEnabledItem(itemId);
        return new ServiceItemDetailResponse(item.getId(), item.getItemCode(), item.getItemName(),
                item.getSummary(), item.getDescription(), item.getIcon(), parseSchema(item.getId(), item.getFormSchema()),
                item.getSlaHours(),
                item.getDefaultPriority() == null ? PRIORITY_NORMAL : item.getDefaultPriority(),
                Boolean.TRUE.equals(item.getPhotoWorkflowRequired()),
                item.getGuideContent(),
                parseOptionalJson(item.getId(), item.getMaterialList()),
                parseOptionalJson(item.getId(), item.getFaq()));
    }

    @Override
    public ServiceItemEntity requireEnabledItem(Long itemId) {
        ServiceItemEntity item = itemMapper.selectById(itemId);
        if (item == null || item.getDeleted() != null && item.getDeleted() == 1
                || !STATUS_ENABLED.equals(item.getStatus())) {
            throw new BusinessException(ErrorCode.CATALOG_ITEM_NOT_FOUND);
        }
        return item;
    }

    @Override
    public String validateAndNormalizeFormData(Long itemId, JsonNode formData) {
        ServiceItemEntity item = requireEnabledItem(itemId);
        ArrayNode schema = parseSchemaArray(item.getFormSchema());
        ObjectNode normalized = objectMapper.createObjectNode();
        for (JsonNode field : schema) {
            String key = field.path("key").asText();
            String type = field.path("type").asText("input");
            boolean required = field.path("required").asBoolean(false);
            JsonNode value = formData == null ? null : formData.get(key);

            if (value == null || value.isNull()
                    || (value.isTextual() && value.asText().isBlank())
                    || (value.isArray() && value.isEmpty())) {
                if (required) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "缺少必填字段：" + field.path("label").asText(key));
                }
                continue;
            }
            normalized.set(key, normalizeField(key, type, field, value));
        }
        return normalized.toString();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCategory(ServiceCategoryCreateRequest request) {
        ServiceCategoryEntity entity = new ServiceCategoryEntity();
        entity.setCampusId(1L);
        entity.setCategoryCode(request.categoryCode());
        entity.setCategoryName(request.categoryName());
        entity.setIcon(request.icon());
        entity.setDescription(request.description());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setStatus(STATUS_ENABLED);
        categoryMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long upsertItem(ServiceItemUpsertRequest request) {
        ServiceItemEntity entity;
        if (request.itemId() == null) {
            entity = new ServiceItemEntity();
            entity.setCampusId(requireCategoryCampus(request.categoryId()));
            entity.setStatus(STATUS_ENABLED);
            entity.setSortOrder(0);
            entity.setDefaultPriority(PRIORITY_NORMAL);
            entity.setPhotoWorkflowRequired(Boolean.FALSE);
        } else {
            entity = itemMapper.selectById(request.itemId());
            if (entity == null) {
                throw new BusinessException(ErrorCode.CATALOG_ITEM_NOT_FOUND);
            }
        }
        if (request.categoryId() != null) {
            requireCategory(request.categoryId());
            entity.setCategoryId(request.categoryId());
        }
        entity.setItemCode(request.itemCode());
        entity.setItemName(request.itemName());
        entity.setSummary(request.summary());
        entity.setDescription(request.description());
        entity.setIcon(request.icon());
        entity.setFormSchema(request.formSchema() == null ? null : request.formSchema().toString());
        entity.setHandlerOrganizationId(request.handlerOrganizationId());
        if (request.defaultPriority() != null) {
            entity.setDefaultPriority(request.defaultPriority());
        } else if (entity.getId() == null) {
            entity.setDefaultPriority(PRIORITY_NORMAL);
        }
        if (request.photoWorkflowRequired() != null) {
            entity.setPhotoWorkflowRequired(request.photoWorkflowRequired());
        }
        if (request.guideContent() != null) {
            entity.setGuideContent(request.guideContent().isBlank() ? null : request.guideContent().trim());
        }
        if (request.materialList() != null) {
            entity.setMaterialList(request.materialList().isNull() ? null : request.materialList().toString());
        }
        if (request.faq() != null) {
            entity.setFaq(request.faq().isNull() ? null : request.faq().toString());
        }
        if (request.slaHours() != null) {
            entity.setSlaHours(request.slaHours());
        }
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        if (entity.getId() == null) {
            itemMapper.insert(entity);
        } else {
            itemMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copyItem(Long itemId) {
        ServiceItemEntity source = itemMapper.selectById(itemId);
        if (source == null) {
            throw new BusinessException(ErrorCode.CATALOG_ITEM_NOT_FOUND);
        }
        ServiceItemEntity copy = new ServiceItemEntity();
        copy.setCampusId(source.getCampusId());
        copy.setCategoryId(source.getCategoryId());
        copy.setItemCode(nextCopyCode(source.getCampusId(), source.getItemCode()));
        copy.setItemName(truncate(source.getItemName() == null ? "服务项" : source.getItemName(), 120) + "（副本）");
        copy.setSummary(source.getSummary());
        copy.setDescription(source.getDescription());
        copy.setIcon(source.getIcon());
        copy.setFormSchema(source.getFormSchema());
        copy.setHandlerOrganizationId(source.getHandlerOrganizationId());
        copy.setSlaHours(source.getSlaHours());
        copy.setDefaultPriority(source.getDefaultPriority() == null ? PRIORITY_NORMAL : source.getDefaultPriority());
        copy.setPhotoWorkflowRequired(Boolean.TRUE.equals(source.getPhotoWorkflowRequired()));
        copy.setGuideContent(source.getGuideContent());
        copy.setMaterialList(source.getMaterialList());
        copy.setFaq(source.getFaq());
        copy.setSortOrder(source.getSortOrder() == null ? 0 : source.getSortOrder() + 1);
        // 副本默认停用，避免复制后立即出现在小程序目录；确认无误后再上架。
        copy.setStatus(STATUS_DISABLED);
        itemMapper.insert(copy);
        log.info("服务项已复制, sourceItemId={}, copyItemId={}", itemId, copy.getId());
        return copy.getId();
    }

    @Override
    public List<ServiceHandlerOrganizationResponse> handlerOrganizations(Long campusId) {
        return organizationMapper.selectList(new LambdaQueryWrapper<OrganizationEntity>()
                        .eq(OrganizationEntity::getStatus, STATUS_ENABLED)
                        .and(wrapper -> wrapper.eq(OrganizationEntity::getCampusId, campusId)
                                .or().isNull(OrganizationEntity::getCampusId))
                        .orderByAsc(OrganizationEntity::getSortOrder)
                        .orderByAsc(OrganizationEntity::getId))
                .stream()
                .map(organization -> new ServiceHandlerOrganizationResponse(organization.getId(),
                        organization.getOrganizationName(), organization.getOrganizationCode(),
                        organization.getStatus()))
                .toList();
    }

    /** 生成不冲突的副本编码：原编码 + "-COPY"，重复时追加序号；编码上限 64。 */
    private String nextCopyCode(Long campusId, String itemCode) {
        String base = truncate(itemCode == null ? "SERVICE" : itemCode, 64 - COPY_SUFFIX.length() - 3);
        for (int index = 1; index <= 99; index++) {
            String candidate = base + COPY_SUFFIX + (index == 1 ? "" : "-" + index);
            Long count = itemMapper.selectCount(new LambdaQueryWrapper<ServiceItemEntity>()
                    .eq(ServiceItemEntity::getCampusId, campusId)
                    .eq(ServiceItemEntity::getItemCode, candidate));
            if (count == null || count == 0) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该服务项副本数量过多，请手工指定编码");
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long itemId) {
        ServiceItemEntity item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ErrorCode.CATALOG_ITEM_NOT_FOUND);
        }
        itemMapper.deleteById(itemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItemStatus(Long itemId, String status) {
        ServiceItemEntity item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ErrorCode.CATALOG_ITEM_NOT_FOUND);
        }
        if (!STATUS_ENABLED.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "状态仅支持 ENABLED 或 DISABLED");
        }
        if (status.equals(item.getStatus())) {
            return;
        }
        ServiceItemEntity update = new ServiceItemEntity();
        update.setId(itemId);
        update.setStatus(status);
        itemMapper.updateById(update);
    }

    private JsonNode parseSchema(Long itemId, String formSchema) {
        try {
            return objectMapper.readTree(formSchema == null || formSchema.isBlank() ? "[]" : formSchema);
        } catch (Exception exception) {
            log.error("服务项表单定义解析失败, itemId={}", itemId, exception);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /** 可选 JSON 列：空值返回 null，解析失败按 null 处理（不阻断列表展示）。 */
    private JsonNode parseOptionalJson(Long itemId, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception exception) {
            log.warn("服务项扩展 JSON 解析失败, itemId={}", itemId);
            return null;
        }
    }

    private ArrayNode parseSchemaArray(String formSchema) {
        JsonNode node = parseSchema(null, formSchema);
        if (!node.isArray()) {
            return objectMapper.createArrayNode();
        }
        return (ArrayNode) node;
    }

    private JsonNode normalizeField(String key, String type, JsonNode field, JsonNode value) {
        return switch (type) {
            case "input", "textarea" -> normalizeText(key, field, value);
            case "select" -> normalizeSelect(key, field, value);
            case "date" -> normalizeDate(key, value);
            case "number" -> normalizeNumber(key, field, value);
            case "image" -> normalizeImages(key, field, value);
            default -> value;
        };
    }

    private JsonNode normalizeText(String key, JsonNode field, JsonNode value) {
        String text = value.asText().trim();
        int max = field.path("max").asInt(500);
        if (text.length() > max) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "字段超出长度限制：" + field.path("label").asText(key));
        }
        return objectMapper.getNodeFactory().textNode(text);
    }

    private JsonNode normalizeSelect(String key, JsonNode field, JsonNode value) {
        String text = value.asText();
        Iterator<JsonNode> options = field.path("options").elements();
        while (options.hasNext()) {
            if (options.next().asText().equals(text)) {
                return objectMapper.getNodeFactory().textNode(text);
            }
        }
        throw new BusinessException(ErrorCode.INVALID_PARAMETER, "字段取值不在可选项内：" + field.path("label").asText(key));
    }

    private JsonNode normalizeDate(String key, JsonNode value) {
        try {
            LocalDate.parse(value.asText().trim());
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "日期格式应为 yyyy-MM-dd：" + key);
        }
        return objectMapper.getNodeFactory().textNode(value.asText().trim());
    }

    private JsonNode normalizeNumber(String key, JsonNode field, JsonNode value) {
        if (!value.isNumber()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "字段应为数字：" + field.path("label").asText(key));
        }
        long number = value.asLong();
        if (field.has("min") && number < field.get("min").asLong()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "字段小于最小值：" + field.path("label").asText(key));
        }
        if (field.has("max") && number > field.get("max").asLong()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "字段大于最大值：" + field.path("label").asText(key));
        }
        return objectMapper.getNodeFactory().numberNode(number);
    }

    private JsonNode normalizeImages(String key, JsonNode field, JsonNode value) {
        if (!value.isArray()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "图片字段应为数组：" + field.path("label").asText(key));
        }
        int maxCount = field.path("maxCount").asInt(3);
        if (value.size() > maxCount) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "图片数量超出限制：" + field.path("label").asText(key));
        }
        ArrayNode urls = objectMapper.createArrayNode();
        value.forEach(url -> urls.add(url.asText()));
        return urls;
    }

    private void requireCategory(Long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BusinessException(ErrorCode.CATALOG_CATEGORY_NOT_FOUND);
        }
    }

    private Long requireCategoryCampus(Long categoryId) {
        ServiceCategoryEntity category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ErrorCode.CATALOG_CATEGORY_NOT_FOUND);
        }
        return category.getCampusId();
    }
}
