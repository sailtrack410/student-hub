package com.studenthub.modules.social.service;

import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.social.controller.dto.FollowResponse;
import com.studenthub.modules.social.controller.dto.PrivacySettingsResponse;
import com.studenthub.modules.social.controller.dto.SocialStatsResponse;
import com.studenthub.modules.social.controller.dto.UserCardResponse;

/**
 * 社交关系（需求清单 2.x）：关注/取关、身份名片、四维统计、隐私设置。
 */
public interface SocialService {

    FollowResponse follow(AuthenticatedUser user, Long targetUserId);

    FollowResponse unfollow(AuthenticatedUser user, Long targetUserId);

    UserCardResponse card(AuthenticatedUser viewer, Long targetUserId);

    SocialStatsResponse stats(AuthenticatedUser user);

    PrivacySettingsResponse privacyOf(Long userId);

    PrivacySettingsResponse updatePrivacy(AuthenticatedUser user, PrivacySettingsResponse settings);

    /** 他人是否公开学业信息；缺行=公开 */
    boolean showAcademicInfo(Long userId);

    /** 他人是否公开勋章；缺行=公开 */
    boolean showMedals(Long userId);

    /** viewerId 是否已关注 targetId */
    boolean isFollowing(Long viewerId, Long targetId);
}
