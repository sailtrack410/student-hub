package com.studenthub.common.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务单号生成收敛：原先 workorder(WO-) 与 servicerequest(SR-) 各写一份「日期 + TMP 占位 + 插入后回填 ID」模式。
 * id 为 null 时生成 TMP 占位单号（插入前占位防唯一键冲突），插入成功后以真实 ID 回填。
 */
public final class OrderNoGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private OrderNoGenerator() {
    }

    /** prefix-yyyyMMdd-TMP（id 为 null）或 prefix-yyyyMMdd-id */
    public static String next(String prefix, Long id) {
        String date = LocalDate.now().format(DATE);
        return id == null ? prefix + "-" + date + "-TMP" : prefix + "-" + date + "-" + id;
    }
}
