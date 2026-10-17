package com.studenthub.modules.social.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.social.controller.dto.FollowResponse;
import com.studenthub.modules.social.controller.dto.PrivacySettingsResponse;
import com.studenthub.modules.social.controller.dto.SocialStatsResponse;
import com.studenthub.modules.social.controller.dto.UserCardResponse;
import com.studenthub.modules.social.service.SocialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 社交关系（API需求清单 2.x）：关注/取关、身份名片、四维统计、隐私设置。
 */
@RestController
@RequestMapping("/api/app")
@Tag(name = "小程序-社交关系", description = "关注关系、身份名片、社交统计与隐私设置")
@RequiredArgsConstructor
public class AppSocialController {

    private final SocialService socialService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/users/{userId}/card")
    @Operation(summary = "用户轻量级身份名片", description = "气泡弹层直出：经验条、关注态、勋章 4 格预览；勋章预览受对方 showMedals 控制。",
            operationId = "getUserCard")
    public Result<UserCardResponse> card(@PathVariable Long userId) {
        return Result.success(socialService.card(currentUserProvider.requireCurrentUser(), userId));
    }

    @PostMapping("/users/{userId}/follow")
    @Operation(summary = "关注用户", description = "不可关注自己；重复关注幂等返回当前态。", operationId = "followUser")
    public Result<FollowResponse> follow(@PathVariable Long userId) {
        return Result.success(socialService.follow(currentUserProvider.requireCurrentUser(), userId));
    }

    @DeleteMapping("/users/{userId}/follow")
    @Operation(summary = "取消关注用户", description = "未关注时幂等返回 following=false。", operationId = "unfollowUser")
    public Result<FollowResponse> unfollow(@PathVariable Long userId) {
        return Result.success(socialService.unfollow(currentUserProvider.requireCurrentUser(), userId));
    }

    @GetMapping("/profile/stats")
    @Operation(summary = "个人四维社交数据统计", description = "累计获赞（帖子+评论，仅有效内容）、互关好友、关注数、粉丝数。",
            operationId = "getSocialStats")
    public Result<SocialStatsResponse> stats() {
        return Result.success(socialService.stats(currentUserProvider.requireCurrentUser()));
    }

    @GetMapping("/profile/privacy")
    @Operation(summary = "查询隐私设置", description = "缺省全公开。", operationId = "getPrivacySettings")
    public Result<PrivacySettingsResponse> privacy() {
        return Result.success(socialService.privacyOf(currentUserProvider.requireCurrentUser().userId()));
    }

    @PutMapping("/profile/privacy")
    @Operation(summary = "修改隐私设置", description = "showAcademicInfo=false 时他人主页学业字段置空；showMedals=false 时不展示勋章。",
            operationId = "updatePrivacySettings")
    public Result<PrivacySettingsResponse> updatePrivacy(@RequestBody PrivacySettingsResponse settings) {
        return Result.success(socialService.updatePrivacy(currentUserProvider.requireCurrentUser(), settings));
    }
}
