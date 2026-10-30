package com.studenthub.modules.homework.controller;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.homework.controller.dto.AdminHomeworkPageQuery;
import com.studenthub.modules.homework.controller.dto.AdminHomeworkResponse;
import com.studenthub.modules.homework.controller.dto.CreateHomeworkRequest;
import com.studenthub.modules.homework.service.HomeworkService;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-作业管理：发布作业、分页查询与关闭作业。
 * 作业归属于操作人所属校区，默认只管理本校区数据。
 */
@RestController
@RequestMapping("/api/admin/homework")
@Tag(name = "管理端-作业管理", description = "作业发布、作业分页与关闭")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('catalog:manage')")
public class AdminHomeworkController {

    private final HomeworkService homeworkService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @Operation(summary = "发布作业", description = "课程名称、标题、内容必填，截止时间必须晚于发布时间；"
            + "发布教师不填时使用当前操作人姓名。发布后状态为 PUBLISHED，学生即可在 App 查看。",
            operationId = "createHomework")
    public Result<AdminHomeworkResponse> create(@RequestBody @Valid CreateHomeworkRequest request) {
        return Result.success(homeworkService.create(currentUserProvider.requireCurrentUser(), request));
    }

    @GetMapping
    @Operation(summary = "分页查询作业", description = "默认只返回当前账号所属校区的作业，按发布时间倒序；"
            + "可按状态 PUBLISHED/CLOSED 过滤。",
            operationId = "pageAdminHomeworks")
    public Result<PageResult<AdminHomeworkResponse>> page(AdminHomeworkPageQuery query) {
        return Result.success(homeworkService.pageForAdmin(currentUserProvider.requireCurrentUser(), query));
    }

    @PostMapping("/{homeworkId}/close")
    @Operation(summary = "关闭作业", description = "关闭后学生端列表不再展示且不可再提交；重复关闭幂等返回当前状态。",
            operationId = "closeHomework")
    public Result<AdminHomeworkResponse> close(@PathVariable Long homeworkId) {
        return Result.success(homeworkService.close(currentUserProvider.requireCurrentUser(), homeworkId));
    }
}
