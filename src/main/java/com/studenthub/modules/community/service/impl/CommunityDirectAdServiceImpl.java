package com.studenthub.modules.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.community.controller.dto.AdminDirectAdPageQuery;
import com.studenthub.modules.community.controller.dto.AdminDirectAdRequest;
import com.studenthub.modules.community.controller.dto.AdminDirectAdResponse;
import com.studenthub.modules.community.controller.dto.AdminDirectAdReviewRequest;
import com.studenthub.modules.community.controller.dto.AdminDirectAdStatsResponse;
import com.studenthub.modules.community.entity.CommunityAdEventEntity;
import com.studenthub.modules.community.entity.CommunityAdMaterialEntity;
import com.studenthub.modules.community.entity.CommunityAdPlanEntity;
import com.studenthub.modules.community.entity.CommunityAdvertiserEntity;
import com.studenthub.modules.community.entity.CommunityDirectAdEntity;
import com.studenthub.modules.community.entity.CommunitySectionEntity;
import com.studenthub.modules.community.mapper.CommunityAdEventMapper;
import com.studenthub.modules.community.mapper.CommunityAdMaterialMapper;
import com.studenthub.modules.community.mapper.CommunityAdPlanMapper;
import com.studenthub.modules.community.mapper.CommunityAdvertiserMapper;
import com.studenthub.modules.community.mapper.CommunityDirectAdMapper;
import com.studenthub.modules.community.mapper.CommunitySectionMapper;
import com.studenthub.modules.community.service.CommunityDirectAdService;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端官方直投广告实现：CRUD、上下架与数据统计。
 * 统计口径：可测落地 = 收到 LANDING 的点击（仅 IN_APP_WEBVIEW/APP_PAGE）；
 * 跳出 = 从落地加载完成起 N 秒内退出；外部浏览器/DEEPLINK 不可测，跳出率返回 null。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityDirectAdServiceImpl implements CommunityDirectAdService {

    private static final Set<String> LANDING_TYPES = Set.of(
            "IN_APP_WEBVIEW", "APP_PAGE", "EXTERNAL_BROWSER", "DEEPLINK");
    private static final Set<String> MEASURABLE_LANDING_TYPES = Set.of("IN_APP_WEBVIEW", "APP_PAGE");
    private static final Set<String> SLOTS = Set.of("FEED", "BANNER", "SPLASH", "SERVICE_HALL");
    private static final Set<String> CONFIG_STATUSES = Set.of("DRAFT", "ENABLED", "DISABLED");
    private static final Set<String> REVIEW_STATUSES = Set.of("DRAFT", "PENDING", "APPROVED", "REJECTED");
    private static final Set<String> EDITABLE_REVIEW_STATUSES = Set.of("DRAFT", "REJECTED");
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_BOUNCE_SECONDS = 5;

    private final CommunityDirectAdMapper adMapper;
    private final CommunityAdEventMapper eventMapper;
    private final CommunityAdvertiserMapper advertiserMapper;
    private final CommunityAdPlanMapper planMapper;
    private final CommunityAdMaterialMapper materialMapper;
    private final CommunitySectionMapper sectionMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AdminCampusContextService campusContextService;

    @Override
    public PageResult<AdminDirectAdResponse> page(AdminDirectAdPageQuery query) {
        int current = Math.max(1, query.pageNumOrDefault());
        int size = Math.max(1, Math.min(query.pageSizeOrDefault(), MAX_PAGE_SIZE));
        String reviewStatus = normalizeEnum(query.reviewStatus());
        if (reviewStatus != null && !REVIEW_STATUSES.contains(reviewStatus)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "审核状态仅支持 DRAFT/PENDING/APPROVED/REJECTED");
        }
        LambdaQueryWrapper<CommunityDirectAdEntity> wrapper = new LambdaQueryWrapper<CommunityDirectAdEntity>()
                .eq(CommunityDirectAdEntity::getCampusId, campusId())
                .eq(StringUtils.hasText(query.status()), CommunityDirectAdEntity::getStatus,
                        normalizeEnum(query.status()))
                .eq(reviewStatus != null, CommunityDirectAdEntity::getReviewStatus, reviewStatus)
                .eq(query.advertiserId() != null, CommunityDirectAdEntity::getAdvertiserId, query.advertiserId())
                .eq(StringUtils.hasText(query.slot()), CommunityDirectAdEntity::getSlot,
                        normalizeEnum(query.slot()))
                .and(StringUtils.hasText(query.keyword()), w -> {
                    String keyword = query.keyword().trim();
                    w.like(CommunityDirectAdEntity::getTitle, keyword)
                            .or().like(CommunityDirectAdEntity::getBrandName, keyword);
                })
                .orderByDesc(CommunityDirectAdEntity::getPriority)
                .orderByDesc(CommunityDirectAdEntity::getId);
        Page<CommunityDirectAdEntity> page = adMapper.selectPage(new Page<>(current, size), wrapper);
        LocalDateTime now = LocalDateTime.now();
        Map<Long, String> advertiserNames = advertiserNames(page.getRecords().stream()
                .map(CommunityDirectAdEntity::getAdvertiserId).toList());
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(),
                page.getRecords().stream()
                        .map(entity -> response(entity, now, advertiserNames.get(entity.getAdvertiserId())))
                        .toList());
    }

    @Override
    public AdminDirectAdResponse detail(Long adId) {
        CommunityDirectAdEntity entity = requireAd(adId);
        return response(entity, LocalDateTime.now(), advertiserName(entity.getAdvertiserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminDirectAdResponse create(AdminDirectAdRequest request) {
        CommunityDirectAdEntity entity = new CommunityDirectAdEntity();
        entity.setCampusId(campusId());
        applyRequest(entity, request, true);
        // 创建一律从草稿开始：审核通过后由 /status 接口上架
        entity.setStatus("DRAFT");
        entity.setReviewStatus("DRAFT");
        adMapper.insert(entity);
        log.info("创建官方直投广告, adId={}, title={}, advertiserId={}, operatorId={}",
                entity.getId(), entity.getTitle(), entity.getAdvertiserId(),
                currentUserProvider.currentUserIdOrSystem());
        return response(adMapper.selectById(entity.getId()), LocalDateTime.now(),
                advertiserName(entity.getAdvertiserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminDirectAdResponse update(Long adId, AdminDirectAdRequest request) {
        CommunityDirectAdEntity entity = requireAd(adId);
        if (!EDITABLE_REVIEW_STATUSES.contains(entity.getReviewStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID,
                    "仅草稿或已驳回的广告可编辑，审核中/已通过的广告请先撤回或等待审核结果");
        }
        applyRequest(entity, request, false);
        adMapper.updateById(entity);
        log.info("更新官方直投广告, adId={}, title={}, reviewStatus={}, operatorId={}",
                adId, entity.getTitle(), entity.getReviewStatus(),
                currentUserProvider.currentUserIdOrSystem());
        return response(adMapper.selectById(adId), LocalDateTime.now(),
                advertiserName(entity.getAdvertiserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminDirectAdResponse updateStatus(Long adId, String status) {
        String target = normalizeEnum(status);
        if (target == null || !CONFIG_STATUSES.contains(target)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "广告状态仅支持 ENABLED/DISABLED/DRAFT");
        }
        CommunityDirectAdEntity entity = requireAd(adId);
        if ("ENABLED".equals(target) && !"APPROVED".equals(entity.getReviewStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID,
                    "广告审核通过后才能上架，请先提交审核并等待通过");
        }
        if ("ENABLED".equals(target) && !entity.getEndAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "广告已过结束时间，无法上架");
        }
        CommunityDirectAdEntity update = new CommunityDirectAdEntity();
        update.setId(entity.getId());
        update.setStatus(target);
        update.setVersion(entity.getVersion());
        adMapper.updateById(update);
        log.info("官方直投广告状态变更, adId={}, status={}, operatorId={}",
                adId, target, currentUserProvider.currentUserIdOrSystem());
        return response(adMapper.selectById(adId), LocalDateTime.now(),
                advertiserName(entity.getAdvertiserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminDirectAdResponse submit(Long adId) {
        CommunityDirectAdEntity entity = requireAd(adId);
        if ("PENDING".equals(entity.getReviewStatus())) {
            // 幂等：重复提交不改变提交时间
            return response(entity, LocalDateTime.now(), advertiserName(entity.getAdvertiserId()));
        }
        if (!EDITABLE_REVIEW_STATUSES.contains(entity.getReviewStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID,
                    "广告已审核通过，无需重复提交");
        }
        LocalDateTime now = LocalDateTime.now();
        adMapper.update(null, new LambdaUpdateWrapper<CommunityDirectAdEntity>()
                .eq(CommunityDirectAdEntity::getId, entity.getId())
                .set(CommunityDirectAdEntity::getReviewStatus, "PENDING")
                .set(CommunityDirectAdEntity::getSubmittedAt, now)
                .set(CommunityDirectAdEntity::getReviewReason, null)
                .set(CommunityDirectAdEntity::getReviewedBy, null)
                .set(CommunityDirectAdEntity::getReviewedAt, null));
        log.info("官方直投广告提交审核, adId={}, operatorId={}", adId,
                currentUserProvider.currentUserIdOrSystem());
        return response(adMapper.selectById(adId), now, advertiserName(entity.getAdvertiserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminDirectAdResponse review(Long adId, AdminDirectAdReviewRequest request) {
        String action = normalizeEnum(request.action());
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "审核动作仅支持 APPROVE/REJECT");
        }
        boolean approve = "APPROVE".equals(action);
        String reason = trimToNull(request.reason());
        if (!approve && reason == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "驳回必须填写原因");
        }
        CommunityDirectAdEntity entity = requireAd(adId);
        if (!"PENDING".equals(entity.getReviewStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID, "仅待审核广告可执行审核");
        }
        LocalDateTime now = LocalDateTime.now();
        Long reviewerId = currentUserProvider.currentUserIdOrSystem();
        adMapper.update(null, new LambdaUpdateWrapper<CommunityDirectAdEntity>()
                .eq(CommunityDirectAdEntity::getId, entity.getId())
                .set(CommunityDirectAdEntity::getReviewStatus, approve ? "APPROVED" : "REJECTED")
                .set(CommunityDirectAdEntity::getReviewReason, approve ? null : reason)
                .set(CommunityDirectAdEntity::getReviewedBy, reviewerId)
                .set(CommunityDirectAdEntity::getReviewedAt, now));
        log.info("官方直投广告审核, adId={}, action={}, reviewerId={}", adId, action, reviewerId);
        return response(adMapper.selectById(adId), now, advertiserName(entity.getAdvertiserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long adId) {
        CommunityDirectAdEntity entity = requireAd(adId);
        adMapper.deleteById(entity.getId());
        log.info("删除官方直投广告, adId={}, operatorId={}", adId,
                currentUserProvider.currentUserIdOrSystem());
    }

    @Override
    public AdminDirectAdStatsResponse stats(Long adId, Integer bounceThresholdSeconds,
                                            LocalDateTime startAt, LocalDateTime endAt) {
        CommunityDirectAdEntity ad = requireAd(adId);
        int threshold = bounceThresholdSeconds == null
                ? (ad.getBounceThresholdSeconds() == null ? DEFAULT_BOUNCE_SECONDS : ad.getBounceThresholdSeconds())
                : bounceThresholdSeconds;
        if (threshold < 1 || threshold > 60) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "跳出统计秒数 N 仅支持 1-60");
        }
        List<CommunityAdEventEntity> events = eventMapper.selectList(
                new LambdaQueryWrapper<CommunityAdEventEntity>()
                        .eq(CommunityAdEventEntity::getAdId, adId)
                        .ge(startAt != null, CommunityAdEventEntity::getCreatedAt, startAt)
                        .lt(endAt != null, CommunityAdEventEntity::getCreatedAt, endAt));
        long impressions = 0;
        List<CommunityAdEventEntity> clicks = new ArrayList<>();
        Map<String, CommunityAdEventEntity> landings = new HashMap<>();
        Map<String, CommunityAdEventEntity> dwells = new HashMap<>();
        for (CommunityAdEventEntity event : events) {
            switch (event.getEventType()) {
                case CommunityAdEventEntity.TYPE_IMPRESSION -> impressions++;
                case CommunityAdEventEntity.TYPE_CLICK -> clicks.add(event);
                case CommunityAdEventEntity.TYPE_LANDING -> {
                    if (event.getClickId() != null) {
                        landings.putIfAbsent(event.getClickId(), event);
                    }
                }
                case CommunityAdEventEntity.TYPE_DWELL -> {
                    if (event.getClickId() != null) {
                        dwells.putIfAbsent(event.getClickId(), event);
                    }
                }
                default -> { }
            }
        }

        Map<String, Long> clicksByType = new LinkedHashMap<>();
        Map<String, Set<String>> landingsByType = new HashMap<>();
        Map<String, List<Long>> dwellsByType = new HashMap<>();
        long unmeasurableClicks = 0;
        for (CommunityAdEventEntity click : clicks) {
            String type = resolveLandingType(click, ad);
            clicksByType.merge(type, 1L, Long::sum);
            if (!MEASURABLE_LANDING_TYPES.contains(type)) {
                unmeasurableClicks++;
            }
        }
        for (Map.Entry<String, CommunityAdEventEntity> entry : landings.entrySet()) {
            String type = resolveLandingType(entry.getValue(), ad);
            if (!MEASURABLE_LANDING_TYPES.contains(type)) {
                continue;
            }
            landingsByType.computeIfAbsent(type, key -> new HashSet<>()).add(entry.getKey());
        }
        for (Map.Entry<String, CommunityAdEventEntity> entry : dwells.entrySet()) {
            String type = resolveLandingType(entry.getValue(), ad);
            if (!MEASURABLE_LANDING_TYPES.contains(type)) {
                continue;
            }
            dwellsByType.computeIfAbsent(type, key -> new ArrayList<>())
                    .add(entry.getValue().getDwellMs() == null ? 0L : entry.getValue().getDwellMs());
        }

        long measurableLandings = landingsByType.values().stream().mapToLong(Set::size).sum();
        long bounced = countBounced(landings, dwells, threshold);
        Double bounceRate = measurableLandings == 0 ? null : round4((double) bounced / measurableLandings);
        Double avgDwellMs = measurableLandings == 0 ? null
                : round1((double) totalDwell(dwells) / measurableLandings);
        double ctr = impressions == 0 ? 0.0 : round4((double) clicks.size() / impressions);

        List<AdminDirectAdStatsResponse.LandingTypeStats> breakdown = new ArrayList<>();
        List<String> orderedTypes = new ArrayList<>();
        if (ad.getLandingType() != null) {
            orderedTypes.add(ad.getLandingType());
        }
        clicksByType.keySet().stream()
                .filter(type -> !orderedTypes.contains(type)).sorted().forEach(orderedTypes::add);
        for (String type : orderedTypes) {
            long typeClicks = clicksByType.getOrDefault(type, 0L);
            boolean measurable = MEASURABLE_LANDING_TYPES.contains(type);
            long typeLandings = measurable ? landingsByType.getOrDefault(type, Set.of()).size() : 0;
            long typeBounced = measurable ? typeLandings == 0 ? 0
                    : countBouncedForType(landingsByType.get(type), dwells, type, ad, threshold) : 0;
            Double typeBounceRate = measurable && typeLandings > 0
                    ? round4((double) typeBounced / typeLandings) : null;
            Double typeAvgDwell = measurable && typeLandings > 0
                    ? round1((double) dwellsByType.getOrDefault(type, List.of()).stream()
                    .mapToLong(Long::longValue).sum() / typeLandings) : null;
            breakdown.add(new AdminDirectAdStatsResponse.LandingTypeStats(type, typeClicks, measurable,
                    typeLandings, typeBounceRate, typeAvgDwell));
        }

        return new AdminDirectAdStatsResponse(ad.getId(), impressions, clicks.size(), ctr,
                measurableLandings, bounced, bounceRate, avgDwellMs, unmeasurableClicks,
                MEASURABLE_LANDING_TYPES.contains(ad.getLandingType()), threshold, breakdown);
    }

    private long countBounced(Map<String, CommunityAdEventEntity> landings,
                              Map<String, CommunityAdEventEntity> dwells, int thresholdSeconds) {
        long bounced = 0;
        long thresholdMs = thresholdSeconds * 1000L;
        for (String clickId : landings.keySet()) {
            CommunityAdEventEntity dwell = dwells.get(clickId);
            if (dwell != null && dwell.getDwellMs() != null && dwell.getDwellMs() < thresholdMs) {
                bounced++;
            }
        }
        return bounced;
    }

    private long countBouncedForType(Set<String> landingClickIds,
                                     Map<String, CommunityAdEventEntity> dwells,
                                     String type, CommunityDirectAdEntity ad, int thresholdSeconds) {
        if (landingClickIds == null || landingClickIds.isEmpty()) {
            return 0;
        }
        long bounced = 0;
        long thresholdMs = thresholdSeconds * 1000L;
        for (String clickId : landingClickIds) {
            CommunityAdEventEntity dwell = dwells.get(clickId);
            if (dwell == null || !type.equals(resolveLandingType(dwell, ad))) {
                continue;
            }
            if (dwell.getDwellMs() != null && dwell.getDwellMs() < thresholdMs) {
                bounced++;
            }
        }
        return bounced;
    }

    private long totalDwell(Map<String, CommunityAdEventEntity> dwells) {
        long total = 0;
        for (CommunityAdEventEntity dwell : dwells.values()) {
            total += dwell.getDwellMs() == null ? 0 : dwell.getDwellMs();
        }
        return total;
    }

    private String resolveLandingType(CommunityAdEventEntity event, CommunityDirectAdEntity ad) {
        return event.getLandingType() == null ? ad.getLandingType() : event.getLandingType();
    }

    private void applyRequest(CommunityDirectAdEntity entity, AdminDirectAdRequest request, boolean creating) {
        String landingType = normalizeEnum(request.landingType());
        if (landingType == null || !LANDING_TYPES.contains(landingType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "跳转类型仅支持 IN_APP_WEBVIEW/APP_PAGE/EXTERNAL_BROWSER/DEEPLINK");
        }
        String slot = normalizeEnum(request.slot());
        if (slot == null) {
            slot = "FEED";
        }
        if (!SLOTS.contains(slot)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "广告位仅支持 FEED/BANNER/SPLASH/SERVICE_HALL");
        }
        String status = creating ? "DRAFT" : normalizeEnum(request.status());
        if (status == null) {
            status = "DRAFT";
        }
        if (!CONFIG_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "广告状态仅支持 DRAFT/ENABLED/DISABLED");
        }
        if ("ENABLED".equals(status) && !"APPROVED".equals(entity.getReviewStatus())) {
            throw new BusinessException(ErrorCode.REQUEST_STATE_INVALID,
                    "广告审核通过后才能上架，请先提交审核并等待通过");
        }
        if (!request.endAt().isAfter(request.startAt())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "投放结束时间必须晚于开始时间");
        }
        if ("ENABLED".equals(status) && !request.endAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "投放结束时间必须晚于当前时间");
        }
        if (request.sectionId() != null) {
            CommunitySectionEntity section = sectionMapper.selectById(request.sectionId());
            if (section == null || !campusId().equals(section.getCampusId())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "投放分区不存在");
            }
        }
        int threshold = request.bounceThresholdSeconds() == null
                ? DEFAULT_BOUNCE_SECONDS : request.bounceThresholdSeconds();
        if (threshold < 1 || threshold > 60) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "跳出统计秒数 N 仅支持 1-60");
        }
        String targetUrl = request.targetUrl().trim();
        validateTargetUrl(targetUrl, landingType);
        String coverUrl = trimToNull(request.coverUrl());
        if (coverUrl != null) {
            validateHttpUrl(coverUrl, "封面图地址");
        }
        entity.setTitle(request.title().trim());
        entity.setBrandName(request.brandName().trim());
        entity.setDescription(trimToNull(request.description()));
        entity.setCoverUrl(coverUrl);
        entity.setTargetUrl(targetUrl);
        entity.setLandingType(landingType);
        entity.setSectionId(request.sectionId());
        entity.setSlot(slot);
        entity.setPriority(request.priority() == null ? 0 : request.priority());
        entity.setStartAt(request.startAt());
        entity.setEndAt(request.endAt());
        entity.setStatus(status);
        entity.setBounceThresholdSeconds(threshold);
        entity.setCtaText(trimToNull(request.ctaText()));
        entity.setSponsoredLabel(trimToNull(request.sponsoredLabel()) == null
                ? "推广" : request.sponsoredLabel().trim());
        entity.setAdvertiserId(requireAdvertiserId(request.advertiserId()));
        entity.setPlanId(requirePlanId(request.planId()));
        entity.setMaterialId(requireMaterialId(request.materialId()));
    }

    /** 广告主必须存在且与广告同校区；为空表示不关联广告主。 */
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
        Set<Long> distinct = advertiserIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinct.isEmpty()) {
            // 不用 Map.of()：其 get(null) 会抛 NPE，而广告可以不关联广告主
            return new HashMap<>();
        }
        return advertiserMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(CommunityAdvertiserEntity::getId, CommunityAdvertiserEntity::getName,
                        (existing, ignored) -> existing));
    }

    private AdminDirectAdResponse response(CommunityDirectAdEntity entity, LocalDateTime now,
                                           String advertiserName) {
        return AdminDirectAdResponse.from(entity, now, advertiserName,
                planName(entity.getPlanId()), materialName(entity.getMaterialId()));
    }

    /** 计划必须同校区；为空表示不关联 */
    private Long requirePlanId(Long planId) {
        if (planId == null) {
            return null;
        }
        CommunityAdPlanEntity plan = planMapper.selectById(planId);
        if (plan == null || !campusId().equals(plan.getCampusId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "关联投放计划不存在或不属于当前校区");
        }
        return planId;
    }

    /** 素材必须同校区且审核通过；为空表示不关联（引用未过审素材会让投放链路的审核失去意义） */
    private Long requireMaterialId(Long materialId) {
        if (materialId == null) {
            return null;
        }
        CommunityAdMaterialEntity material = materialMapper.selectById(materialId);
        if (material == null || !campusId().equals(material.getCampusId())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "关联素材不存在或不属于当前校区");
        }
        if (!CommunityAdMaterialEntity.REVIEW_APPROVED.equals(material.getReviewStatus())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "关联素材尚未审核通过，不能用于投放");
        }
        return materialId;
    }

    private String planName(Long planId) {
        if (planId == null) {
            return null;
        }
        CommunityAdPlanEntity plan = planMapper.selectById(planId);
        return plan == null ? null : plan.getPlanName();
    }

    private String materialName(Long materialId) {
        if (materialId == null) {
            return null;
        }
        CommunityAdMaterialEntity material = materialMapper.selectById(materialId);
        return material == null ? null : material.getMaterialName();
    }

    /**
     * 广告落地地址安全校验：站内/外部浏览器只允许 http(s)，Deeplink 允许自定义 scheme
     * 但拒绝 javascript/data/file/intent 等可执行或本地文件协议，防止 WebView/系统打开恶意 URI。
     */
    private void validateTargetUrl(String targetUrl, String landingType) {
        if (!StringUtils.hasText(targetUrl)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "落地地址不能为空");
        }
        if ("DEEPLINK".equals(landingType)) {
            URI uri = parseUri(targetUrl, "落地地址");
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (scheme.isEmpty() || Set.of("javascript", "data", "file", "content", "intent", "vbscript")
                    .contains(scheme)) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Deeplink 协议不合法");
            }
            return;
        }
        validateHttpUrl(targetUrl, "落地地址");
    }

    private void validateHttpUrl(String value, String field) {
        URI uri = parseUri(value, field);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null
                || uri.getHost().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, field + "仅支持 http/https 地址");
        }
    }

    private URI parseUri(String value, String field) {
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null || uri.getScheme().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, field + "缺少协议");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, field + "格式不正确");
        }
    }

    private CommunityDirectAdEntity requireAd(Long adId) {
        CommunityDirectAdEntity entity = adMapper.selectById(adId);
        if (entity == null || !campusId().equals(entity.getCampusId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "广告不存在");
        }
        return entity;
    }

    private Long campusId() {
        return campusContextService.currentCampusId();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeEnum(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
