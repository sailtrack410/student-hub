package com.studenthub.modules.aichat.intent;

/** AI 助手意图枚举。 */
public enum AiIntent {
    /** 查某一天课表（携带 dayOfWeek 参数） */
    SCHEDULE_DAY,
    /** 查本周课表 */
    SCHEDULE_WEEK,
    /** 查我的申请/工单进度（携带 statusFilter 参数） */
    MY_REQUESTS,
    /** 要办理某服务（携带 itemCode 参数） */
    SERVICE_ACTION,
    /** 其它，走普通闲聊 */
    FALLBACK
}