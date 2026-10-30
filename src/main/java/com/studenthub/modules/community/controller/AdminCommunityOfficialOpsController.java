package com.studenthub.modules.community.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.community.controller.dto.AdminOfficialPinRequest;
import com.studenthub.modules.community.controller.dto.AdminOfficialPublishRequest;
import com.studenthub.modules.community.controller.dto.AdminOfficialPublishResponse;
import com.studenthub.modules.community.controller.dto.AdminOfficialReplyRequest;
import com.studenthub.modules.community.controller.dto.AdminOfficialReplyResponse;
import com.studenthub.modules.community.service.CommunityOfficialOpsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-官方号运营（C12）：帖子置顶/取消、官方账号回复帖子、官方账号代发社区帖子。
 * 所有写操作由管理端审计 AOP 自动留痕。
 */
@RestController
@RequestMapping("/api/admin/community")
@Tag(name = "管理端-官方号运营", description = "帖子置顶、官方回复与官方代发")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('community:moderate') "
        + "or hasAuthority('community:official:manage')")
public class AdminCommunityOfficialOpsController {

    private final CommunityOfficialOpsService officialOpsService;

    @PostMapping("/posts/{postId}/pin")
    @Operation(summary = "帖子置顶/取消置顶（C12）",
            description = "pinned=true 置顶并记录置顶时间与操作人；false 取消置顶。置顶不改变帖子状态，仅影响信息流排序。",
            operationId = "adminPinCommunityPost")
    public Result<Void> pin(@PathVariable Long postId,
                            @RequestBody @Valid AdminOfficialPinRequest request) {
        officialOpsService.pinPost(postId, request);
        return Result.success();
    }

    @PostMapping("/posts/{postId}/official-reply")
    @Operation(summary = "官方账号回复帖子（C12）",
            description = "指定 OFFICIAL_AUTO/OFFICIAL_HUMAN 账号发一条官方回复；命中屏蔽词时拒绝，楼主会收到站内通知。",
            operationId = "adminReplyCommunityPostAsOfficial")
    public Result<AdminOfficialReplyResponse> reply(@PathVariable Long postId,
                                                    @RequestBody @Valid AdminOfficialReplyRequest request) {
        return Result.success(officialOpsService.replyAsOfficial(postId, request));
    }

    @PostMapping("/posts/official-publish")
    @Operation(summary = "官方账号代发社区帖子（C12）",
            description = "以官方账号身份发布到指定分区；图片最多 9 张、话题最多 8 个；命中屏蔽词直接拒绝（不静默隐藏官方内容）。",
            operationId = "adminPublishCommunityPostAsOfficial")
    public Result<AdminOfficialPublishResponse> publish(
            @RequestBody @Valid AdminOfficialPublishRequest request) {
        return Result.success(officialOpsService.publishAsOfficial(request));
    }
}
