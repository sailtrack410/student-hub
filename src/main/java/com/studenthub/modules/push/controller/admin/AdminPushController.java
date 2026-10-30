package com.studenthub.modules.push.controller.admin;

import com.studenthub.common.PageResult;
import com.studenthub.common.Result;
import com.studenthub.modules.push.controller.dto.PushDeliveryResponse;
import com.studenthub.modules.push.controller.dto.PushStatsResponse;
import com.studenthub.modules.push.controller.dto.PushTaskPageQuery;
import com.studenthub.modules.push.controller.dto.PushTaskRequest;
import com.studenthub.modules.push.controller.dto.PushTaskResponse;
import com.studenthub.modules.push.controller.dto.PushTemplateRequest;
import com.studenthub.modules.push.controller.dto.PushTemplateResponse;
import com.studenthub.modules.push.controller.dto.PushTemplateTestRequest;
import com.studenthub.modules.push.controller.dto.PushTemplateTestResponse;
import com.studenthub.modules.push.provider.PushProviderRegistry;
import com.studenthub.modules.push.service.PushTaskService;
import com.studenthub.modules.push.service.PushTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端-消息推送：模板（N02）、任务（N03）、触达统计（N04）。
 *
 * <p>通道能力通过 {@link PushProviderRegistry} 如实暴露；未配置通道返回 NOT_CONFIGURED，
 * 测试发送只发往当前管理员，不会向真实用户伪造投递。</p>
 */
@RestController
@RequestMapping("/api/admin/push")
@Tag(name = "管理端-消息推送", description = "推送模板、推送任务与触达统计")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('push:manage')")
public class AdminPushController {

    private final PushTemplateService templateService;
    private final PushTaskService taskService;
    private final PushProviderRegistry providerRegistry;

    // ---------------------------------------------------------------- 模板 N02

    @GetMapping("/templates")
    @Operation(summary = "推送模板列表", description = "按关键词/场景/状态过滤，更新时间倒序。",
            operationId = "adminListPushTemplates")
    public Result<List<PushTemplateResponse>> listTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String status) {
        return Result.success(templateService.list(keyword, scene, status));
    }

    @PostMapping("/templates")
    @Operation(summary = "创建推送模板", description = "同一校区模板编码唯一；渠道取自 LOG/WS/FCM/APNS/WEBPUSH。",
            operationId = "adminCreatePushTemplate")
    public Result<PushTemplateResponse> createTemplate(@RequestBody @Valid PushTemplateRequest request) {
        return Result.success(templateService.create(request));
    }

    @PutMapping("/templates/{templateId}")
    @Operation(summary = "更新推送模板", description = "模板编码不可修改。",
            operationId = "adminUpdatePushTemplate")
    public Result<PushTemplateResponse> updateTemplate(@PathVariable Long templateId,
                                                       @RequestBody @Valid PushTemplateRequest request) {
        return Result.success(templateService.update(templateId, request));
    }

    @DeleteMapping("/templates/{templateId}")
    @Operation(summary = "删除推送模板", description = "软删除；已创建任务保留标题正文快照。",
            operationId = "adminDeletePushTemplate")
    public Result<Void> deleteTemplate(@PathVariable Long templateId) {
        templateService.delete(templateId);
        return Result.success();
    }

    @PostMapping("/templates/{templateId}/test")
    @Operation(summary = "测试发送模板", description = "渲染变量后发送到当前管理员站内通知，并尝试已配置通道；"
            + "未配置通道以 NOT_CONFIGURED 返回。", operationId = "adminTestPushTemplate")
    public Result<PushTemplateTestResponse> testTemplate(@PathVariable Long templateId,
                                                         @RequestBody(required = false) @Valid PushTemplateTestRequest request) {
        return Result.success(templateService.test(templateId, request));
    }

    // ---------------------------------------------------------------- 任务 N03

    @GetMapping("/tasks")
    @Operation(summary = "推送任务分页", operationId = "adminPagePushTasks")
    public Result<PageResult<PushTaskResponse>> pageTasks(PushTaskPageQuery query) {
        return Result.success(taskService.page(query));
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "推送任务详情", operationId = "adminGetPushTask")
    public Result<PushTaskResponse> taskDetail(@PathVariable Long taskId) {
        return Result.success(taskService.detail(taskId));
    }

    @GetMapping("/tasks/{taskId}/deliveries")
    @Operation(summary = "推送投递明细分页", operationId = "adminPagePushDeliveries")
    public Result<PageResult<PushDeliveryResponse>> pageDeliveries(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(taskService.pageDeliveries(taskId, pageNum, pageSize, status));
    }

    @PostMapping("/tasks")
    @Operation(summary = "创建推送任务", description = "action=DRAFT 存草稿 / SEND 立即发送 / SCHEDULE 定时；"
            + "立即发送会真实生成投递明细与站内通知。", operationId = "adminCreatePushTask")
    public Result<PushTaskResponse> createTask(@RequestBody @Valid PushTaskRequest request) {
        return Result.success(taskService.create(request));
    }

    @PutMapping("/tasks/{taskId}")
    @Operation(summary = "更新推送任务", description = "仅草稿/定时待发可编辑。",
            operationId = "adminUpdatePushTask")
    public Result<PushTaskResponse> updateTask(@PathVariable Long taskId,
                                               @RequestBody @Valid PushTaskRequest request) {
        return Result.success(taskService.update(taskId, request));
    }

    @DeleteMapping("/tasks/{taskId}")
    @Operation(summary = "删除推送任务", description = "发送中任务不可删除；同时清理投递明细。",
            operationId = "adminDeletePushTask")
    public Result<Void> deleteTask(@PathVariable Long taskId) {
        taskService.delete(taskId);
        return Result.success();
    }

    @PostMapping("/tasks/{taskId}/send")
    @Operation(summary = "立即发送任务", operationId = "adminSendPushTask")
    public Result<PushTaskResponse> sendTask(@PathVariable Long taskId) {
        return Result.success(taskService.sendNow(taskId));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    @Operation(summary = "取消推送任务", description = "仅草稿/定时待发可取消。",
            operationId = "adminCancelPushTask")
    public Result<PushTaskResponse> cancelTask(@PathVariable Long taskId) {
        return Result.success(taskService.cancel(taskId));
    }

    @PostMapping("/tasks/{taskId}/retry")
    @Operation(summary = "重试失败投递", description = "重试 FAILED 与 NOT_CONFIGURED 明细，并重算任务计数。",
            operationId = "adminRetryPushTask")
    public Result<PushTaskResponse> retryTask(@PathVariable Long taskId) {
        return Result.success(taskService.retry(taskId));
    }

    // ---------------------------------------------------------------- 统计 N04

    @GetMapping("/stats")
    @Operation(summary = "触达统计", description = "按渠道聚合发送/成功/失败/到达/点击；未配置通道单列。",
            operationId = "adminPushStats")
    public Result<PushStatsResponse> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.success(taskService.stats(from, to));
    }

    @GetMapping("/channels")
    @Operation(summary = "推送通道能力", description = "返回每个通道是否具备真实投递能力及未配置原因。",
            operationId = "adminListPushChannels")
    public Result<List<PushProviderRegistry.ChannelStatus>> channels() {
        return Result.success(providerRegistry.statuses());
    }
}
