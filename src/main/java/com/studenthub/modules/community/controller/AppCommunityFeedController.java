package com.studenthub.modules.community.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.community.controller.dto.CommunityFeedItemResponse;
import com.studenthub.modules.community.controller.dto.FeedAdItemResponse;
import com.studenthub.modules.community.service.CommunityAdFeedService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * App 社区混合信息流：自然帖子 + 官方直投广告（POST /api/app/community/feed）。
 * 原 GET /api/app/community/posts 保持不变。
 */
@RestController
@RequestMapping("/api/app/community")
@Tag(name = "小程序-社区信息流", description = "自然内容与官方直投广告混合流")
@RequiredArgsConstructor
public class AppCommunityFeedController {

    private final CommunityAdFeedService adFeedService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/feed")
    @Operation(summary = "社区混合信息流",
            description = "自然内容按 sort 分页后，每 N 条插入 1 条官方直投广告（N 默认 8）；广告带推广标识与 clickId，"
                    + "按优先级/时间窗/分区筛选并做频控与去重。",
            operationId = "getCommunityFeed")
    public Result<PageResult<CommunityFeedItemResponse>> feed(
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String sessionId) {
        return Result.success(adFeedService.feed(currentUserProvider.requireCurrentUser(),
                sectionId, sort, pageNum, pageSize, sessionId));
    }

    @GetMapping("/slot-ads")
    @Operation(summary = "App 广告位读取",
            description = "按广告位返回审核通过、已上架且在投放时间窗内的广告（最多 3 条，按优先级倒序、开始时间正序）；"
                    + "不参与信息流曝光频控，每条广告返回新的 clickId，可复用曝光/点击/落地事件上报。",
            operationId = "getCommunitySlotAds")
    public Result<List<FeedAdItemResponse>> slotAds(@RequestParam String slot) {
        return Result.success(adFeedService.slotAds(currentUserProvider.requireCurrentUser(), slot));
    }
}
