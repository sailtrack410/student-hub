package com.studenthub.modules.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.dict.controller.dto.DictItemResponse;
import com.studenthub.modules.dict.controller.dto.DictItemUpsertRequest;
import com.studenthub.modules.dict.controller.dto.DictTypeResponse;
import com.studenthub.modules.dict.controller.dto.DictTypeUpsertRequest;
import com.studenthub.modules.dict.entity.SysDictItemEntity;
import com.studenthub.modules.dict.entity.SysDictTypeEntity;
import com.studenthub.modules.dict.mapper.SysDictItemMapper;
import com.studenthub.modules.dict.mapper.SysDictTypeMapper;
import com.studenthub.modules.dict.provider.SystemDictProvider;
import com.studenthub.modules.dict.service.DictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字典管理实现：typeCode/itemCode 创建后不可修改（外部按编码引用），内置字典禁止删除。
 * 状态仅允许 ENABLED/DISABLED；extra 非空时必须是合法 JSON。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final SysDictTypeMapper typeMapper;
    private final SysDictItemMapper itemMapper;
    private final ObjectMapper objectMapper;
    private final List<SystemDictProvider> systemDictProviders;

    /** App 侧启用字典项只读缓存；管理端写操作后按 typeCode 失效。 */
    private final Map<String, List<DictItemResponse>> enabledCache = new ConcurrentHashMap<>();

    @Override
    public List<DictTypeResponse> listTypes(String keyword, String status) {
        LambdaQueryWrapper<SysDictTypeEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(SysDictTypeEntity::getTypeCode, keyword.trim())
                    .or().like(SysDictTypeEntity::getTypeName, keyword.trim()));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SysDictTypeEntity::getStatus, normalizeStatus(status));
        }
        wrapper.orderByAsc(SysDictTypeEntity::getTypeCode);
        List<DictTypeResponse> result = new ArrayList<>(typeMapper.selectList(wrapper).stream()
                .map(this::toTypeResponse).toList());
        String statusFilter = StringUtils.hasText(status) ? normalizeStatus(status) : null;
        for (SystemDictProvider provider : systemDictProviders) {
            for (DictTypeResponse type : provider.listTypes()) {
                if (statusFilter != null && !statusFilter.equals(type.status())) {
                    continue;
                }
                if (StringUtils.hasText(keyword) && !matchesKeyword(type, keyword.trim())) {
                    continue;
                }
                result.add(type);
            }
        }
        result.sort(Comparator.comparing(DictTypeResponse::typeCode));
        return result;
    }

    @Override
    public DictTypeResponse createType(DictTypeUpsertRequest request) {
        String code = request.typeCode().trim();
        if (findProvider(code) != null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该字典编码由系统模块维护，不能手动创建");
        }
        if (findTypeByCode(code) != null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "字典编码已存在");
        }
        SysDictTypeEntity entity = new SysDictTypeEntity();
        entity.setTypeCode(code);
        entity.setTypeName(request.typeName().trim());
        entity.setRemark(request.remark());
        entity.setStatus(normalizeStatus(request.status()));
        entity.setBuiltin(false);
        typeMapper.insert(entity);
        return toTypeResponse(entity);
    }

    @Override
    public DictTypeResponse updateType(Long id, DictTypeUpsertRequest request) {
        SysDictTypeEntity entity = requireType(id);
        if (StringUtils.hasText(request.typeCode()) && !request.typeCode().trim().equals(entity.getTypeCode())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "字典编码创建后不可修改");
        }
        SysDictTypeEntity update = new SysDictTypeEntity();
        update.setId(entity.getId());
        update.setTypeName(request.typeName().trim());
        update.setRemark(request.remark());
        update.setStatus(normalizeStatus(request.status()));
        update.setVersion(entity.getVersion());
        typeMapper.updateById(update);
        enabledCache.remove(entity.getTypeCode());
        return toTypeResponse(typeMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id) {
        SysDictTypeEntity entity = requireType(id);
        if (Boolean.TRUE.equals(entity.getBuiltin())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "内置字典不允许删除，可改为停用");
        }
        LambdaQueryWrapper<SysDictItemEntity> items = new LambdaQueryWrapper<>();
        items.eq(SysDictItemEntity::getTypeCode, entity.getTypeCode());
        itemMapper.delete(items);
        typeMapper.deleteById(entity.getId());
        enabledCache.remove(entity.getTypeCode());
        log.info("字典类型已删除, typeCode={}, operatorId 由审计字段记录", entity.getTypeCode());
    }

    @Override
    public List<DictItemResponse> listItems(String typeCode, boolean includeDisabled) {
        SysDictTypeEntity type = findTypeByCode(typeCode);
        if (type == null) {
            SystemDictProvider provider = findProvider(typeCode);
            if (provider != null) {
                return provider.listItems(typeCode, includeDisabled);
            }
            throw new BusinessException(ErrorCode.NOT_FOUND, "字典类型不存在");
        }
        LambdaQueryWrapper<SysDictItemEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictItemEntity::getTypeCode, typeCode);
        if (!includeDisabled) {
            wrapper.eq(SysDictItemEntity::getStatus, STATUS_ENABLED);
        }
        wrapper.orderByAsc(SysDictItemEntity::getSortOrder).orderByAsc(SysDictItemEntity::getItemCode);
        return itemMapper.selectList(wrapper).stream().map(this::toItemResponse).toList();
    }

    @Override
    public DictItemResponse createItem(DictItemUpsertRequest request) {
        String typeCode = request.typeCode() == null ? null : request.typeCode().trim();
        if (!StringUtils.hasText(typeCode)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "字典编码不能为空");
        }
        requireWritableTypeByCode(typeCode);
        String itemCode = request.itemCode().trim();
        if (findItem(typeCode, itemCode, null) != null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "该字典下已存在相同字典项编码");
        }
        validateExtra(request.extra());
        SysDictItemEntity entity = new SysDictItemEntity();
        entity.setTypeCode(typeCode);
        entity.setItemCode(itemCode);
        entity.setItemLabel(request.itemLabel().trim());
        entity.setItemValue(StringUtils.hasText(request.itemValue()) ? request.itemValue().trim() : request.itemLabel().trim());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setStatus(normalizeStatus(request.status()));
        entity.setExtra(request.extra());
        entity.setRemark(request.remark());
        itemMapper.insert(entity);
        enabledCache.remove(typeCode);
        return toItemResponse(entity);
    }

    @Override
    public DictItemResponse updateItem(Long id, DictItemUpsertRequest request) {
        SysDictItemEntity entity = requireItem(id);
        ensureWritable(entity.getTypeCode());
        if (StringUtils.hasText(request.typeCode()) && !request.typeCode().trim().equals(entity.getTypeCode())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "字典项所属字典不可修改");
        }
        if (StringUtils.hasText(request.itemCode()) && !request.itemCode().trim().equals(entity.getItemCode())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "字典项编码创建后不可修改");
        }
        validateExtra(request.extra());
        SysDictItemEntity update = new SysDictItemEntity();
        update.setId(entity.getId());
        update.setItemLabel(request.itemLabel().trim());
        update.setItemValue(StringUtils.hasText(request.itemValue()) ? request.itemValue().trim() : request.itemLabel().trim());
        update.setSortOrder(request.sortOrder() == null ? entity.getSortOrder() : request.sortOrder());
        update.setStatus(normalizeStatus(request.status()));
        update.setExtra(request.extra());
        update.setRemark(request.remark());
        update.setVersion(entity.getVersion());
        itemMapper.updateById(update);
        enabledCache.remove(entity.getTypeCode());
        return toItemResponse(itemMapper.selectById(entity.getId()));
    }

    @Override
    public void deleteItem(Long id) {
        SysDictItemEntity entity = requireItem(id);
        ensureWritable(entity.getTypeCode());
        itemMapper.deleteById(id);
        enabledCache.remove(entity.getTypeCode());
    }

    @Override
    public List<DictItemResponse> enabledItems(String typeCode) {
        List<DictItemResponse> cached = enabledCache.get(typeCode);
        if (cached != null) {
            return cached;
        }
        SysDictTypeEntity type = findTypeByCode(typeCode);
        if (type == null) {
            SystemDictProvider provider = findProvider(typeCode);
            return provider == null ? List.of() : provider.listItems(typeCode, false);
        }
        if (!STATUS_ENABLED.equals(type.getStatus())) {
            return List.of();
        }
        LambdaQueryWrapper<SysDictItemEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictItemEntity::getTypeCode, typeCode)
                .eq(SysDictItemEntity::getStatus, STATUS_ENABLED)
                .orderByAsc(SysDictItemEntity::getSortOrder)
                .orderByAsc(SysDictItemEntity::getItemCode);
        List<DictItemResponse> result = itemMapper.selectList(wrapper).stream()
                .map(this::toItemResponse).toList();
        enabledCache.put(typeCode, result);
        return result;
    }

    @Override
    public void refreshCache() {
        enabledCache.clear();
    }

    private SysDictTypeEntity requireType(Long id) {
        SysDictTypeEntity entity = typeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "字典类型不存在");
        }
        return entity;
    }

    private SysDictTypeEntity requireWritableTypeByCode(String typeCode) {
        SysDictTypeEntity entity = findTypeByCode(typeCode);
        if (entity != null) {
            return entity;
        }
        if (findProvider(typeCode) != null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "该字典由系统模块维护，只能只读查看，请到对应业务页面修改");
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "字典类型不存在");
    }

    private void ensureWritable(String typeCode) {
        if (findProvider(typeCode) != null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "该字典由系统模块维护，只能只读查看，请到对应业务页面修改");
        }
    }

    private SystemDictProvider findProvider(String typeCode) {
        if (!StringUtils.hasText(typeCode)) {
            return null;
        }
        return systemDictProviders.stream()
                .filter(provider -> provider.supports(typeCode.trim()))
                .findFirst().orElse(null);
    }

    private boolean matchesKeyword(DictTypeResponse type, String keyword) {
        String lower = keyword.toLowerCase();
        return type.typeCode().toLowerCase().contains(lower)
                || type.typeName().toLowerCase().contains(lower);
    }

    private SysDictTypeEntity findTypeByCode(String typeCode) {
        if (!StringUtils.hasText(typeCode)) {
            return null;
        }
        LambdaQueryWrapper<SysDictTypeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictTypeEntity::getTypeCode, typeCode.trim());
        return typeMapper.selectOne(wrapper);
    }

    private SysDictItemEntity requireItem(Long id) {
        SysDictItemEntity entity = itemMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "字典项不存在");
        }
        return entity;
    }

    private SysDictItemEntity findItem(String typeCode, String itemCode, Long excludeId) {
        LambdaQueryWrapper<SysDictItemEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictItemEntity::getTypeCode, typeCode)
                .eq(SysDictItemEntity::getItemCode, itemCode);
        if (excludeId != null) {
            wrapper.ne(SysDictItemEntity::getId, excludeId);
        }
        return itemMapper.selectOne(wrapper);
    }

    private void validateExtra(String extra) {
        if (!StringUtils.hasText(extra)) {
            return;
        }
        try {
            objectMapper.readTree(extra);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "扩展字段必须是合法 JSON");
        }
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return STATUS_ENABLED;
        }
        String value = status.trim().toUpperCase();
        if (!STATUS_ENABLED.equals(value) && !STATUS_DISABLED.equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "状态仅支持 ENABLED/DISABLED");
        }
        return value;
    }

    private DictTypeResponse toTypeResponse(SysDictTypeEntity entity) {
        LambdaQueryWrapper<SysDictItemEntity> items = new LambdaQueryWrapper<>();
        items.eq(SysDictItemEntity::getTypeCode, entity.getTypeCode());
        Long count = itemMapper.selectCount(items);
        return new DictTypeResponse(entity.getId(), entity.getTypeCode(), entity.getTypeName(),
                entity.getRemark(), entity.getStatus(), entity.getBuiltin(), count,
                entity.getCreatedAt(), entity.getUpdatedAt(), "MANUAL", false);
    }

    private DictItemResponse toItemResponse(SysDictItemEntity entity) {
        return new DictItemResponse(entity.getId(), entity.getTypeCode(), entity.getItemCode(),
                entity.getItemLabel(), entity.getItemValue(), entity.getSortOrder(), entity.getStatus(),
                entity.getExtra(), entity.getRemark());
    }
}
