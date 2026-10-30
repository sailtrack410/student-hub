package com.studenthub.modules.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.PageResult;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.community.controller.dto.AdClickResponse;
import com.studenthub.modules.community.controller.dto.AdImpressionResponse;
import com.studenthub.modules.community.controller.dto.AdLandingExitResponse;
import com.studenthub.modules.community.controller.dto.AdLandingResponse;
import com.studenthub.modules.community.controller.dto.CommunityFeedItemResponse;
import com.studenthub.modules.community.controller.dto.CommunityPostResponse;
import com.studenthub.modules.community.controller.dto.FeedAdItemResponse;
import com.studenthub.modules.community.controller.dto.ReportAdClickRequest;
import com.studenthub.modules.community.controller.dto.ReportAdImpressionRequest;
import com.studenthub.modules.community.controller.dto.ReportAdLandingExitRequest;
import com.studenthub.modules.community.controller.dto.ReportAdLandingLoadedRequest;
import com.studenthub.modules.community.entity.CommunityAdEventEntity;
import com.studenthub.modules.community.entity.CommunityDirectAdEntity;
import com.studenthub.modules.community.mapper.CommunityAdEventMapper;
import com.studenthub.modules.community.mapper.CommunityDirectAdMapper;
import com.studenthub.modules.community.security.CommunityAdEventGuard;
import com.studenthub.modules.community.service.CommunityAdFeedService;
import com.studenthub.modules.community.service.CommunityService;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.systemconfig.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * App 端信息流广告实现：
 * 1) 自然内容分页后按配置间隔插入官方直投广告（优先级轮转、去重、频控）；
 * 2) 曝光/点击/落地/停留事件链路，click_id 串起全链路并做幂等；
 * 3) 外部浏览器/DEEPLINK 只记录点击，不接受落地/停留上报。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityAdFeedServiceImpl implements CommunityAdFeedService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> MEASURABLE_LANDING_TYPES = Set.of("IN_APP_WEBVIEW", "APP_PAGE");
    private static final String FEED_SLOT = "FEED";
    private static final Set<String> APP_SLOTS = Set.of("BANNER", "SERVICE_HALL", "SPLASH");
    private static final String REVIEW_APPROVED = "APPROVED";
    private static final int MAX_SLOT_ADS = 3;
    private static final String KEY_FEED_INTERVAL = "community.feed_ad_interval";
    private static final String KEY_DAILY_CAP = "community.ad_daily_impression_cap";
    private static final String KEY_USER_AD_CAP = "community.ad_user_ad_daily_cap";
    private static final String KEY_SESSION_CAP = "community.ad_session_impression_cap";
    private static final int DEFAULT_FEED_INTERVAL = 8;
    private static final int DEFAULT_DAILY_CAP = 20;
    private static final int DEFAULT_USER_AD_CAP = 3;
    private static final int DEFAULT_SESSION_CAP = 5;
    private static final int DEFAULT_BOUNCE_SECONDS = 5;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_ADS_PER_PAGE = 3;
    private static final long MAX_DWELL_MS = 24L * 60 * 60 * 1000;
    private static final long MAX_ATTRIBUTION_AGE_MINUTES = 30;

    private final CommunityDirectAdMapper adMapper;
    private final CommunityAdEventMapper eventMapper;
    private final CommunityService communityService;
    private final SystemConfigService systemConfigService;
    private final CommunityAdEventGuard eventGuard;

    @Override
    public PageResult<CommunityFeedItemResponse> feed(AuthenticatedUser user, Long sectionId, String sort,
                                                      long pageNum, long pageSize, String sessionId) {
        long current = Math.max(1, pageNum);
        int size = (int) Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        PageResult<CommunityPostResponse> naturalPage = communityService.pagePosts(
                user, sectionId, null, current, size, sort);
        List<CommunityPostResponse> posts = naturalPage.getRecords();
        if (posts.isEmpty()) {
            return PageResult.of(current, size, naturalPage.getTotal(), List.of());
        }
        int interval = configInt(KEY_FEED_INTERVAL, user.campusId(), DEFAULT_FEED_INTERVAL, 1, 50);
        LocalDateTime now = LocalDateTime.now();
        List<CommunityDirectAdEntity> candidates = loadCandidates(user, sectionId, now);
        candidates = applyFrequencyControl(user, candidates, sessionId, now);
        List<FeedAdItemResponse> pageAds = selectAdsForPage(candidates, current, size, interval);

        List<CommunityFeedItemResponse> items = new ArrayList<>(posts.size() + pageAds.size());
        int adIndex = 0;
        for (int i = 0; i < posts.size(); i++) {
            items.add(CommunityFeedItemResponse.ofPost(posts.get(i)));
            long globalNo = (current - 1) * size + i + 1;
            if (globalNo % interval == 0 && adIndex < pageAds.size()) {
                items.add(CommunityFeedItemResponse.ofAd(pageAds.get(adIndex++)));
            }
        }
        return PageResult.of(current, size, naturalPage.getTotal(), items);
    }

    @Override
    public List<FeedAdItemResponse> slotAds(AuthenticatedUser user, String slot) {
        String normalizedSlot = StringUtils.hasText(slot) ? slot.trim().toUpperCase() : null;
        if (normalizedSlot == null || !APP_SLOTS.contains(normalizedSlot)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "广告位仅支持 BANNER/SERVICE_HALL/SPLASH");
        }
        if (user.campusId() == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<CommunityDirectAdEntity> ads = adMapper.selectList(
                new LambdaQueryWrapper<CommunityDirectAdEntity>()
                        .in(CommunityDirectAdEntity::getCampusId, List.of(user.campusId(), 0L))
                        .eq(CommunityDirectAdEntity::getSlot, normalizedSlot)
                        .eq(CommunityDirectAdEntity::getStatus, "ENABLED")
                        .eq(CommunityDirectAdEntity::getReviewStatus, REVIEW_APPROVED)
                        .le(CommunityDirectAdEntity::getStartAt, now)
                        .gt(CommunityDirectAdEntity::getEndAt, now)
                        .orderByDesc(CommunityDirectAdEntity::getPriority)
                        .orderByAsc(CommunityDirectAdEntity::getStartAt)
                        .orderByAsc(CommunityDirectAdEntity::getId)
                        .last("LIMIT " + MAX_SLOT_ADS));
        return ads.stream().map(this::toFeedItem).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdImpressionResponse reportImpression(AuthenticatedUser user, Long adId,
                                                 ReportAdImpressionRequest request) {
        CommunityDirectAdEntity ad = requireAd(user, adId);
        String clickId = StringUtils.hasText(request.clickId()) ? request.clickId().trim() : newClickId();
        CommunityAdEventEntity existing = findByClickId(clickId, CommunityAdEventEntity.TYPE_IMPRESSION);
        if (existing != null) {
            if (!ad.getId().equals(existing.getAdId())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "clickId 已用于其它广告");
            }
            return new AdImpressionResponse(existing.getId(), clickId);
        }
        eventGuard.checkImpression(user);
        CommunityAdEventEntity event = newEvent(ad, user, clickId, request.sessionId(),
                CommunityAdEventEntity.TYPE_IMPRESSION);
        eventMapper.insert(event);
        return new AdImpressionResponse(event.getId(), clickId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdClickResponse reportClick(AuthenticatedUser user, Long adId, ReportAdClickRequest request) {
        CommunityDirectAdEntity ad = requireAd(user, adId);
        String clickId = StringUtils.hasText(request.clickId()) ? request.clickId().trim() : newClickId();
        CommunityAdEventEntity existing = findByClickId(clickId, CommunityAdEventEntity.TYPE_CLICK);
        if (existing != null) {
            if (!ad.getId().equals(existing.getAdId())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "clickId 已用于其它广告");
            }
            return clickResponse(ad, clickId);
        }
        eventGuard.checkClick(user);
        if (StringUtils.hasText(request.clickId()) && eventGuard.clickAttributionRequired()) {
            requireAttribution(user, ad, clickId, request.sessionId());
        }
        CommunityAdEventEntity event = newEvent(ad, user, clickId, request.sessionId(),
                CommunityAdEventEntity.TYPE_CLICK);
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException exception) {
            // 并发重试：唯一键兜底，按幂等成功返回
            log.debug("广告点击重复上报, adId={}, clickId={}", adId, clickId);
        }
        return clickResponse(ad, clickId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdLandingResponse reportLandingLoaded(AuthenticatedUser user, ReportAdLandingLoadedRequest request) {
        String clickId = request.clickId().trim();
        CommunityAdEventEntity click = requireClick(user, clickId);
        CommunityDirectAdEntity ad = requireAd(user, click.getAdId());
        eventGuard.checkLanding(user);
        String landingType = click.getLandingType() == null ? ad.getLandingType() : click.getLandingType();
        if (!MEASURABLE_LANDING_TYPES.contains(landingType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "该落地类型不可测量（外部浏览器/Deeplink），仅支持记录点击");
        }
        CommunityAdEventEntity existing = findByClickId(clickId, CommunityAdEventEntity.TYPE_LANDING);
        if (existing != null) {
            return new AdLandingResponse(existing.getId(), clickId, true);
        }
        CommunityAdEventEntity event = newEvent(ad, user, clickId, request.sessionId(),
                CommunityAdEventEntity.TYPE_LANDING);
        event.setLandingType(landingType);
        eventMapper.insert(event);
        return new AdLandingResponse(event.getId(), clickId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdLandingExitResponse reportLandingExit(AuthenticatedUser user, ReportAdLandingExitRequest request) {
        String clickId = request.clickId().trim();
        CommunityAdEventEntity click = requireClick(user, clickId);
        CommunityDirectAdEntity ad = requireAd(user, click.getAdId());
        String landingType = click.getLandingType() == null ? ad.getLandingType() : click.getLandingType();
        if (!MEASURABLE_LANDING_TYPES.contains(landingType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "该落地类型不可测量（外部浏览器/Deeplink），仅支持记录点击");
        }
        if (findByClickId(clickId, CommunityAdEventEntity.TYPE_LANDING) == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "请先上报 landing/loaded 再上报停留/跳出");
        }
        long dwellMs = request.dwellMs();
        if (dwellMs < 0 || dwellMs > MAX_DWELL_MS) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "停留毫秒数不合法");
        }
        int thresholdSeconds = ad.getBounceThresholdSeconds() == null
                ? DEFAULT_BOUNCE_SECONDS : ad.getBounceThresholdSeconds();
        boolean bounced = dwellMs < thresholdSeconds * 1000L;
        CommunityAdEventEntity existing = findByClickId(clickId, CommunityAdEventEntity.TYPE_DWELL);
        if (existing != null) {
            return new AdLandingExitResponse(clickId, existing.getDwellMs(),
                    Boolean.TRUE.equals(existing.getBounced()), thresholdSeconds);
        }
        CommunityAdEventEntity event = newEvent(ad, user, clickId, request.sessionId(),
                CommunityAdEventEntity.TYPE_DWELL);
        event.setLandingType(landingType);
        event.setDwellMs(dwellMs);
        event.setBounced(bounced);
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException exception) {
            CommunityAdEventEntity duplicate = findByClickId(clickId, CommunityAdEventEntity.TYPE_DWELL);
            if (duplicate != null) {
                return new AdLandingExitResponse(clickId, duplicate.getDwellMs(),
                        Boolean.TRUE.equals(duplicate.getBounced()), thresholdSeconds);
            }
        }
        return new AdLandingExitResponse(clickId, dwellMs, bounced, thresholdSeconds);
    }

    private List<CommunityDirectAdEntity> loadCandidates(AuthenticatedUser user, Long sectionId,
                                                         LocalDateTime now) {
        LambdaQueryWrapper<CommunityDirectAdEntity> wrapper = new LambdaQueryWrapper<CommunityDirectAdEntity>()
                .in(CommunityDirectAdEntity::getCampusId, List.of(user.campusId(), 0L))
                .eq(CommunityDirectAdEntity::getSlot, FEED_SLOT)
                .eq(CommunityDirectAdEntity::getStatus, "ENABLED")
                .eq(CommunityDirectAdEntity::getReviewStatus, REVIEW_APPROVED)
                .le(CommunityDirectAdEntity::getStartAt, now)
                .gt(CommunityDirectAdEntity::getEndAt, now)
                .orderByDesc(CommunityDirectAdEntity::getPriority)
                .orderByAsc(CommunityDirectAdEntity::getStartAt)
                .orderByAsc(CommunityDirectAdEntity::getId);
        if (sectionId != null) {
            wrapper.and(w -> w.isNull(CommunityDirectAdEntity::getSectionId)
                    .or().eq(CommunityDirectAdEntity::getSectionId, sectionId));
        }
        return adMapper.selectList(wrapper);
    }

    /** 频控：单用户单日总曝光、单用户单广告单日曝光、单会话曝光上限（按 IMPRESSION 事件计数）。 */
    private List<CommunityDirectAdEntity> applyFrequencyControl(AuthenticatedUser user,
                                                                List<CommunityDirectAdEntity> candidates,
                                                                String sessionId, LocalDateTime now) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        LocalDateTime today = LocalDate.now(ZONE).atStartOfDay();
        int dailyCap = configInt(KEY_DAILY_CAP, user.campusId(), DEFAULT_DAILY_CAP, 1, 1000);
        Long totalToday = eventMapper.selectCount(new LambdaQueryWrapper<CommunityAdEventEntity>()
                .eq(CommunityAdEventEntity::getUserId, user.userId())
                .eq(CommunityAdEventEntity::getEventType, CommunityAdEventEntity.TYPE_IMPRESSION)
                .ge(CommunityAdEventEntity::getCreatedAt, today));
        if (totalToday != null && totalToday >= dailyCap) {
            return List.of();
        }
        if (StringUtils.hasText(sessionId)) {
            int sessionCap = configInt(KEY_SESSION_CAP, user.campusId(), DEFAULT_SESSION_CAP, 1, 1000);
            Long sessionToday = eventMapper.selectCount(new LambdaQueryWrapper<CommunityAdEventEntity>()
                    .eq(CommunityAdEventEntity::getUserId, user.userId())
                    .eq(CommunityAdEventEntity::getSessionId, sessionId.trim())
                    .eq(CommunityAdEventEntity::getEventType, CommunityAdEventEntity.TYPE_IMPRESSION)
                    .ge(CommunityAdEventEntity::getCreatedAt, today));
            if (sessionToday != null && sessionToday >= sessionCap) {
                return List.of();
            }
        }
        int perAdCap = configInt(KEY_USER_AD_CAP, user.campusId(), DEFAULT_USER_AD_CAP, 1, 1000);
        Map<Long, Long> counts = new HashMap<>();
        eventMapper.selectList(new LambdaQueryWrapper<CommunityAdEventEntity>()
                        .eq(CommunityAdEventEntity::getUserId, user.userId())
                        .eq(CommunityAdEventEntity::getEventType, CommunityAdEventEntity.TYPE_IMPRESSION)
                        .ge(CommunityAdEventEntity::getCreatedAt, today)
                        .in(CommunityAdEventEntity::getAdId,
                                candidates.stream().map(CommunityDirectAdEntity::getId).toList()))
                .forEach(event -> counts.merge(event.getAdId(), 1L, Long::sum));
        return candidates.stream()
                .filter(ad -> counts.getOrDefault(ad.getId(), 0L) < perAdCap)
                .toList();
    }

    /** 按广告间隔计算本页广告位，按页面轮转候选广告，保证单页不重复。 */
    private List<FeedAdItemResponse> selectAdsForPage(List<CommunityDirectAdEntity> candidates,
                                                      long pageNum, int pageSize, int interval) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        int slots = 0;
        for (int i = 0; i < pageSize; i++) {
            long globalNo = (pageNum - 1) * pageSize + i + 1;
            if (globalNo % interval == 0) {
                slots++;
            }
        }
        slots = Math.min(slots, MAX_ADS_PER_PAGE);
        if (slots == 0) {
            return List.of();
        }
        int offset = (int) ((pageNum - 1) * slots % candidates.size());
        Set<Integer> picked = new LinkedHashSet<>();
        int index = offset;
        for (int i = 0; i < candidates.size() && picked.size() < slots; i++) {
            picked.add(index % candidates.size());
            index++;
        }
        List<FeedAdItemResponse> ads = new ArrayList<>(picked.size());
        for (Integer candidateIndex : picked) {
            ads.add(toFeedItem(candidates.get(candidateIndex)));
        }
        return ads;
    }

    private FeedAdItemResponse toFeedItem(CommunityDirectAdEntity ad) {
        boolean measurable = MEASURABLE_LANDING_TYPES.contains(ad.getLandingType());
        return new FeedAdItemResponse(ad.getId(), newClickId(), ad.getTitle(), ad.getBrandName(),
                ad.getDescription(), ad.getCoverUrl(), ad.getTargetUrl(), ad.getLandingType(),
                ad.getSectionId(), ad.getPriority(), measurable,
                ad.getSponsoredLabel() == null ? "推广" : ad.getSponsoredLabel(), ad.getCtaText());
    }

    private AdClickResponse clickResponse(CommunityDirectAdEntity ad, String clickId) {
        boolean measurable = MEASURABLE_LANDING_TYPES.contains(ad.getLandingType());
        return new AdClickResponse(clickId, ad.getId(), ad.getTargetUrl(), ad.getLandingType(), measurable);
    }

    private CommunityDirectAdEntity requireAd(AuthenticatedUser user, Long adId) {
        CommunityDirectAdEntity ad = adMapper.selectById(adId);
        if (ad == null || user.campusId() == null
                || !(user.campusId().equals(ad.getCampusId()) || Long.valueOf(0L).equals(ad.getCampusId()))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "广告不存在");
        }
        return ad;
    }

    private CommunityAdEventEntity requireClick(AuthenticatedUser user, String clickId) {
        CommunityAdEventEntity click = findByClickId(clickId, CommunityAdEventEntity.TYPE_CLICK);
        if (click == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "点击记录不存在，请先上报点击");
        }
        if (click.getUserId() != null && !click.getUserId().equals(user.userId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "点击记录不存在，请先上报点击");
        }
        return click;
    }

    private CommunityAdEventEntity findByClickId(String clickId, String eventType) {
        return eventMapper.selectOne(new LambdaQueryWrapper<CommunityAdEventEntity>()
                .eq(CommunityAdEventEntity::getClickId, clickId)
                .eq(CommunityAdEventEntity::getEventType, eventType)
                .last("LIMIT 1"));
    }

    /** 点击必须能追溯到同一用户、同一广告、30 分钟内的曝光；避免直接伪造点击。 */
    private void requireAttribution(AuthenticatedUser user, CommunityDirectAdEntity ad,
                                    String clickId, String sessionId) {
        CommunityAdEventEntity impression = findByClickId(clickId, CommunityAdEventEntity.TYPE_IMPRESSION);
        if (impression == null) {
            throw new BusinessException(ErrorCode.AD_ATTRIBUTION_INVALID, "点击缺少同广告的有效曝光");
        }
        if (!ad.getId().equals(impression.getAdId())) {
            throw new BusinessException(ErrorCode.AD_ATTRIBUTION_INVALID, "点击与曝光广告不一致");
        }
        if (!user.userId().equals(impression.getUserId())) {
            throw new BusinessException(ErrorCode.AD_ATTRIBUTION_INVALID, "点击与曝光用户不一致");
        }
        LocalDateTime createdAt = impression.getCreatedAt();
        if (createdAt == null || createdAt.isBefore(LocalDateTime.now().minusMinutes(MAX_ATTRIBUTION_AGE_MINUTES))) {
            throw new BusinessException(ErrorCode.AD_ATTRIBUTION_INVALID, "曝光已过期，请重新从信息流进入");
        }
        if (StringUtils.hasText(impression.getSessionId()) && StringUtils.hasText(sessionId)
                && !impression.getSessionId().equals(sessionId.trim())) {
            throw new BusinessException(ErrorCode.AD_ATTRIBUTION_INVALID, "点击与曝光会话不一致");
        }
    }

    private CommunityAdEventEntity newEvent(CommunityDirectAdEntity ad, AuthenticatedUser user,
                                            String clickId, String sessionId, String eventType) {
        CommunityAdEventEntity event = new CommunityAdEventEntity();
        event.setAdId(ad.getId());
        event.setEventType(eventType);
        event.setUserId(user.userId());
        event.setSessionId(StringUtils.hasText(sessionId) ? sessionId.trim() : null);
        event.setClickId(clickId);
        event.setLandingType(ad.getLandingType());
        event.setCreatedAt(LocalDateTime.now());
        return event;
    }

    private int configInt(String key, Long campusId, int fallback, int min, int max) {
        String raw = systemConfigService.getValue(key, campusId);
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String newClickId() {
        return "c" + UUID.randomUUID().toString().replace("-", "");
    }
}
