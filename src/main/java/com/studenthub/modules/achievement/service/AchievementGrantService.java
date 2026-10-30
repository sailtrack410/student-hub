package com.studenthub.modules.achievement.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.achievement.controller.dto.AdminAchievementGrantRequest;
import com.studenthub.modules.achievement.controller.dto.AdminAchievementGrantResponse;

/**
 * 成就发放记录（SYS09）：查询用户获得记录、管理端手动发放与撤销。
 *
 * <p>自动达成由 {@link AchievementService#evaluate(Long)} 在访问时写入（grant_source=AUTO）；
 * 手动发放写 grant_source=MANUAL，两者共用 user_achievement_badge 表，撤销后若用户仍满足
 * 自动解锁条件，下一轮评估会重新发放——如需彻底禁止，应先停用该勋章。</p>
 */
public interface AchievementGrantService {

    PageResult<AdminAchievementGrantResponse> page(int pageNum, int pageSize, Long userId, String badgeCode);

    AdminAchievementGrantResponse grant(AdminAchievementGrantRequest request);

    void revoke(Long id);
}
