package com.studenthub.modules.aichat.intent;

import com.studenthub.modules.aichat.controller.dto.AiActionResponse;

import java.time.LocalDate;
import java.util.List;

/** 供 AI 意图使用的数据供给与服务入口解析。 */
public interface AiAssistantDataService {

    /**
     * 查课表：{@code date} 非空时定位到具体某天；否则 {@code dayOfWeek} 非空时查本周的周X；
     * 二者皆空时查本周 7 天。
     */
    AiAssistantResolved resolveSchedule(Long userId, LocalDate date, Integer dayOfWeek);

    /** 查我的申请（可选状态过滤），返回最近申请概要与查看入口。 */
    AiAssistantResolved resolveMyRequests(Long userId, String statusFilter);

    /** 解析服务入口卡片（按 itemCode），找不到返回空动作 + 提示文本。 */
    AiAssistantResolved resolveServiceItem(String itemCode, Long campusId);

    /** 序列化动作卡片为 JSON 字符串。 */
    String serializeActions(List<AiActionResponse> actions);
}