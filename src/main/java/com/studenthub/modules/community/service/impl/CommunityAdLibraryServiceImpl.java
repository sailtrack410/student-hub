package com.studenthub.modules.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.community.controller.dto.AdminAdMaterialPageQuery;
import com.studenthub.modules.community.controller.dto.AdminAdMaterialRequest;
import com.studenthub.modules.community.controller.dto.AdminAdMaterialResponse;
import com.studenthub.modules.community.controller.dto.AdminAdMaterialReviewRequest;
import com.studenthub.modules.community.controller.dto.AdminAdPlanPageQuery;
import com.studenthub.modules.community.controller.dto.AdminAdPlanRequest;
import com.studenthub.modules.community.controller.dto.AdminAdPlanResponse;
import com.studenthub.modules.community.entity.CommunityAdMaterialEntity;
import com.studenthub.modules.community.entity.CommunityAdPlanEntity;
import com.studenthub.modules.community.entity.CommunityAdvertiserEntity;
import com.studenthub.modules.community.entity.CommunityDirectAdEntity;
import com.studenthub.modules.community.mapper.CommunityAdMaterialMapper;
import com.studenthub.modules.community.mapper.CommunityAdPlanMapper;
import com.studenthub.modules.community.mapper.CommunityAdvertiserMapper;
import com.studenthub.modules.community.mapper.CommunityDirectAdMapper;
import com.studenthub.modules.community.service.CommunityAdLibraryService;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 投放计划与素材库实现（C13）。约束：
 * <ol>
 *   <li>计划/素材均按当前管理校区隔离；广告主/素材必须同校区；</li>
 *   <li>素材审核状态 PENDING → APPROVED/REJECTED，仅 PENDING 可审核；仅 APPROVED 素材可被直投广告引用；</li>
 *   <li>计划/素材被直投广告引用时禁止删除，提示先解除关联，避免投放池出现悬空引用；</li>
 *   <li>不伪造任何曝光/点击数据：计划/素材只做配置与审核，统计仍来自 community_ad_event。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityAdLibraryServiceImpl implements CommunityAdLibraryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> PLAN_STATUSES = Set.of("DRAFT", "ENABLED", "DISABLED");
    private static final Set<String> PLAN_OBJECTIVES = Set.of("AWARENESS", "TRAFFIC", "CONVERSION");
    private static final Set<String> SLOTS = Set.of("FEED", "BANNER", "SPLASH", "SERVICE_HALL");
    private static final Set<String> MATERIAL_TYPES = Set.of("IMAGE", "VIDEO", "TEXT");
    private static final Set<String> MATERIAL_REVIEW_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");
    private static final Set<String> MATERIAL_CONFIG_STATUSES = Set.of("ENABLED", "DISABLED");

    private final CommunityAdPlanMapper planMapper;
    private final CommunityAdMaterialMapper materialMapper;
    private final CommunityDirectAdMapper adMapper;
    private final CommunityAdvertiserMapper advertiserMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AdminCampusContextService campusContextService;

    // ------------------------------------------------------------------
    // 投放计划
    // ------------------------------------------------------------------

    @Override
    public PageResult<AdminAdPlanResponse> pagePlans(AdminAdPlanPageQuery query) {
        Long campusId = campusId();
        LambdaQueryWrapper<CommunityAdPlanEntity> wrapper = new LambdaQueryWrapper<CommunityAdPlanEntity>()
                .eq(CommunityAdPlanEntity::getCampusId, campusId)
                .eq(StringUtils.hasText(query.status()), CommunityAdPlanEntity::getStatus,
                        normalize(query.status()))
                .eq(StringUtils.hasText(query.slot()), CommunityAdPlanEntity::getSlot, normalize(query.slot()))
                .eq(query.advertiserId() != null, CommunityAdPlanEntity::getAdvertiserId, query.advertiserId())
                .like(StringUtils.hasText(query.keyword()), CommunityAdPlanEntity::getPlanName,
                        query.keyword() == null ? null : query.keyword().trim())
                .orderByDesc(CommunityAdPlanEntity::getUpdatedAt);
        Page<CommunityAdPlanEntity> page = planMapper.selectPage(new Page<>(
                query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
        Map<Long, String> advertiserNames = advertiserNames(page.getRecords().stream()
                .map(CommunityAdPlanEntity::getAdvertiserId).toList());
        Map<Long, Long> adCounts = adCountsByPlan(page.getRecords().stream()
                .map(CommunityAdPlanEntity::getId).toList());
        LocalDateTime now = LocalDateTime.now(ZONE);
        List<AdminAdPlanResponse> records = page.getRecords().stream()
                .map(plan -> AdminAdPlanResponse.from(plan, advertiserNames.get(plan.getAdvertiserId()),
                        adCounts.getOrDefault(plan.getId(), 0L), now))
                .toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAdPlanResponse createPlan(AdminAdPlanRequest request) {
        CommunityAdPlanEntity entity = new CommunityAdPlanEntity();
        entity.setCampusId(campusId());
        applyPlan(entity, request);
        entity.setStatus(CommunityAdPlanEntity.STATUS_DRAFT);
        planMapper.insert(entity);
        log.info("创建投放计划, planId={}, name={}, operatorId={}", entity.getId(), entity.getPlanName(),
                currentUserProvider.requireCurrentUser().userId());
        return AdminAdPlanResponse.from(entity, advertiserName(entity.getAdvertiserId()), 0L,
                LocalDateTime.now(ZONE));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAdPlanResponse updatePlan(Long planId, AdminAdPlanRequest request) {
        CommunityAdPlanEntity entity = requirePlan(planId);
        applyPlan(entity, request);
        planMapper.updateById(entity);
        return AdminAdPlanResponse.from(entity, advertiserName(entity.getAdvertiserId()),
                countAdsByPlan(planId), LocalDateTime.now(ZONE));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAdPlanResponse updatePlanStatus(Long planId, String status) {
        String target = normalize(status);
        if (target == null || !PLAN_STATUSES.contains(target)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "计划状态仅支持 DRAFT/ENABLED/DISABLED");
        }
        CommunityAdPlanEntity entity = requirePlan(planId);
        if ("ENABLED".equals(target)) {
            if (entity.getEndAt() == null || !entity.getEndAt().isAfter(LocalDateTime.now(ZONE))) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "投放结束时间已过，请先调整排期");
            }
        }
        CommunityAdPlanEntity update = new CommunityAdPlanEntity();
        update.setId(entity.getId());
        update.setStatus(target);
        update.setVersion(entity.getVersion());
        planMapper.updateById(update);
        entity.setStatus(target);
        return AdminAdPlanResponse.from(entity, advertiserName(entity.getAdvertiserId()),
                countAdsByPlan(planId), LocalDateTime.now(ZONE));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePlan(Long planId) {
        CommunityAdPlanEntity entity = requirePlan(planId);
        long linked = countAdsByPlan(entity.getId());
        if (linked > 0) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID,
                    "该计划下还有 " + linked + " 条直投广告，请先解除关联再删除");
        }
        planMapper.deleteById(entity.getId());
        log.info("删除投放计划, planId={}, operatorId={}", planId,
                currentUserProvider.requireCurrentUser().userId());
    }

    private void applyPlan(CommunityAdPlanEntity entity, AdminAdPlanRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "计划结束时间必须晚于开始时间");
        }
        String objective = normalize(request.objective());
        if (objective == null) {
            objective = "AWARENESS";
        }
        if (!PLAN_OBJECTIVES.contains(objective)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "投放目标仅支持 AWARENESS/TRAFFIC/CONVERSION");
        }
        String slot = normalize(request.slot());
        if (slot == null) {
            slot = "FEED";
        }
        if (!SLOTS.contains(slot)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "广告位仅支持 FEED/BANNER/SPLASH/SERVICE_HALL");
        }
        if (request.budgetCents() != null && request.budgetCents() < 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "总预算不能为负数");
        }
        if (request.dailyBudgetCents() != null && request.dailyBudgetCents() < 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "日预算不能为负数");
        }
        entity.setAdvertiserId(requireAdvertiserId(request.advertiserId()));
        entity.setPlanName(request.planName().trim());
        entity.setObjective(objective);
        entity.setSlot(slot);
        entity.setStartAt(request.startAt());
        entity.setEndAt(request.endAt());
        entity.setBudgetCents(request.budgetCents());
        entity.setDailyBudgetCents(request.dailyBudgetCents());
        entity.setRemark(trimToNull(request.remark()));
    }

    private CommunityAdPlanEntity requirePlan(Long planId) {
        CommunityAdPlanEntity entity = planMapper.selectById(planId);
        if (entity == null || !campusId().equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "投放计划不存在或不属于当前校区");
        }
        return entity;
    }

    private long countAdsByPlan(Long planId) {
        Long count = adMapper.selectCount(new LambdaQueryWrapper<CommunityDirectAdEntity>()
                .eq(CommunityDirectAdEntity::getPlanId, planId));
        return count == null ? 0 : count;
    }

    private Map<Long, Long> adCountsByPlan(List<Long> planIds) {
        List<Long> ids = planIds.stream().filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (CommunityDirectAdEntity ad : adMapper.selectList(new LambdaQueryWrapper<CommunityDirectAdEntity>()
                .in(CommunityDirectAdEntity::getPlanId, ids)
                .select(CommunityDirectAdEntity::getPlanId))) {
            counts.merge(ad.getPlanId(), 1L, Long::sum);
        }
        return counts;
    }

    // ------------------------------------------------------------------
    // 素材库
    // ------------------------------------------------------------------

    @Override
    public PageResult<AdminAdMaterialResponse> pageMaterials(AdminAdMaterialPageQuery query) {
        Long campusId = campusId();
        LambdaQueryWrapper<CommunityAdMaterialEntity> wrapper = new LambdaQueryWrapper<CommunityAdMaterialEntity>()
                .eq(CommunityAdMaterialEntity::getCampusId, campusId)
                .eq(StringUtils.hasText(query.reviewStatus()), CommunityAdMaterialEntity::getReviewStatus,
                        normalize(query.reviewStatus()))
                .eq(StringUtils.hasText(query.materialType()), CommunityAdMaterialEntity::getMaterialType,
                        normalize(query.materialType()))
                .and(StringUtils.hasText(query.keyword()), w -> w
                        .like(CommunityAdMaterialEntity::getMaterialName, query.keyword().trim())
                        .or().like(CommunityAdMaterialEntity::getTitle, query.keyword().trim()))
                .orderByDesc(CommunityAdMaterialEntity::getUpdatedAt);
        Page<CommunityAdMaterialEntity> page = materialMapper.selectPage(new Page<>(
                query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
        Map<Long, Long> counts = adCountsByMaterial(page.getRecords().stream()
                .map(CommunityAdMaterialEntity::getId).toList());
        List<AdminAdMaterialResponse> records = page.getRecords().stream()
                .map(material -> AdminAdMaterialResponse.from(material,
                        counts.getOrDefault(material.getId(), 0L)))
                .toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAdMaterialResponse createMaterial(AdminAdMaterialRequest request) {
        CommunityAdMaterialEntity entity = new CommunityAdMaterialEntity();
        entity.setCampusId(campusId());
        applyMaterial(entity, request);
        entity.setReviewStatus(CommunityAdMaterialEntity.REVIEW_PENDING);
        entity.setStatus("ENABLED");
        materialMapper.insert(entity);
        log.info("创建广告素材, materialId={}, type={}, operatorId={}", entity.getId(),
                entity.getMaterialType(), currentUserProvider.requireCurrentUser().userId());
        return AdminAdMaterialResponse.from(entity, 0L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAdMaterialResponse updateMaterial(Long materialId, AdminAdMaterialRequest request) {
        CommunityAdMaterialEntity entity = requireMaterial(materialId);
        if (!CommunityAdMaterialEntity.REVIEW_PENDING.equals(entity.getReviewStatus())
                && !CommunityAdMaterialEntity.REVIEW_REJECTED.equals(entity.getReviewStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID,
                    "仅待审核或已驳回素材可编辑，审核通过后如需修改请新建素材");
        }
        applyMaterial(entity, request);
        // 修改后重新回到待审核，避免审核结论与素材内容不一致
        entity.setReviewStatus(CommunityAdMaterialEntity.REVIEW_PENDING);
        entity.setReviewReason(null);
        entity.setReviewedBy(null);
        entity.setReviewedAt(null);
        materialMapper.updateById(entity);
        return AdminAdMaterialResponse.from(entity, countAdsByMaterial(materialId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAdMaterialResponse reviewMaterial(Long materialId, AdminAdMaterialReviewRequest request) {
        String action = normalize(request.action());
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "审核结论仅支持 APPROVE/REJECT");
        }
        boolean approve = "APPROVE".equals(action);
        if (!approve && !StringUtils.hasText(request.reason())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "驳回必须填写原因");
        }
        CommunityAdMaterialEntity entity = requireMaterial(materialId);
        if (!CommunityAdMaterialEntity.REVIEW_PENDING.equals(entity.getReviewStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "该素材不在待审核状态");
        }
        Long reviewer = currentUserProvider.requireCurrentUser().userId();
        CommunityAdMaterialEntity update = new CommunityAdMaterialEntity();
        update.setId(entity.getId());
        update.setReviewStatus(approve ? CommunityAdMaterialEntity.REVIEW_APPROVED
                : CommunityAdMaterialEntity.REVIEW_REJECTED);
        update.setReviewReason(approve ? null : request.reason().trim());
        update.setReviewedBy(reviewer);
        update.setReviewedAt(LocalDateTime.now(ZONE));
        update.setVersion(entity.getVersion());
        materialMapper.updateById(update);
        CommunityAdMaterialEntity saved = materialMapper.selectById(materialId);
        log.info("广告素材审核, materialId={}, status={}, reviewer={}", materialId,
                saved.getReviewStatus(), reviewer);
        return AdminAdMaterialResponse.from(saved, countAdsByMaterial(materialId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAdMaterialResponse updateMaterialStatus(Long materialId, String status) {
        String target = normalize(status);
        if (target == null || !MATERIAL_CONFIG_STATUSES.contains(target)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "素材状态仅支持 ENABLED/DISABLED");
        }
        CommunityAdMaterialEntity entity = requireMaterial(materialId);
        CommunityAdMaterialEntity update = new CommunityAdMaterialEntity();
        update.setId(entity.getId());
        update.setStatus(target);
        update.setVersion(entity.getVersion());
        materialMapper.updateById(update);
        entity.setStatus(target);
        return AdminAdMaterialResponse.from(entity, countAdsByMaterial(materialId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMaterial(Long materialId) {
        CommunityAdMaterialEntity entity = requireMaterial(materialId);
        long linked = countAdsByMaterial(entity.getId());
        if (linked > 0) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID,
                    "该素材已被 " + linked + " 条直投广告引用，请先解除关联再删除");
        }
        materialMapper.deleteById(entity.getId());
        log.info("删除广告素材, materialId={}, operatorId={}", materialId,
                currentUserProvider.requireCurrentUser().userId());
    }

    private void applyMaterial(CommunityAdMaterialEntity entity, AdminAdMaterialRequest request) {
        String type = normalize(request.materialType());
        if (type == null || !MATERIAL_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "素材类型仅支持 IMAGE/VIDEO/TEXT");
        }
        String fileUrl = trimToNull(request.fileUrl());
        String coverUrl = trimToNull(request.coverUrl());
        String title = trimToNull(request.title());
        String description = trimToNull(request.description());
        if ("TEXT".equals(type)) {
            if (!StringUtils.hasText(title) && !StringUtils.hasText(description)) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文本素材需填写标题或正文");
            }
        } else if (!StringUtils.hasText(fileUrl)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "图片/视频素材必须填写文件地址");
        }
        if (fileUrl != null) {
            validateHttpUrl(fileUrl, "素材文件地址");
        }
        if (coverUrl != null) {
            validateHttpUrl(coverUrl, "封面地址");
        }
        if (request.width() != null && request.width() < 0 || request.height() != null && request.height() < 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "素材宽高不能为负数");
        }
        if (request.sizeBytes() != null && request.sizeBytes() < 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "素材大小不能为负数");
        }
        entity.setMaterialName(request.materialName().trim());
        entity.setMaterialType(type);
        entity.setFileUrl(fileUrl);
        entity.setCoverUrl(coverUrl);
        entity.setTitle(title);
        entity.setDescription(description);
        entity.setWidth(request.width());
        entity.setHeight(request.height());
        entity.setSizeBytes(request.sizeBytes());
    }

    private CommunityAdMaterialEntity requireMaterial(Long materialId) {
        CommunityAdMaterialEntity entity = materialMapper.selectById(materialId);
        if (entity == null || !campusId().equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "广告素材不存在或不属于当前校区");
        }
        if (entity.getReviewStatus() == null || !MATERIAL_REVIEW_STATUSES.contains(entity.getReviewStatus())) {
            entity.setReviewStatus(CommunityAdMaterialEntity.REVIEW_PENDING);
        }
        return entity;
    }

    private long countAdsByMaterial(Long materialId) {
        Long count = adMapper.selectCount(new LambdaQueryWrapper<CommunityDirectAdEntity>()
                .eq(CommunityDirectAdEntity::getMaterialId, materialId));
        return count == null ? 0 : count;
    }

    private Map<Long, Long> adCountsByMaterial(List<Long> materialIds) {
        List<Long> ids = materialIds.stream().filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (CommunityDirectAdEntity ad : adMapper.selectList(new LambdaQueryWrapper<CommunityDirectAdEntity>()
                .in(CommunityDirectAdEntity::getMaterialId, ids)
                .select(CommunityDirectAdEntity::getMaterialId))) {
            counts.merge(ad.getMaterialId(), 1L, Long::sum);
        }
        return counts;
    }

    // ------------------------------------------------------------------
    // 公共
    // ------------------------------------------------------------------

    private Long requireAdvertiserId(Long advertiserId) {
        if (advertiserId == null) {
            return null;
        }
        CommunityAdvertiserEntity advertiser = advertiserMapper.selectById(advertiserId);
        if (advertiser == null || !campusId().equals(advertiser.getCampusId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "关联广告主不存在或不属于当前校区");
        }
        return advertiserId;
    }

    private String advertiserName(Long advertiserId) {
        if (advertiserId == null) {
            return null;
        }
        CommunityAdvertiserEntity advertiser = advertiserMapper.selectById(advertiserId);
        return advertiser == null ? null : advertiser.getName();
    }

    private Map<Long, String> advertiserNames(List<Long> advertiserIds) {
        List<Long> distinct = advertiserIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return new HashMap<>();
        }
        return advertiserMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(CommunityAdvertiserEntity::getId, CommunityAdvertiserEntity::getName,
                        (left, right) -> left, HashMap::new));
    }

    private void validateHttpUrl(String url, String label) {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, label + "必须为 http/https 地址");
        }
    }

    private Long campusId() {
        return campusContextService.currentCampusId();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
