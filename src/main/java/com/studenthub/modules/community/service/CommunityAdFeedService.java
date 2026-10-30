package com.studenthub.modules.community.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.community.controller.dto.AdClickResponse;
import com.studenthub.modules.community.controller.dto.AdImpressionResponse;
import com.studenthub.modules.community.controller.dto.AdLandingExitResponse;
import com.studenthub.modules.community.controller.dto.AdLandingResponse;
import com.studenthub.modules.community.controller.dto.CommunityFeedItemResponse;
import com.studenthub.modules.community.controller.dto.FeedAdItemResponse;
import com.studenthub.modules.community.controller.dto.ReportAdClickRequest;
import com.studenthub.modules.community.controller.dto.ReportAdImpressionRequest;
import com.studenthub.modules.community.controller.dto.ReportAdLandingExitRequest;
import com.studenthub.modules.community.controller.dto.ReportAdLandingLoadedRequest;
import com.studenthub.modules.identity.security.AuthenticatedUser;

import java.util.List;

/**
 * App 端信息流广告：混合流插入与曝光/点击/落地/停留事件上报。
 * 「可测落地」仅限 IN_APP_WEBVIEW / APP_PAGE；EXTERNAL_BROWSER / DEEPLINK 只记录点击。
 */
public interface CommunityAdFeedService {

    PageResult<CommunityFeedItemResponse> feed(AuthenticatedUser user, Long sectionId, String sort,
                                               long pageNum, long pageSize, String sessionId);

    /**
     * App 广告位读取（BANNER/SERVICE_HALL/SPLASH）：仅返回审核通过、已上架且在投放时间窗内的广告，
     * 按优先级倒序、开始时间正序最多 3 条；不参与信息流曝光频控，每条生成新的 clickId。
     */
    List<FeedAdItemResponse> slotAds(AuthenticatedUser user, String slot);

    AdImpressionResponse reportImpression(AuthenticatedUser user, Long adId, ReportAdImpressionRequest request);

    AdClickResponse reportClick(AuthenticatedUser user, Long adId, ReportAdClickRequest request);

    AdLandingResponse reportLandingLoaded(AuthenticatedUser user, ReportAdLandingLoadedRequest request);

    AdLandingExitResponse reportLandingExit(AuthenticatedUser user, ReportAdLandingExitRequest request);
}
