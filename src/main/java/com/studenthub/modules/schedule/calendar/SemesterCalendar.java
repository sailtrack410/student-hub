package com.studenthub.modules.schedule.calendar;

import com.studenthub.modules.schedule.entity.ScheduleCourseEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 学期校历与周次换算工具。
 *
 * <p>把“课表所在的日历位置”固化为每个学期的第一周周一 + 总周数，
 * 课表页表头与 AI 按日期查询共用，避免前后端各自推算导致结果不一致。
 * 开学日仍按校历约定估算：第一学期≈9月1日、第二学期≈次年3月1日（周末顺延到周一）。
 */
public final class SemesterCalendar {

    private static final Pattern CODE_PARTS = Pattern.compile("^(\\d{4})\\s*[,，-]\\s*(\\d)$");
    private static final Pattern NAME_PARTS = Pattern.compile("(\\d{4})[-–—]\\d{4}学年第([一二三四五六]+|\\d)学期");
    private static final Pattern WEEK_RANGE = Pattern.compile("(\\d+)\\s*[-–~]\\s*(\\d+)");
    private static final Pattern WEEK_SINGLE = Pattern.compile("\\d+");

    private SemesterCalendar() {
    }

    /** 学期编码/名称 → 第一周周一；无法解析时返回 null */
    public static LocalDate firstMonday(String semesterCode, String semesterName) {
        int[] parts = semesterParts(semesterCode, semesterName);
        if (parts == null) {
            return null;
        }
        int year = parts[0];
        int term = parts[1];
        // 与教务周次口径一致：第一学期≈9月1日、第二学期≈次年3月1日；
        // with(MONDAY) 取锚点日期所在自然周的周一（教务周次按该周记为第1周）。
        // 锚点定在 8 月/2 月会把“本周”前移约 4 周落到空周上，课程看起来全部消失。
        int month = term == 1 ? 9 : 3;
        int anchorYear = term == 1 ? year : year + 1;
        LocalDate anchor = LocalDate.of(anchorYear, month, 1);
        DayOfWeek dow = anchor.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY) {
            anchor = anchor.plusDays(2);
        } else if (dow == DayOfWeek.SUNDAY) {
            anchor = anchor.plusDays(1);
        }
        return anchor.with(DayOfWeek.MONDAY);
    }

    /** 断课表学期编码：8~12月与1月为第一学期，2~7月为第二学期（与导入默认学期同规则） */
    public static String semesterCodeForDate(LocalDate date) {
        int month = date.getMonthValue();
        int startYear = month >= 8 ? date.getYear() : date.getYear() - 1;
        int term = (month >= 8 || month == 1) ? 1 : 2;
        return startYear + "," + term;
    }

    /** date 所在周的周一 */
    public static LocalDate mondayOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    /** date 在给定学期里的周次（1 起，可能为 0/负/超界，由调用方判断） */
    public static int weekOf(LocalDate date, LocalDate firstMonday) {
        return (int) ChronoUnit.DAYS.between(firstMonday, mondayOf(date)) / 7 + 1;
    }

    /** 判断课程在第 week 周是否上课（解析周次表达式并叠加单双周过滤） */
    public static boolean activeAtWeek(String weeks, Integer weekParity, int week) {
        if (weeks == null || weeks.isBlank()) {
            return false;
        }
        String normalized = weeks.replace("周", "").replace(" ", "");
        for (String part : normalized.split("[,，;；]+")) {
            int start;
            int end;
            Matcher range = WEEK_RANGE.matcher(part.trim());
            if (range.find()) {
                start = Integer.parseInt(range.group(1));
                end = Integer.parseInt(range.group(2));
            } else {
                Matcher single = WEEK_SINGLE.matcher(part.trim());
                if (!single.find()) {
                    continue;
                }
                start = Integer.parseInt(single.group());
                end = start;
            }
            if (end < start) {
                int tmp = start;
                start = end;
                end = tmp;
            }
            for (int w = start; w <= end; w++) {
                if (weekParity != null && weekParity == 1 && w % 2 == 0) {
                    continue;
                }
                if (weekParity != null && weekParity == 2 && w % 2 == 1) {
                    continue;
                }
                if (w == week) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 学期课程最大周次（无课程返回 1） */
    public static int maxWeekOf(List<ScheduleCourseEntity> courses) {
        int max = 1;
        if (courses != null) {
            for (ScheduleCourseEntity course : courses) {
                int week = maxWeekIn(course.getWeeks());
                if (week > max) {
                    max = week;
                }
            }
        }
        return max;
    }

    /** 单条周次表达式的最大周次 */
    public static int maxWeekIn(String weeks) {
        if (weeks == null || weeks.isBlank()) {
            return 0;
        }
        int max = 0;
        String normalized = weeks.replace("周", "").replace(" ", "");
        for (String part : normalized.split("[,，;；]+")) {
            java.util.regex.Matcher m = Pattern.compile("(\\d+)").matcher(part);
            while (m.find()) {
                int value = Integer.parseInt(m.group());
                if (value > max) {
                    max = value;
                }
            }
        }
        return max;
    }

    private static int[] semesterParts(String semesterCode, String semesterName) {
        // 学期名称优先：教务编码里的 term（2025,1）有时与真实“第几学期”语义相反，
        // 名称“第二学期/第一学期”才是确定开学月份的可靠来源。
        if (semesterName != null) {
            Matcher nameMatcher = NAME_PARTS.matcher(semesterName);
            if (nameMatcher.find()) {
                int year = Integer.parseInt(nameMatcher.group(1));
                String termText = nameMatcher.group(2);
                int term = switch (termText) {
                    case "一" -> 1;
                    case "二" -> 2;
                    case "三" -> 3;
                    case "四" -> 4;
                    case "五" -> 5;
                    case "六" -> 6;
                    default -> Integer.parseInt(termText);
                };
                return new int[]{year, term};
            }
        }
        if (semesterCode != null) {
            Matcher codeMatcher = CODE_PARTS.matcher(semesterCode.trim());
            if (codeMatcher.find()) {
                return new int[]{Integer.parseInt(codeMatcher.group(1)), Integer.parseInt(codeMatcher.group(2))};
            }
        }
        return null;
    }
}