package com.studenthub.modules.devtool;

import com.studenthub.common.Result;
import com.studenthub.integration.jw.KingosoftScheduleClient;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.jwauth.security.JwSessionStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 开发期专用调试接口：用托管会话直接拉取教务课表页，确认端点与解析。
 * 仅 dev 环境生效。
 */
@RestController
@RequestMapping("/api/dev/schedule")
@Profile("dev")
@RequiredArgsConstructor
@Tag(name = "dev-调试", description = "开发期课表端点调试（仅 dev）")
public class DevScheduleController {

    private final CurrentUserProvider currentUserProvider;
    private final JwSessionStore jwSessionStore;
    private final KingosoftScheduleClient scheduleClient;

    @GetMapping("/raw")
    @Operation(summary = "拉取教务课表原始页面，确认端点与解析")
    public Result<KingosoftScheduleClient.JwSchedulePage> raw() {
        Long userId = currentUserProvider.requireCurrentUser().userId();
        Optional<JwSessionStore.JwSession> session = jwSessionStore.find(userId);
        if (session.isEmpty()) {
            return Result.failed("DEV_NO_SESSION", "无托管教务会话");
        }
        return Result.success(scheduleClient.fetchSchedulePage(
                session.get().cookieHeader(), null));
    }
}