package com.studenthub.modules.push.controller;

import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.push.service.PushTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * App 侧推送点击回执：用于 N04「点击」指标的真实采集。
 *
 * <p>当前 App 尚未接入系统推送，因此该接口的调用方暂为 0，统计中的 clicked 会保持 0；
 * 这不是假数据，而是「能力已就绪、客户端未接入」的如实状态。接入后由 App 在用户点击
 * 推送时携带 taskId/userId/channel 调用即可。</p>
 */
@RestController
@RequestMapping("/api/app/push")
@Tag(name = "小程序-系统推送", description = "推送 Token 登记、点击回执")
@RequiredArgsConstructor
public class AppPushDeliveryController {

    private final PushTaskService pushTaskService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/deliveries/{taskId}/click")
    @Operation(summary = "推送点击回执", description = "只能回执发送给当前用户本人的投递记录。",
            operationId = "ackPushDeliveryClick")
    public Result<Boolean> click(@PathVariable Long taskId,
                                 @RequestParam(defaultValue = "WS") String channel) {
        Long userId = currentUserProvider.requireCurrentUser().userId();
        return Result.success(pushTaskService.recordClick(taskId, userId, channel));
    }
}
