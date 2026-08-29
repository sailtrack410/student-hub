package com.studenthub.modules.aichat.intent;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规则优先的意图分类器：先用本地关键词/正则快速命中高频意图，
 * 未命中回退 FALLBACK 走普通 LLM 闲聊，避免额外分类调用。
 */
@Component
public class AiIntentClassifier {

    /** 周一~周日 中文别名 → ISO 1~7 */
    private static final Map<String, Integer> DAY_WORDS = new LinkedHashMap<>();

    private static final Map<String, String[]> SERVICE_KEYWORDS = new LinkedHashMap<>();

    static {
        DAY_WORDS.put("周一", 1);
        DAY_WORDS.put("星期一", 1);
        DAY_WORDS.put("礼拜一", 1);
        DAY_WORDS.put("周二", 2);
        DAY_WORDS.put("星期二", 2);
        DAY_WORDS.put("礼拜二", 2);
        DAY_WORDS.put("周三", 3);
        DAY_WORDS.put("星期三", 3);
        DAY_WORDS.put("礼拜三", 3);
        DAY_WORDS.put("周四", 4);
        DAY_WORDS.put("星期四", 4);
        DAY_WORDS.put("礼拜四", 4);
        DAY_WORDS.put("周五", 5);
        DAY_WORDS.put("星期五", 5);
        DAY_WORDS.put("礼拜五", 5);
        DAY_WORDS.put("周六", 6);
        DAY_WORDS.put("星期六", 6);
        DAY_WORDS.put("礼拜六", 6);
        DAY_WORDS.put("周日", 7);
        DAY_WORDS.put("星期日", 7);
        DAY_WORDS.put("星期天", 7);
        DAY_WORDS.put("礼拜天", 7);
        DAY_WORDS.put("礼拜日", 7);

        SERVICE_KEYWORDS.put("AFFAIR-LEAVE", new String[]{"请假"});
        SERVICE_KEYWORDS.put("REPAIR-DORM", new String[]{"报修", "维修", "修理", "坏了", "漏水"});
        SERVICE_KEYWORDS.put("REPAIR-PUBLIC", new String[]{"公共区域", "教室报修", "公共报修"});
        SERVICE_KEYWORDS.put("CERT-ENROLL", new String[]{"在读证明", "在读", "证明"});
        SERVICE_KEYWORDS.put("AFFAIR-VENUE", new String[]{"场地", "活动场地"});
        SERVICE_KEYWORDS.put("GUIDE-CONSULT", new String[]{"咨询"});
    }

    public AiIntentMatch classify(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            return AiIntentMatch.fallback();
        }

        // 1) 课表：问今天/周几/本周有什么课
        AiIntentMatch schedule = classifySchedule(text);
        if (schedule != null) {
            return schedule;
        }

        // 2) 我的申请/工单进度
        AiIntentMatch requests = classifyMyRequests(text);
        if (requests != null) {
            return requests;
        }

        // 3) 要办理某服务
        AiIntentMatch service = classifyServiceAction(text);
        if (service != null) {
            return service;
        }

        return AiIntentMatch.fallback();
    }

    private AiIntentMatch classifySchedule(String text) {
        boolean talksAboutCourse = containsAny(text, "课", "课程", "课表", "上课", "排课");
        if (!talksAboutCourse) {
            return null;
        }

        if (containsAny(text, "本周", "这周", "这星期", "周几", "星期几")) {
            return AiIntentMatch.scheduleWeek();
        }
        if (contains(text, "今天")) {
            return AiIntentMatch.scheduleDay(LocalDate.now().getDayOfWeek().getValue());
        }
        if (contains(text, "明天")) {
            return AiIntentMatch.scheduleDay(shift(LocalDate.now().getDayOfWeek(), 1));
        }
        if (contains(text, "后天")) {
            return AiIntentMatch.scheduleDay(shift(LocalDate.now().getDayOfWeek(), 2));
        }
        for (Map.Entry<String, Integer> entry : DAY_WORDS.entrySet()) {
            if (contains(text, entry.getKey())) {
                return AiIntentMatch.scheduleDay(entry.getValue());
            }
        }
        // 只出现"课"但没有明确星期 → 默认问本周
        return AiIntentMatch.scheduleWeek();
    }

    private AiIntentMatch classifyMyRequests(String text) {
        boolean progressSignal = containsAny(text, "到哪一步", "进度", "进展", "处理到", "办理到", "派单", "办结", "办完");
        boolean requestSignal = containsAny(text, "我的申请", "我的工单", "我申请", "我提交", "申请进度", "申请状态");
        boolean workOrderSignal = contains(text, "工单") || contains(text, "单号");

        if (!progressSignal && !requestSignal && !workOrderSignal) {
            return null;
        }
        // 也要单独命中"报修/请假 + 到哪一步"
        if (!(containsAny(text, "申请", "工单", "报修", "请假", "单")) && !progressSignal && !requestSignal) {
            return null;
        }

        String statusFilter = null;
        if (containsAny(text, "已完成", "办结", "办完", "完成", "完成情况", "结束")) {
            statusFilter = "COMPLETED";
        } else if (containsAny(text, "进行中", "处理中", "待处理", "待受理", "待派单", "没完成", "未完成")) {
            statusFilter = "IN_PROGRESS";
        }
        return AiIntentMatch.myRequests(statusFilter);
    }

    private AiIntentMatch classifyServiceAction(String text) {
        boolean actionVerb = containsAny(text, "我要", "我想", "需要", "办理", "申请", "提交", "怎么", "如何",
                "去哪", "哪里办", "在哪", "流程");
        if (!actionVerb) {
            return null;
        }

        if (containsAny(text, "公共区域", "教室报修", "公共报修")) {
            return AiIntentMatch.serviceAction("REPAIR-PUBLIC", "教室与公共区域报修");
        }
        for (Map.Entry<String, String[]> entry : SERVICE_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (contains(text, keyword)) {
                    return AiIntentMatch.serviceAction(entry.getKey(), keywordLabel(entry.getKey()));
                }
            }
        }
        return null;
    }

    private String keywordLabel(String itemCode) {
        return switch (itemCode) {
            case "AFFAIR-LEAVE" -> "请假申请";
            case "REPAIR-DORM" -> "宿舍报修";
            case "REPAIR-PUBLIC" -> "教室与公共区域报修";
            case "CERT-ENROLL" -> "在读证明申请";
            case "AFFAIR-VENUE" -> "活动场地申请";
            case "GUIDE-CONSULT" -> "校园事务咨询";
            default -> "服务办理";
        };
    }

    private static int shift(DayOfWeek day, int delta) {
        int shifted = day.getValue() + delta;
        return shifted > 7 ? shifted - 7 : shifted;
    }

    private static boolean contains(String text, String keyword) {
        return text.contains(keyword);
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}