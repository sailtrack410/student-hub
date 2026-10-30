package com.studenthub.modules.push.service;

import com.studenthub.common.PageResult;
import com.studenthub.modules.push.controller.dto.PushDeliveryResponse;
import com.studenthub.modules.push.controller.dto.PushStatsResponse;
import com.studenthub.modules.push.controller.dto.PushTaskPageQuery;
import com.studenthub.modules.push.controller.dto.PushTaskRequest;
import com.studenthub.modules.push.controller.dto.PushTaskResponse;

import java.time.LocalDateTime;

/**
 * 管理端推送任务：创建（人群/渠道/定时/频控）、发送、取消、失败重试、状态与触达统计。
 */
public interface PushTaskService {

    PageResult<PushTaskResponse> page(PushTaskPageQuery query);

    PushTaskResponse detail(Long id);

    PushTaskResponse create(PushTaskRequest request);

    PushTaskResponse update(Long id, PushTaskRequest request);

    void delete(Long id);

    /** 立即发送（DRAFT/SCHEDULED/FAILED 可触发；SENDING 拒绝）。 */
    PushTaskResponse sendNow(Long id);

    /** 取消（仅 DRAFT/SCHEDULED 可取消）。 */
    PushTaskResponse cancel(Long id);

    /** 重试失败/未配置的投递明细，并重算任务计数。 */
    PushTaskResponse retry(Long id);

    PageResult<PushDeliveryResponse> pageDeliveries(Long taskId, Integer pageNum, Integer pageSize, String status);

    PushStatsResponse stats(LocalDateTime from, LocalDateTime to);

    /** 定时任务扫描：把到点的 SCHEDULED 任务置为发送。返回本次处理的任务数。 */
    int dispatchDueTasks();

    /** App 侧点击回调：按 (taskId,userId,channel) 记录点击。返回是否命中。 */
    boolean recordClick(Long taskId, Long userId, String channel);
}
