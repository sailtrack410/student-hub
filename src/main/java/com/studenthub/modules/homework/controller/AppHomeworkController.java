package com.studenthub.modules.homework.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.homework.controller.dto.HomeworkDetailResponse;
import com.studenthub.modules.homework.controller.dto.HomeworkPageQuery;
import com.studenthub.modules.homework.controller.dto.HomeworkSummaryResponse;
import com.studenthub.modules.homework.controller.dto.SubmitHomeworkRequest;
import com.studenthub.modules.homework.service.HomeworkService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序-作业中心：课程作业列表、详情与在线提交。
 * 数据按当前用户所属校区隔离，学生只能看到本校区作业、只能读写自己的提交记录。
 */
@RestController
@RequestMapping("/api/app/homework")
@Tag(name = "小程序-作业中心", description = "作业列表、作业详情与在线提交")
@RequiredArgsConstructor
public class AppHomeworkController {

    private final HomeworkService homeworkService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "分页查询作业", description = "只返回当前用户所属校区下 PUBLISHED 状态的作业，按发布时间倒序；"
            + "每条记录带 submitted 标记与最近提交时间，用于列表展示「已交/未交」。",
            operationId = "pageAppHomeworks")
    public Result<PageResult<HomeworkSummaryResponse>> page(HomeworkPageQuery query) {
        return Result.success(homeworkService.pageForApp(currentUserProvider.requireCurrentUser(), query));
    }

    @GetMapping("/{homeworkId}")
    @Operation(summary = "查询作业详情", description = "返回作业内容、截止时间、当前用户提交内容以及是否仍可提交（submittable）。"
            + "仅当前用户所属校区的作业可见。",
            operationId = "getAppHomeworkDetail")
    public Result<HomeworkDetailResponse> detail(@PathVariable Long homeworkId) {
        return Result.success(homeworkService.detailForApp(
                currentUserProvider.requireCurrentUser(), homeworkId));
    }

    @PostMapping("/{homeworkId}/submit")
    @Operation(summary = "提交作业", description = "提交内容非空；作业关闭或已过截止时间返回 REQUEST_409_01；"
            + "同一学生重复提交时更新原提交记录与提交时间，不新增记录。",
            operationId = "submitHomework")
    public Result<HomeworkDetailResponse> submit(@PathVariable Long homeworkId,
                                                 @RequestBody @Valid SubmitHomeworkRequest request) {
        return Result.success(homeworkService.submit(
                currentUserProvider.requireCurrentUser(), homeworkId, request));
    }
}
