package com.studenthub.modules.push.content.controller.admin;

import com.studenthub.common.Result;
import com.studenthub.modules.push.content.controller.dto.ContentCalendarRequest;
import com.studenthub.modules.push.content.controller.dto.ContentCalendarResponse;
import com.studenthub.modules.push.content.controller.dto.ContentPreviewResponse;
import com.studenthub.modules.push.content.controller.dto.ContentSlotRequest;
import com.studenthub.modules.push.content.controller.dto.ContentSlotResponse;
import com.studenthub.modules.push.content.service.ContentOpsService;
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
 * 管理端-内容运营（A03/C09）：运营位与内容日历的增删改、上下线、预览。
 */
@RestController
@RequestMapping("/api/admin/content")
@Tag(name = "管理端-内容运营", description = "推荐位/活动/专题排期、上下线与预览")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('content:operate') or hasAuthority('community:moderate')")
public class AdminContentOpsController {

    private final ContentOpsService contentOpsService;

    // ---------------------------------------------------------------- 运营位

    @GetMapping("/slots")
    @Operation(summary = "运营位列表", description = "按类型/状态/位置/关键词过滤，权重与排序值优先。",
            operationId = "adminListContentSlots")
    public Result<List<ContentSlotResponse>> listSlots(
            @RequestParam(required = false) String slotType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String position,
            @RequestParam(required = false) String keyword) {
        return Result.success(contentOpsService.listSlots(slotType, status, position, keyword));
    }

    @PostMapping("/slots")
    @Operation(summary = "创建运营位", description = "编码同校区唯一；创建后为草稿态。",
            operationId = "adminCreateContentSlot")
    public Result<ContentSlotResponse> createSlot(@RequestBody @Valid ContentSlotRequest request) {
        return Result.success(contentOpsService.createSlot(request));
    }

    @PutMapping("/slots/{slotId}")
    @Operation(summary = "更新运营位", description = "编码不可修改；在线位可热更新文案与时间窗。",
            operationId = "adminUpdateContentSlot")
    public Result<ContentSlotResponse> updateSlot(@PathVariable Long slotId,
                                                  @RequestBody @Valid ContentSlotRequest request) {
        return Result.success(contentOpsService.updateSlot(slotId, request));
    }

    @DeleteMapping("/slots/{slotId}")
    @Operation(summary = "删除运营位", description = "在线或仍被排期引用的运营位不可删除。",
            operationId = "adminDeleteContentSlot")
    public Result<Void> deleteSlot(@PathVariable Long slotId) {
        contentOpsService.deleteSlot(slotId);
        return Result.success();
    }

    @PostMapping("/slots/{slotId}/online")
    @Operation(summary = "运营位上线", description = "上线前执行与预览相同的校验，失败返回 400 与具体原因。",
            operationId = "adminOnlineContentSlot")
    public Result<ContentSlotResponse> onlineSlot(@PathVariable Long slotId) {
        return Result.success(contentOpsService.onlineSlot(slotId));
    }

    @PostMapping("/slots/{slotId}/offline")
    @Operation(summary = "运营位下线", operationId = "adminOfflineContentSlot")
    public Result<ContentSlotResponse> offlineSlot(@PathVariable Long slotId) {
        return Result.success(contentOpsService.offlineSlot(slotId));
    }

    @GetMapping("/slots/{slotId}/preview")
    @Operation(summary = "运营位预览", description = "返回渲染字段、校验告警与同位置冲突的在线内容。",
            operationId = "adminPreviewContentSlot")
    public Result<ContentPreviewResponse> previewSlot(@PathVariable Long slotId) {
        return Result.success(contentOpsService.previewSlot(slotId));
    }

    // ---------------------------------------------------------------- 内容日历

    @GetMapping("/calendar")
    @Operation(summary = "内容日历列表", description = "按时间范围（与排期区间相交）/状态/类型过滤。",
            operationId = "adminListContentCalendar")
    public Result<List<ContentCalendarResponse>> listCalendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String entryType) {
        return Result.success(contentOpsService.listCalendar(from, to, status, entryType));
    }

    @PostMapping("/calendar")
    @Operation(summary = "创建内容排期", description = "创建后为 PLANNED，需上线才生效。",
            operationId = "adminCreateContentCalendar")
    public Result<ContentCalendarResponse> createCalendar(@RequestBody @Valid ContentCalendarRequest request) {
        return Result.success(contentOpsService.createCalendar(request));
    }

    @PutMapping("/calendar/{entryId}")
    @Operation(summary = "更新内容排期", description = "仅 PLANNED 状态可编辑。",
            operationId = "adminUpdateContentCalendar")
    public Result<ContentCalendarResponse> updateCalendar(@PathVariable Long entryId,
                                                          @RequestBody @Valid ContentCalendarRequest request) {
        return Result.success(contentOpsService.updateCalendar(entryId, request));
    }

    @DeleteMapping("/calendar/{entryId}")
    @Operation(summary = "删除内容排期", description = "已上线排期需先下线。",
            operationId = "adminDeleteContentCalendar")
    public Result<Void> deleteCalendar(@PathVariable Long entryId) {
        contentOpsService.deleteCalendar(entryId);
        return Result.success();
    }

    @PostMapping("/calendar/{entryId}/publish")
    @Operation(summary = "内容排期上线", operationId = "adminPublishContentCalendar")
    public Result<ContentCalendarResponse> publishCalendar(@PathVariable Long entryId) {
        return Result.success(contentOpsService.publishCalendar(entryId));
    }

    @PostMapping("/calendar/{entryId}/offline")
    @Operation(summary = "内容排期下线", operationId = "adminOfflineContentCalendar")
    public Result<ContentCalendarResponse> offlineCalendar(@PathVariable Long entryId) {
        return Result.success(contentOpsService.offlineCalendar(entryId));
    }

    @GetMapping("/calendar/{entryId}/preview")
    @Operation(summary = "内容排期预览", description = "返回渲染字段、告警与同运营位重叠的已上线排期。",
            operationId = "adminPreviewContentCalendar")
    public Result<ContentPreviewResponse> previewCalendar(@PathVariable Long entryId) {
        return Result.success(contentOpsService.previewCalendar(entryId));
    }
}
