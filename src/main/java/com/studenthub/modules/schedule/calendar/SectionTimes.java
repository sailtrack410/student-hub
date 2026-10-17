package com.studenthub.modules.schedule.calendar;

import java.time.LocalTime;
import java.util.List;

/**
 * 节次→时间映射（青果作息，唯一事实源）。
 * 上午 08:30 起 4 节、下午 14:10 起 4 节、晚上 18:50 起 3 节；每节 45 分钟，
 * 课间 10 分钟（第 4/8 节后为午休/晚休长间隔，直接跳到下一时段起点）。
 * 服务端展开周视图与 next-course 共用；客户端不再各自维护映射。
 */
public final class SectionTimes {

    /** 每节起始时间（1-11 节） */
    private static final LocalTime[] STARTS = {
            LocalTime.of(8, 30), LocalTime.of(9, 25), LocalTime.of(10, 20), LocalTime.of(11, 15),
            LocalTime.of(14, 10), LocalTime.of(15, 5), LocalTime.of(16, 0), LocalTime.of(16, 55),
            LocalTime.of(18, 50), LocalTime.of(19, 45), LocalTime.of(20, 40)
    };

    /** 每节结束时间 */
    private static final LocalTime[] ENDS = {
            LocalTime.of(9, 15), LocalTime.of(10, 10), LocalTime.of(11, 5), LocalTime.of(12, 0),
            LocalTime.of(14, 55), LocalTime.of(15, 50), LocalTime.of(16, 45), LocalTime.of(17, 40),
            LocalTime.of(19, 35), LocalTime.of(20, 30), LocalTime.of(21, 25)
    };

    private static final List<String> TIME_RANGES = buildRanges();

    private SectionTimes() {
    }

    /** 节次时间段，如 startSection=1, endSection=2 → "08:30-10:10"；越界节次返回 null */
    public static String rangeOf(int startSection, int endSection) {
        if (startSection < 1 || endSection > STARTS.length || startSection > endSection) {
            return null;
        }
        return STARTS[startSection - 1] + "-" + ENDS[endSection - 1];
    }

    /** 当前时间落在哪一节（1 起，0 表示未在任何节内，如课间/午休/晚休） */
    public static int sectionAt(LocalTime time) {
        for (int i = 0; i < STARTS.length; i++) {
            if (!time.isBefore(STARTS[i]) && !time.isAfter(ENDS[i])) {
                return i + 1;
            }
        }
        return 0;
    }

    public static List<String> allRanges() {
        return TIME_RANGES;
    }

    private static List<String> buildRanges() {
        List<String> ranges = new java.util.ArrayList<>();
        for (int i = 0; i < STARTS.length; i++) {
            ranges.add(STARTS[i] + "-" + ENDS[i]);
        }
        return List.copyOf(ranges);
    }
}
