package com.studenthub.modules.devtool;

import com.studenthub.common.Result;
import com.studenthub.modules.schedule.job.ScheduleSyncTimer;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 开发期调试：手动触发一次课表同步。仅 dev 环境生效。 */
@RestController
@RequestMapping("/api/dev/schedule-sync")
@Profile("dev")
@RequiredArgsConstructor
@Tag(name = "dev-调试", description = "课表同步手动触发（仅 dev）")
public class DevScheduleSyncController {

    private final ScheduleSyncTimer syncTimer;

    @PostMapping("/run")
    public Result<String> run() {
        syncTimer.run();
        return Result.success("已触发课表同步");
    }
}