package com.studenthub.modules.dict.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.community.entity.CommunitySectionEntity;
import com.studenthub.modules.community.entity.CommunityTopicEntity;
import com.studenthub.modules.community.mapper.CommunitySectionMapper;
import com.studenthub.modules.community.mapper.CommunityTopicMapper;
import com.studenthub.modules.dict.controller.dto.DictItemResponse;
import com.studenthub.modules.dict.controller.dto.DictTypeResponse;
import com.studenthub.modules.identity.entity.IdentityRejectReasonEntity;
import com.studenthub.modules.identity.entity.RoleEntity;
import com.studenthub.modules.identity.mapper.IdentityRejectReasonMapper;
import com.studenthub.modules.identity.mapper.RoleMapper;
import com.studenthub.modules.servicecatalog.entity.ServiceCategoryEntity;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import com.studenthub.modules.servicecatalog.mapper.ServiceCategoryMapper;
import com.studenthub.modules.servicecatalog.mapper.ServiceItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务目录只读字典：服务分类/服务事项、社区分区/话题、认证拒绝原因、系统角色。
 *
 * <p>这些数据本身是业务表、由对应管理页维护（服务目录、社区治理、身份审核、角色权限）。
 * 在「字典管理」中统一只读展示，满足“全量字典可查”，但不在字典页提供第二套写入入口，
 * 避免字典与业务表双写不一致。需要编辑时请到对应业务页面。</p>
 */
@Component
@RequiredArgsConstructor
public class BusinessCatalogDictProvider implements SystemDictProvider {

    public static final String SOURCE_BUSINESS = "BUSINESS";

    public static final String TYPE_SERVICE_CATEGORY = "SERVICE_CATEGORY";
    public static final String TYPE_SERVICE_ITEM = "SERVICE_ITEM";
    public static final String TYPE_COMMUNITY_SECTION = "COMMUNITY_SECTION";
    public static final String TYPE_COMMUNITY_TOPIC = "COMMUNITY_TOPIC";
    public static final String TYPE_IDENTITY_REJECT_REASON = "IDENTITY_REJECT_REASON";
    public static final String TYPE_USER_ROLE = "USER_ROLE";

