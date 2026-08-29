package com.studenthub.modules.aichat.intent;

import java.time.LocalDate;

/** 意图分类结果：intent + 可选参数。 */
public record AiIntentMatch(
        AiIntent intent,
        /** SCHEDULE_DAY 时为 1~7（周一~周日）；内容为“今天/明天/周X”时保留（本周语义） */
        Integer dayOfWeek,
        /** SCHEDULE_DAY 时的目标具体日期（如“8月31日”/今天/明天顺延）；与 dayOfWeek 二选一或均为空 */
        LocalDate date,
        /** MY_REQUESTS 时的状态过滤：IN_PROGRESS / COMPLETED / null=全部 */
        String statusFilter,
        /** SERVICE_ACTION 时的服务项编码 */
        String itemCode,
        /** SERVICE_ACTION 时的服务项名称（提示词用） */
        String itemName) {

    public static AiIntentMatch fallback() {
        return new AiIntentMatch(AiIntent.FALLBACK, null, null, null, null, null);
    }

    public static AiIntentMatch scheduleDay(int dayOfWeek) {
        return new AiIntentMatch(AiIntent.SCHEDULE_DAY, dayOfWeek, null, null, null, null);
    }

    public static AiIntentMatch scheduleDate(LocalDate date) {
        return new AiIntentMatch(AiIntent.SCHEDULE_DAY, null, date, null, null, null);
    }

    public static AiIntentMatch scheduleWeek() {
        return new AiIntentMatch(AiIntent.SCHEDULE_WEEK, null, null, null, null, null);
    }

    public static AiIntentMatch myRequests(String statusFilter) {
        return new AiIntentMatch(AiIntent.MY_REQUESTS, null, null, statusFilter, null, null);
    }

    public static AiIntentMatch serviceAction(String itemCode, String itemName) {
        return new AiIntentMatch(AiIntent.SERVICE_ACTION, null, null, null, itemCode, itemName);
    }
}