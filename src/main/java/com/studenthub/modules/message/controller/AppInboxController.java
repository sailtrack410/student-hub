package com.studenthub.modules.message.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import com.studenthub.modules.message.controller.dto.InboxItemResponse;
import com.studenthub.modules.message.service.InboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/messages")
@Tag(name = "小程序-统一收件箱", description = "系统通知/私信/AI 会话三路聚合的收件箱流")
@RequiredArgsConstructor
public class AppInboxController {

    private final InboxService inboxService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/inbox")
    @Operation(summary = "统一会话收件箱流", description = "三路会话源（流程通知折叠 1 条/私信按对端/AI 会话按条）聚合为扁平流，"
            + "按最后消息时间倒序分页；lastTime 为原始时间戳，相对时间由前端渲染。", operationId = "inbox")
    public Result<PageResult<InboxItemResponse>> inbox(@RequestParam(defaultValue = "1") int pageNum,
                                                       @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(inboxService.inbox(currentUserProvider.requireCurrentUser(), pageNum, pageSize));
    }
}