    private final ServiceCategoryMapper serviceCategoryMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final CommunitySectionMapper communitySectionMapper;
    private final CommunityTopicMapper communityTopicMapper;
    private final IdentityRejectReasonMapper rejectReasonMapper;
    private final RoleMapper roleMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String typeCode) {
        return TYPE_SERVICE_CATEGORY.equals(typeCode) || TYPE_SERVICE_ITEM.equals(typeCode)
                || TYPE_COMMUNITY_SECTION.equals(typeCode) || TYPE_COMMUNITY_TOPIC.equals(typeCode)
                || TYPE_IDENTITY_REJECT_REASON.equals(typeCode) || TYPE_USER_ROLE.equals(typeCode);
    }

    @Override
    public List<DictTypeResponse> listTypes() {
        List<DictTypeResponse> result = new ArrayList<>();
        result.add(type(TYPE_SERVICE_CATEGORY, "服务分类", "服务目录维护；按校区配置的办事分类",
                serviceCategoryMapper.selectCount(null)));
        result.add(type(TYPE_SERVICE_ITEM, "服务事项", "服务目录维护；办事事项编码与名称",
                serviceItemMapper.selectCount(null)));
        result.add(type(TYPE_COMMUNITY_SECTION, "社区分区", "社区治理维护；社区分区编码与名称",
                communitySectionMapper.selectCount(null)));
        result.add(type(TYPE_COMMUNITY_TOPIC, "社区话题", "社区治理维护；话题名称、热度与状态",
                communityTopicMapper.selectCount(null)));
        result.add(type(TYPE_IDENTITY_REJECT_REASON, "认证拒绝原因", "身份审核维护；平台/校区拒绝原因模板",
                rejectReasonMapper.selectCount(null)));
        result.add(type(TYPE_USER_ROLE, "系统角色", "角色权限维护；角色编码、名称与类型",
                roleMapper.selectCount(null)));
        return result;
    }

    @Override
    public List<DictItemResponse> listItems(String typeCode, boolean includeDisabled) {
        return switch (typeCode) {
            case TYPE_SERVICE_CATEGORY -> serviceCategoryItems(includeDisabled);
            case TYPE_SERVICE_ITEM -> serviceItemItems(includeDisabled);
            case TYPE_COMMUNITY_SECTION -> communitySectionItems(includeDisabled);
            case TYPE_COMMUNITY_TOPIC -> communityTopicItems(includeDisabled);
            case TYPE_IDENTITY_REJECT_REASON -> rejectReasonItems(includeDisabled);
            case TYPE_USER_ROLE -> roleItems(includeDisabled);
            default -> List.of();
        };
    }

    private DictTypeResponse type(String code, String name, String remark, Long count) {
        return new DictTypeResponse(null, code, name, remark, "ENABLED", true, count,
                null, null, SOURCE_BUSINESS, true);
    }

    private List<DictItemResponse> serviceCategoryItems(boolean includeDisabled) {
        LambdaQueryWrapper<ServiceCategoryEntity> wrapper = new LambdaQueryWrapper<>();
        if (!includeDisabled) wrapper.eq(ServiceCategoryEntity::getStatus, "ENABLED");
        wrapper.orderByAsc(ServiceCategoryEntity::getSortOrder).orderByAsc(ServiceCategoryEntity::getCategoryCode);
        List<DictItemResponse> result = new ArrayList<>();
        long id = -2100L;
        for (ServiceCategoryEntity category : serviceCategoryMapper.selectList(wrapper)) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("campusId", category.getCampusId());
            extra.put("icon", category.getIcon());
            extra.put("description", category.getDescription());
            result.add(new DictItemResponse(id--, TYPE_SERVICE_CATEGORY, category.getCategoryCode(),
                    category.getCategoryName(), category.getCategoryCode(),
                    category.getSortOrder() == null ? 0 : category.getSortOrder(),
                    status(category.getStatus()), toJson(extra), "来源：service_category；维护入口「服务目录」"));
        }
        return result;
    }

    private List<DictItemResponse> serviceItemItems(boolean includeDisabled) {
        LambdaQueryWrapper<ServiceItemEntity> wrapper = new LambdaQueryWrapper<>();
        if (!includeDisabled) wrapper.eq(ServiceItemEntity::getStatus, "ENABLED");
        wrapper.orderByAsc(ServiceItemEntity::getSortOrder).orderByAsc(ServiceItemEntity::getItemCode);
        List<DictItemResponse> result = new ArrayList<>();
        long id = -2200L;
        for (ServiceItemEntity item : serviceItemMapper.selectList(wrapper)) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("campusId", item.getCampusId());
            extra.put("categoryId", item.getCategoryId());
            extra.put("summary", item.getSummary());
            extra.put("slaHours", item.getSlaHours());
            result.add(new DictItemResponse(id--, TYPE_SERVICE_ITEM, item.getItemCode(), item.getItemName(),
                    item.getItemCode(), item.getSortOrder() == null ? 0 : item.getSortOrder(),
                    status(item.getStatus()), toJson(extra), "来源：service_item；维护入口「服务目录」"));
        }
        return result;
    }

    private List<DictItemResponse> communitySectionItems(boolean includeDisabled) {
        LambdaQueryWrapper<CommunitySectionEntity> wrapper = new LambdaQueryWrapper<>();
        if (!includeDisabled) wrapper.eq(CommunitySectionEntity::getStatus, "ENABLED");
        wrapper.orderByAsc(CommunitySectionEntity::getSortOrder).orderByAsc(CommunitySectionEntity::getSectionCode);
        List<DictItemResponse> result = new ArrayList<>();
        long id = -2300L;
        for (CommunitySectionEntity section : communitySectionMapper.selectList(wrapper)) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("campusId", section.getCampusId());
            extra.put("icon", section.getIcon());
            extra.put("description", section.getDescription());
            result.add(new DictItemResponse(id--, TYPE_COMMUNITY_SECTION, section.getSectionCode(),
                    section.getSectionName(), section.getSectionCode(),
                    section.getSortOrder() == null ? 0 : section.getSortOrder(),
                    status(section.getStatus()), toJson(extra), "来源：community_section；维护入口「社区分区」"));
        }
        return result;
    }

    private List<DictItemResponse> communityTopicItems(boolean includeDisabled) {
        LambdaQueryWrapper<CommunityTopicEntity> wrapper = new LambdaQueryWrapper<>();
        if (!includeDisabled) wrapper.eq(CommunityTopicEntity::getStatus, "ENABLED");
        wrapper.orderByDesc(CommunityTopicEntity::getHeat).orderByAsc(CommunityTopicEntity::getName);
        List<DictItemResponse> result = new ArrayList<>();
        long id = -2400L;
        int sort = 10;
        for (CommunityTopicEntity topic : communityTopicMapper.selectList(wrapper)) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("campusId", topic.getCampusId());
            extra.put("heat", topic.getHeat());
            extra.put("recommended", topic.getRecommended());
            result.add(new DictItemResponse(id--, TYPE_COMMUNITY_TOPIC, "TOPIC_" + topic.getId(),
                    topic.getName(), topic.getName(), sort, status(topic.getStatus()), toJson(extra),
                    "来源：community_topic；维护入口「社区话题」"));
            sort += 10;
        }
        return result;
    }

    private List<DictItemResponse> rejectReasonItems(boolean includeDisabled) {
        LambdaQueryWrapper<IdentityRejectReasonEntity> wrapper = new LambdaQueryWrapper<>();
        if (!includeDisabled) wrapper.eq(IdentityRejectReasonEntity::getStatus, "ENABLED");
        wrapper.orderByAsc(IdentityRejectReasonEntity::getSortOrder)
                .orderByAsc(IdentityRejectReasonEntity::getTemplateCode);
        List<DictItemResponse> result = new ArrayList<>();
        long id = -2500L;
        for (IdentityRejectReasonEntity reason : rejectReasonMapper.selectList(wrapper)) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("campusId", reason.getCampusId());
            extra.put("builtin", reason.getBuiltin());
            result.add(new DictItemResponse(id--, TYPE_IDENTITY_REJECT_REASON, reason.getTemplateCode(),
                    reason.getTemplateText(), reason.getTemplateCode(),
                    reason.getSortOrder() == null ? 0 : reason.getSortOrder(),
                    status(reason.getStatus()), toJson(extra),
                    "来源：identity_reject_reason_template；维护入口「身份审核」"));
        }
        return result;
    }

    private List<DictItemResponse> roleItems(boolean includeDisabled) {
        LambdaQueryWrapper<RoleEntity> wrapper = new LambdaQueryWrapper<>();
        if (!includeDisabled) wrapper.eq(RoleEntity::getStatus, "ENABLED");
        wrapper.orderByAsc(RoleEntity::getRoleCode);
        List<DictItemResponse> result = new ArrayList<>();
        long id = -2600L;
        int sort = 10;
        for (RoleEntity role : roleMapper.selectList(wrapper)) {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("roleType", role.getRoleType());
            result.add(new DictItemResponse(id--, TYPE_USER_ROLE, role.getRoleCode(), role.getRoleName(),
                    role.getRoleCode(), sort, status(role.getStatus()), toJson(extra),
                    "来源：sys_role；维护入口「角色权限」"));
            sort += 10;
        }
        return result;
    }

    private String status(String value) {
        return "DISABLED".equals(value) ? "DISABLED" : "ENABLED";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
