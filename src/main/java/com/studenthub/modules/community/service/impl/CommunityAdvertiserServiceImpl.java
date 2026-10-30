package com.studenthub.modules.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.community.controller.dto.AdminAdvertiserPageQuery;
import com.studenthub.modules.community.controller.dto.AdminAdvertiserRequest;
import com.studenthub.modules.community.controller.dto.AdminAdvertiserResponse;
import com.studenthub.modules.community.entity.CommunityAdvertiserEntity;
import com.studenthub.modules.community.mapper.CommunityAdvertiserMapper;
import com.studenthub.modules.community.service.CommunityAdvertiserService;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 管理端社区广告主实现：所有查询/写入按管理端校区上下文隔离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityAdvertiserServiceImpl implements CommunityAdvertiserService {

    private static final Set<String> STATUSES = Set.of("ENABLED", "DISABLED");
    private static final int MAX_PAGE_SIZE = 50;

    private final CommunityAdvertiserMapper advertiserMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AdminCampusContextService campusContextService;

    @Override
    public PageResult<AdminAdvertiserResponse> page(AdminAdvertiserPageQuery query) {
        int current = Math.max(1, query.pageNumOrDefault());
        int size = Math.max(1, Math.min(query.pageSizeOrDefault(), MAX_PAGE_SIZE));
        String status = normalizeEnum(query.status());
        if (status != null && !STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "广告主状态仅支持 ENABLED/DISABLED");
        }
        LambdaQueryWrapper<CommunityAdvertiserEntity> wrapper = new LambdaQueryWrapper<CommunityAdvertiserEntity>()
                .eq(CommunityAdvertiserEntity::getCampusId, campusId())
                .eq(status != null, CommunityAdvertiserEntity::getStatus, status)
                .and(StringUtils.hasText(query.keyword()), w -> {
                    String keyword = query.keyword().trim();
                    w.like(CommunityAdvertiserEntity::getName, keyword)
                            .or().like(CommunityAdvertiserEntity::getContactName, keyword)
                            .or().like(CommunityAdvertiserEntity::getContactPhone, keyword);
                })
                .orderByDesc(CommunityAdvertiserEntity::getId);
        Page<CommunityAdvertiserEntity> page = advertiserMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(),
                page.getRecords().stream().map(AdminAdvertiserResponse::from).toList());
    }

    @Override
    public AdminAdvertiserResponse detail(Long advertiserId) {
        return AdminAdvertiserResponse.from(requireAdvertiser(advertiserId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAdvertiserResponse create(AdminAdvertiserRequest request) {
        CommunityAdvertiserEntity entity = new CommunityAdvertiserEntity();
        entity.setCampusId(campusId());
        entity.setName(request.name().trim());
        entity.setContactName(trimToNull(request.contactName()));
        entity.setContactPhone(trimToNull(request.contactPhone()));
        entity.setStatus(normalizeStatus(request.status()));
        entity.setRemark(trimToNull(request.remark()));
        advertiserMapper.insert(entity);
        log.info("创建社区广告主, advertiserId={}, name={}, operatorId={}",
                entity.getId(), entity.getName(), currentUserProvider.currentUserIdOrSystem());
        return AdminAdvertiserResponse.from(advertiserMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAdvertiserResponse update(Long advertiserId, AdminAdvertiserRequest request) {
        CommunityAdvertiserEntity existing = requireAdvertiser(advertiserId);
        CommunityAdvertiserEntity update = new CommunityAdvertiserEntity();
        update.setId(existing.getId());
        update.setName(request.name().trim());
        update.setContactName(trimToNull(request.contactName()));
        update.setContactPhone(trimToNull(request.contactPhone()));
        // 编辑时未显式传状态则保持原状态，避免误把已停用广告主重新启用
        update.setStatus(normalizeEnum(request.status()) == null
                ? existing.getStatus() : normalizeStatus(request.status()));
        update.setRemark(trimToNull(request.remark()));
        update.setVersion(existing.getVersion());
        advertiserMapper.updateById(update);
        log.info("更新社区广告主, advertiserId={}, operatorId={}", advertiserId,
                currentUserProvider.currentUserIdOrSystem());
        return AdminAdvertiserResponse.from(advertiserMapper.selectById(advertiserId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAdvertiserResponse updateStatus(Long advertiserId, String status) {
        String target = normalizeEnum(status);
        if (target == null || !STATUSES.contains(target)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "广告主状态仅支持 ENABLED/DISABLED");
        }
        CommunityAdvertiserEntity existing = requireAdvertiser(advertiserId);
        CommunityAdvertiserEntity update = new CommunityAdvertiserEntity();
        update.setId(existing.getId());
        update.setStatus(target);
        update.setVersion(existing.getVersion());
        advertiserMapper.updateById(update);
        log.info("社区广告主启停, advertiserId={}, status={}, operatorId={}", advertiserId, target,
                currentUserProvider.currentUserIdOrSystem());
        return AdminAdvertiserResponse.from(advertiserMapper.selectById(advertiserId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long advertiserId) {
        CommunityAdvertiserEntity existing = requireAdvertiser(advertiserId);
        advertiserMapper.deleteById(existing.getId());
        log.info("删除社区广告主, advertiserId={}, operatorId={}", advertiserId,
                currentUserProvider.currentUserIdOrSystem());
    }

    private CommunityAdvertiserEntity requireAdvertiser(Long advertiserId) {
        CommunityAdvertiserEntity entity = advertiserMapper.selectById(advertiserId);
        if (entity == null || !campusId().equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "广告主不存在");
        }
        return entity;
    }

    private Long campusId() {
        return campusContextService.currentCampusId();
    }

    private static String normalizeStatus(String value) {
        String normalized = normalizeEnum(value);
        if (normalized == null) {
            return "ENABLED";
        }
        if (!STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "广告主状态仅支持 ENABLED/DISABLED");
        }
        return normalized;
    }

    private static String normalizeEnum(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
