package com.studenthub.modules.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.controller.dto.AdminOrganizationLeaderCandidateResponse;
import com.studenthub.modules.identity.controller.dto.AdminOrganizationRequest;
import com.studenthub.modules.identity.controller.dto.AdminOrganizationResponse;
import com.studenthub.modules.identity.entity.OrganizationEntity;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.entity.UserRoleEntity;
import com.studenthub.modules.identity.mapper.OrganizationMapper;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.mapper.UserRoleMapper;
import com.studenthub.modules.identity.service.AdminOrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 组织架构实现：单根树 + 校区范围（campus_scope），树形所需层级/负责人/子组织数/用户数统计、
 * 删除保护、同级排序、负责人候选。
 *
 * <p>校区范围口径：CAMPUS 具体校区（campus_id 必填，子节点必须同校区）；SCHOOL 校级
 * （campus_id 为空，可挂任意子节点）；MULTI 跨校区（campus_id 为空，不能挂校级子节点）。
 * 根节点固定为 SCHOOL + ROOT，全局仅一个；组织编码改为全局唯一。</p>
 *
 * <p>删除保护口径：根节点不可删；直接子组织数 > 0 或有用户把该组织作为默认组织、或以该组织
 * 绑定角色时拒绝删除，避免把用户与数据范围悬空。统计均按 deleted=0 逻辑过滤。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrganizationServiceImpl implements AdminOrganizationService {

    private static final String SCOPE_CAMPUS = "CAMPUS";
    private static final String SCOPE_SCHOOL = "SCHOOL";
    private static final String SCOPE_MULTI = "MULTI";
    private static final String TYPE_ROOT = "ROOT";

    private final OrganizationMapper organizationMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final AdminCampusContextService campusContextService;

    @Override
    public List<AdminOrganizationResponse> list() {
        List<OrganizationEntity> all = organizationMapper.selectList(
                new LambdaQueryWrapper<OrganizationEntity>()
                        .orderByAsc(OrganizationEntity::getSortOrder)
                        .orderByAsc(OrganizationEntity::getId));
        if (!campusContextService.isPlatformAdmin()) {
            Long campusId = campusContextService.currentCampusId();
            all = all.stream()
                    .filter(organization -> organization.getCampusId() == null
                            || campusId.equals(organization.getCampusId()))
                    .toList();
        }
        return enrich(all);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminOrganizationResponse create(Long operatorId, AdminOrganizationRequest request) {
        String scope = normalizeScope(request.campusScope());
        ensureScopeManageable(scope);
        String type = normalizeType(request.organizationType());
        Long campusId = resolveCampusId(scope, request.campusId(), null);
        if (request.parentId() == null) {
            ensureRootCreatable();
        }
        validate(request, null, campusId, scope, type);
        ensureCodeUnique(request.organizationCode().trim(), null);
        OrganizationEntity entity = new OrganizationEntity();
        entity.setParentId(request.parentId());
        entity.setCampusId(campusId);
        entity.setCampusScope(scope);
        entity.setOrganizationCode(request.organizationCode().trim());
        entity.setOrganizationName(request.organizationName().trim());
        entity.setOrganizationType(type);
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setStatus(normalizeStatus(request.status()));
        entity.setLeaderId(request.leaderId());
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        organizationMapper.insert(entity);
        log.info("组织已创建, orgId={}, scope={}, campusId={}, leaderId={}, operatorId={}",
                entity.getId(), scope, campusId, entity.getLeaderId(), operatorId);
        return findResponse(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminOrganizationResponse update(Long operatorId, Long organizationId, AdminOrganizationRequest request) {
        OrganizationEntity current = organizationMapper.selectById(organizationId);
        if (current == null) throw new BusinessException(ErrorCode.NOT_FOUND, "组织不存在");
        if (organizationId.equals(request.parentId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "组织不能作为自己的父节点");
        }
        String scope = request.campusScope() == null
                ? normalizeScope(current.getCampusScope())
                : normalizeScope(request.campusScope());
        String type = normalizeType(request.organizationType());
        if (!campusContextService.isPlatformAdmin()
                && (!SCOPE_CAMPUS.equals(normalizeScope(current.getCampusScope())) || !SCOPE_CAMPUS.equals(scope))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "普通管理员只能维护所属校区的组织");
        }
        Long campusId = resolveCampusId(scope, request.campusId(), current.getCampusId());
        boolean root = current.getParentId() == null;
        if (root) {
            if (request.parentId() != null) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "根组织不能挂到其他组织下");
            }
            requireRootShape(scope, type);
        } else if (request.parentId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "已存在根组织，不能创建第二个根节点");
        }
        validate(request, organizationId, campusId, scope, type);
        ensureChildrenCompatible(organizationId, scope, campusId);
        ensureCodeUnique(request.organizationCode().trim(), organizationId);
        OrganizationEntity update = new OrganizationEntity();
        update.setId(organizationId);
        update.setParentId(request.parentId());
        update.setCampusId(campusId);
        update.setCampusScope(scope);
        update.setOrganizationCode(request.organizationCode().trim());
        update.setOrganizationName(request.organizationName().trim());
        update.setOrganizationType(type);
        update.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        update.setStatus(normalizeStatus(request.status()));
        update.setLeaderId(request.leaderId());
        update.setUpdatedBy(operatorId);
        update.setVersion(current.getVersion());
        organizationMapper.updateById(update);
        log.info("组织已更新, orgId={}, scope={}, campusId={}, leaderId={}, operatorId={}",
                organizationId, scope, campusId, request.leaderId(), operatorId);
        return findResponse(organizationId);
    }

    @Override
    public List<AdminOrganizationLeaderCandidateResponse> leaderCandidates() {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getStatus, "ENABLED")
                .orderByAsc(UserEntity::getDisplayName)
                .orderByAsc(UserEntity::getId);
        // 平台管理员可维护校级/跨校区组织，负责人候选放开到全部校区；普通管理员仍只看本校区。
        if (!campusContextService.isPlatformAdmin()) {
            wrapper.eq(UserEntity::getCampusId, campusContextService.currentCampusId());
        }
        List<UserEntity> users = userMapper.selectList(wrapper);
        Map<Long, String> organizationNames = new HashMap<>();
        for (OrganizationEntity organization : organizationMapper.selectList(
                new LambdaQueryWrapper<OrganizationEntity>())) {
            organizationNames.put(organization.getId(), organization.getOrganizationName());
        }
        return users.stream()
                .map(user -> new AdminOrganizationLeaderCandidateResponse(user.getId(), user.getUsername(),
                        user.getDisplayName(), user.getDefaultOrganizationId(),
                        user.getDefaultOrganizationId() == null ? null
                                : organizationNames.get(user.getDefaultOrganizationId())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long operatorId, Long organizationId) {
        OrganizationEntity organization = organizationMapper.selectById(organizationId);
        if (organization == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "组织不存在");
        }
        if (organization.getParentId() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "根组织不能删除");
        }
        if (!campusContextService.isPlatformAdmin()
                && !SCOPE_CAMPUS.equals(normalizeScope(organization.getCampusScope()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "普通管理员只能删除所属校区的组织");
        }
        long childCount = organizationMapper.selectCount(new LambdaQueryWrapper<OrganizationEntity>()
                .eq(OrganizationEntity::getParentId, organizationId));
        if (childCount > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "该组织下还有 " + childCount + " 个子组织，请先删除或迁移子组织");
        }
        long userCount = countUsers(organizationId);
        if (userCount > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "该组织仍有 " + userCount + " 个用户（默认组织或角色绑定），请先迁移用户");
        }
        organizationMapper.deleteById(organizationId);
        log.info("组织已删除, orgId={}, operatorId={}", organizationId, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(Long operatorId, Long organizationId, String direction) {
        OrganizationEntity current = organizationMapper.selectById(organizationId);
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "组织不存在");
        }
        if (!campusContextService.isPlatformAdmin()
                && !SCOPE_CAMPUS.equals(normalizeScope(current.getCampusScope()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "普通管理员只能调整所属校区的组织排序");
        }
        String normalized = direction == null ? "" : direction.trim().toUpperCase(Locale.ROOT);
        if (!"UP".equals(normalized) && !"DOWN".equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "排序方向仅支持 UP/DOWN");
        }
        LambdaQueryWrapper<OrganizationEntity> wrapper = new LambdaQueryWrapper<OrganizationEntity>()
                .orderByAsc(OrganizationEntity::getSortOrder)
                .orderByAsc(OrganizationEntity::getId);
        if (current.getParentId() == null) {
            wrapper.isNull(OrganizationEntity::getParentId);
        } else {
            wrapper.eq(OrganizationEntity::getParentId, current.getParentId());
        }
        List<OrganizationEntity> siblings = new ArrayList<>(organizationMapper.selectList(wrapper));
        int index = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).getId().equals(organizationId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "组织不存在");
        }
        int target = "UP".equals(normalized) ? index - 1 : index + 1;
        if (target < 0 || target >= siblings.size()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "UP".equals(normalized) ? "已经是同级第一个组织" : "已经是同级最后一个组织");
        }
        siblings.add(target, siblings.remove(index));
        for (int i = 0; i < siblings.size(); i++) {
            OrganizationEntity sibling = siblings.get(i);
            int newSort = (i + 1) * 10;
            if (Objects.equals(sibling.getSortOrder(), newSort)) {
                continue;
            }
            OrganizationEntity update = new OrganizationEntity();
            update.setId(sibling.getId());
            update.setSortOrder(newSort);
            update.setUpdatedBy(operatorId);
            update.setVersion(sibling.getVersion());
            organizationMapper.updateById(update);
        }
        log.info("组织排序已调整, orgId={}, direction={}, operatorId={}", organizationId, normalized, operatorId);
    }

    /**
     * 批量装配层级、负责人、子组织数与用户数，避免逐节点查询造成 N+1。
     */
    private List<AdminOrganizationResponse> enrich(List<OrganizationEntity> all) {
        Map<Long, OrganizationEntity> byId = new LinkedHashMap<>();
        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        for (OrganizationEntity organization : all) {
            byId.put(organization.getId(), organization);
            childrenByParent.computeIfAbsent(organization.getParentId(), ignored -> new ArrayList<>())
                    .add(organization.getId());
        }
        Map<Long, Integer> levels = new HashMap<>();
        for (OrganizationEntity organization : all) {
            levels.put(organization.getId(), resolveLevel(organization, byId, levels, 0));
        }
        Map<Long, Set<Long>> usersByOrganization = loadUsersByOrganization(new ArrayList<>(byId.keySet()));
        Map<Long, String> leaderNames = new HashMap<>();
        Set<Long> leaderIds = all.stream().map(OrganizationEntity::getLeaderId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (!leaderIds.isEmpty()) {
            for (UserEntity user : userMapper.selectBatchIds(leaderIds)) {
                leaderNames.put(user.getId(), StringUtils.hasText(user.getDisplayName())
                        ? user.getDisplayName() : user.getUsername());
            }
        }
        return all.stream()
                .map(organization -> AdminOrganizationResponse.from(organization,
                        leaderNames.get(organization.getLeaderId()),
                        levels.getOrDefault(organization.getId(), 1),
                        childrenByParent.getOrDefault(organization.getId(), List.of()).size(),
                        usersByOrganization.getOrDefault(organization.getId(), Set.of()).size()))
                .toList();
    }

    private int resolveLevel(OrganizationEntity organization, Map<Long, OrganizationEntity> byId,
                             Map<Long, Integer> levels, int depth) {
        Integer cached = levels.get(organization.getId());
        if (cached != null) {
            return cached;
        }
        if (organization.getParentId() == null || depth > 32 || !byId.containsKey(organization.getParentId())) {
            levels.put(organization.getId(), depth + 1);
            return depth + 1;
        }
        int level = resolveLevel(byId.get(organization.getParentId()), byId, levels, depth + 1);
        levels.put(organization.getId(), level + 1);
        return level + 1;
    }

    private Map<Long, Set<Long>> loadUsersByOrganization(List<Long> organizationIds) {
        Map<Long, Set<Long>> usersByOrganization = new HashMap<>();
        if (organizationIds.isEmpty()) {
            return usersByOrganization;
        }
        for (UserEntity user : userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .select(UserEntity::getId, UserEntity::getDefaultOrganizationId)
                .in(UserEntity::getDefaultOrganizationId, organizationIds))) {
            usersByOrganization.computeIfAbsent(user.getDefaultOrganizationId(),
                    ignored -> new HashSet<>()).add(user.getId());
        }
        for (UserRoleEntity binding : userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                .select(UserRoleEntity::getUserId, UserRoleEntity::getOrganizationId)
                .in(UserRoleEntity::getOrganizationId, organizationIds))) {
            usersByOrganization.computeIfAbsent(binding.getOrganizationId(),
                    ignored -> new HashSet<>()).add(binding.getUserId());
        }
        return usersByOrganization;
    }

    private long countUsers(Long organizationId) {
        // 按用户去重：同一用户既以默认组织、又以角色绑定到该组织时只计 1 人
        return loadUsersByOrganization(List.of(organizationId))
                .getOrDefault(organizationId, Set.of()).size();
    }

    private AdminOrganizationResponse findResponse(Long organizationId) {
        return list().stream()
                .filter(item -> item.organizationId().equals(organizationId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "组织不存在"));
    }

    private void validate(AdminOrganizationRequest request, Long selfId, Long campusId, String scope, String type) {
        if (request.organizationCode() == null || request.organizationCode().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "组织编码不能为空");
        }
        if (SCOPE_CAMPUS.equals(scope)) {
            if (campusId == null) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "具体校区的组织必须指定校区");
            }
        } else if (campusId != null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "校级/跨校区组织不能绑定具体校区");
        }
        if (request.parentId() == null) {
            requireRootShape(scope, type);
        } else {
            if (TYPE_ROOT.equals(type)) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "只有根组织可以使用 ROOT 类型");
            }
            OrganizationEntity parent = organizationMapper.selectById(request.parentId());
            if (parent == null) throw new BusinessException(ErrorCode.INVALID_PARAMETER, "父组织不存在");
            String parentScope = normalizeScope(parent.getCampusScope());
            if (!campusContextService.isPlatformAdmin() && parent.getParentId() != null
                    && !SCOPE_CAMPUS.equals(parentScope)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "普通管理员只能在所属校区的学院/部门下维护组织");
            }
            if (SCOPE_CAMPUS.equals(parentScope)) {
                if (!SCOPE_CAMPUS.equals(scope) || !Objects.equals(parent.getCampusId(), campusId)) {
                    throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                            "父组织是具体校区节点，子组织必须属于同一校区");
                }
            } else if (SCOPE_MULTI.equals(parentScope) && SCOPE_SCHOOL.equals(scope)) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "跨校区组织下不能挂校级节点");
            }
        }
        if (request.leaderId() != null) {
            UserEntity leader = userMapper.selectById(request.leaderId());
            if (leader == null) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "负责人用户不存在");
            }
            if (SCOPE_CAMPUS.equals(scope) && !Objects.equals(leader.getCampusId(), campusId)) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "负责人不属于当前校区");
            }
            if (!"ENABLED".equals(leader.getStatus())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "负责人账号已停用，不能指定");
            }
        }
        if (selfId == null) return;
        Long current = request.parentId();
        int guard = 0;
        while (current != null && guard++ < 32) {
            if (selfId.equals(current)) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "组织层级不能形成循环");
            }
            OrganizationEntity parent = organizationMapper.selectById(current);
            current = parent == null ? null : parent.getParentId();
        }
    }

    private void requireRootShape(String scope, String type) {
        if (!SCOPE_SCHOOL.equals(scope) || !TYPE_ROOT.equals(type)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "根组织必须是 ROOT 类型且校区范围为校级");
        }
    }

    private void ensureRootCreatable() {
        Long roots = organizationMapper.selectCount(new LambdaQueryWrapper<OrganizationEntity>()
                .isNull(OrganizationEntity::getParentId));
        if (roots != null && roots > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "已存在根组织，不能重复创建");
        }
    }

    private void ensureCodeUnique(String organizationCode, Long excludeId) {
        LambdaQueryWrapper<OrganizationEntity> wrapper = new LambdaQueryWrapper<OrganizationEntity>()
                .eq(OrganizationEntity::getOrganizationCode, organizationCode);
        if (excludeId != null) {
            wrapper.ne(OrganizationEntity::getId, excludeId);
        }
        Long duplicate = organizationMapper.selectCount(wrapper);
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "组织编码已存在");
        }
    }

    /** 校级/跨校区节点涉及多校区数据，只允许平台管理员维护；普通管理员仅维护具体校区节点。 */
    private void ensureScopeManageable(String scope) {
        if (!campusContextService.isPlatformAdmin() && !SCOPE_CAMPUS.equals(scope)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有平台管理员可以维护校级/跨校区组织");
        }
    }

    /** 调整节点自身范围后，已有子节点必须仍满足父子范围规则，避免存量数据越界。 */
    private void ensureChildrenCompatible(Long organizationId, String scope, Long campusId) {
        List<OrganizationEntity> children = organizationMapper.selectList(
                new LambdaQueryWrapper<OrganizationEntity>()
                        .eq(OrganizationEntity::getParentId, organizationId));
        for (OrganizationEntity child : children) {
            String childScope = normalizeScope(child.getCampusScope());
            if (SCOPE_CAMPUS.equals(scope)
                    && (!SCOPE_CAMPUS.equals(childScope)
                    || !Objects.equals(child.getCampusId(), campusId))) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                        "子组织「" + child.getOrganizationName() + "」不属于目标校区，请先调整子组织");
            }
            if (SCOPE_MULTI.equals(scope) && SCOPE_SCHOOL.equals(childScope)) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                        "子组织「" + child.getOrganizationName() + "」是校级节点，不能挂到跨校区组织下");
            }
        }
    }

    private Long resolveCampusId(String scope, Long requestedCampusId, Long fallbackCampusId) {
        if (!SCOPE_CAMPUS.equals(scope)) {
            return null;
        }
        Long campusId = requestedCampusId != null ? requestedCampusId
                : (fallbackCampusId != null ? fallbackCampusId : campusContextService.currentCampusId());
        boolean accessible = campusContextService.accessibleCampuses().stream()
                .anyMatch(campus -> campus.campusId().equals(campusId));
        if (!accessible) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "校区不存在或不属于当前账号");
        }
        return campusId;
    }

    private String normalizeScope(String scope) {
        String value = scope == null ? SCOPE_CAMPUS : scope.trim().toUpperCase(Locale.ROOT);
        if (!SCOPE_CAMPUS.equals(value) && !SCOPE_SCHOOL.equals(value) && !SCOPE_MULTI.equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "校区范围仅支持 CAMPUS/SCHOOL/MULTI");
        }
        return value;
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "组织类型不能为空");
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!"ENABLED".equals(value) && !"DISABLED".equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "状态仅支持 ENABLED/DISABLED");
        }
        return value;
    }
}
