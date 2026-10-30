package com.studenthub.modules.achievement.controller.admin;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.achievement.controller.dto.AdminAchievementGrantRequest;
import com.studenthub.modules.achievement.controller.dto.AdminAchievementGrantResponse;
import com.studenthub.modules.achievement.service.AchievementGrantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-成就发放记录（SYS09）：用户获得记录分页、手动发放、撤销。
 * 自动达成仍由 App 侧评估写入；这里只做运营补偿与纠错，所有写操作由审计 AOP 留痕。
 */
@RestController
@RequestMapping("/api/admin/achievements")
@Tag(name = "管理端-成就发放", description = "成就发放记录、手动发放与撤销")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:user:manage')")
public class AdminAchievementGrantController {

    private final AchievementGrantService achievementGrantService;

    @GetMapping("/grants")
    @Operation(summary = "成就发放记录分页",
            description = "按用户 ID / 勋章编码过滤，按解锁时间倒序；只返回已解锁记录。",
            operationId = "adminPageAchievementGrants")
    public Result<PageResult<AdminAchievementGrantResponse>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String badgeCode) {
        return Result.success(achievementGrantService.page(pageNum, pageSize, userId, badgeCode));
    }

    @PostMapping("/grants")
    @Operation(summary = "手动发放勋章",
            description = "用于活动奖励/补偿；已拥有时返回 400，重复发放被拒绝；勋章停用时拒绝发放。",
            operationId = "adminCreateAchievementGrant")
    public Result<AdminAchievementGrantResponse> grant(
            @RequestBody @Valid AdminAchievementGrantRequest request) {
        return Result.success(achievementGrantService.grant(request));
    }

    @DeleteMapping("/grants/{id}")
    @Operation(summary = "撤销发放记录",
            description = "删除发放记录并清理佩戴态；若用户仍满足自动解锁条件，下一轮评估会重新发放。",
            operationId = "adminRevokeAchievementGrant")
    public Result<Void> revoke(@PathVariable Long id) {
        achievementGrantService.revoke(id);
        return Result.success();
    }
}
