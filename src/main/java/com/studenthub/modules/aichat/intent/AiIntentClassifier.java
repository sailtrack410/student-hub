package com.studenthub.modules.aichat.intent;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则优先的意图分类器：先用本地关键词/正则快速命中高频意图，
 * 未命中回退 FALLBACK 走普通 LLM 闲聊，避免额外分类调用。
 */
@Component
public class AiIntentClassifier {

    /** 周一~周日 中文别名 → ISO 1~7 */
    private static final Map<String, Integer> DAY_WORDS = new LinkedHashMap<>();

    private static final Map<String, String[]> SERVICE_KEYWORDS = new LinkedHashMap<>();

    /** "8月31日/8月31号/8.31/8/31/8-31"，可选年份 "2026年" 或 "2026-" 前缀 */
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(?:(\\d{4})\\s*[年/-])?\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*(?:日|号)"
                    + "|(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})");

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
        // 具体日期优先：今天/明天/后天/绝对日期（8月31日、8/31 …）
        LocalDate today = LocalDate.now();
        if (contains(text, "今天")) {
            return AiIntentMatch.scheduleDate(today);
        }
        if (contains(text, "昨天")) {
            return AiIntentMatch.scheduleDate(today.minusDays(1));
        }
        if (contains(text, "明天")) {
            return AiIntentMatch.scheduleDate(today.plusDays(1));
        }
        if (contains(text, "后天")) {
            return AiIntentMatch.scheduleDate(today.plusDays(2));
        }
        LocalDate absolute = parseAbsoluteDate(text, today);
        if (absolute != null) {
            return AiIntentMatch.scheduleDate(absolute);
        }
        for (Map.Entry<String, Integer> entry : DAY_WORDS.entrySet()) {
            if (contains(text, entry.getKey())) {
                return AiIntentMatch.scheduleDay(entry.getValue());
            }
        }
        // 只出现"课"但没有明确日期/星期 → 默认问本周
        return AiIntentMatch.scheduleWeek();
    }

    /** 解析 "8月31日/8月31号/8.31/8/31/8-31"，缺少年份时用当前年并折叠到最近学年 */
    private static LocalDate parseAbsoluteDate(String text, LocalDate today) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        while (matcher.find()) {
            LocalDate date = null;
            if (matcher.group(2) != null) {
                // 月/日 形式：8月31日、8月31号、2026年8月31日
                int month = Integer.parseInt(matcher.group(2));
                int dayOfMonth = Integer.parseInt(matcher.group(3));
                if (matcher.group(1) != null) {
                    date = safeDate(Integer.parseInt(matcher.group(1)), month, dayOfMonth);
                } else {
                    date = foldToRecent(today, month, dayOfMonth);
                }
            } else if (matcher.group(4) != null) {
                // 纯数字形式：8.31 / 8/31 / 8-31，排除 "1-8周" 这类周次表达式
                int month = Integer.parseInt(matcher.group(4));
                int dayOfMonth = Integer.parseInt(matcher.group(5));
                if (matcher.end() < text.length()) {
                    char next = text.charAt(matcher.end());
                    if (next == '周' || next == '节' || next == '次') {
                        continue;
                    }
                }
                date = foldToRecent(today, month, dayOfMonth);
            }
            if (date != null) {
                return date;
            }
        }
        return null;
    }

    private static LocalDate safeDate(int year, int month, int dayOfMonth) {
        if (month < 1 || month > 12 || dayOfMonth < 1 || dayOfMonth > 31) {
            return null;
        }
        try {
            return LocalDate.of(year, month, dayOfMonth);
        } catch (java.time.DateTimeException ignored) {
            return null;
        }
    }

    /** 把无年份的月/日折叠到最近学年：8~1月归第一学年，2~7月归第二学年，再取与今天相差最小的年份 */
    private static LocalDate foldToRecent(LocalDate today, int month, int dayOfMonth) {
        if (month < 1 || month > 12 || dayOfMonth < 1 || dayOfMonth > 31) {
            return null;
        }
        boolean firstTermTarget = (month >= 8 || month == 1);
        boolean firstTermToday = (today.getMonthValue() >= 8 || today.getMonthValue() == 1);
        int basisYear = firstTermTarget
                ? (firstTermToday ? today.getYear() : today.getYear() - 1)
                : (firstTermToday ? today.getYear() + 1 : today.getYear());
        LocalDate best = null;
        long bestDist = Long.MAX_VALUE;
        for (int year : new int[]{basisYear - 1, basisYear, basisYear + 1}) {
            LocalDate candidate = safeDate(year, month, dayOfMonth);
            if (candidate == null) {
                continue;
            }
            long dist = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(today, candidate));
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
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