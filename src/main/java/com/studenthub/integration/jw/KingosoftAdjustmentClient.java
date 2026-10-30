package com.studenthub.integration.jw;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.util.IntegrationExceptions;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.jwauth.config.JwProperties;
import com.studenthub.modules.schedule.entity.ScheduleAdjustmentEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 青果调课监控客户端（2026-10-18 实测校正）。
 *
 * <p>真实链路：查询壳 {@code /student/jxap.jxaptzxx.html} 提交表单到
 * {@code POST /student/jxap.jxaptzxx_rpt.jsp}，参数 {@code xh}=教务内部学号、
 * {@code xn}=学年起始年、{@code xq}=学期（0=第一学期 / 1=第二学期）、{@code xn1}=xn+1、{@code _xq}=xq。
 * 裸 GET 该 rpt 页返回「无效访问请求」，必须按此 POST。
 *
 * <p>报表结构（每学期一次返回全部公告，实测 28 条/112 个 table 块）：
 * <ul>
 *   <li>头块两行：{@code 调课时间：2026-06-09 09:37 | | 补课}、{@code 课程：[M05F2022]爬虫与数据可视化 | 上课班号：008}；</li>
 *   <li>明细块：表头 {@code 调课项目/调课前/调课后}，行如 {@code 周次 → 16}、{@code 节次 → 四[1-4节]}、
 *       {@code 地点 → 信-302…}、{@code 教师 → [编号]姓名}、{@code 授课方式 → 实验}，末行含调课原因/备注；</li>
 *   <li>班级块：{@code 上课班级构成：计科B2314 计科B2315}。</li>
 * </ul>
 * 原始块 HTML 存 raw_text（双写约定），解析失败整条跳过并告警，不阻断其余条目。
 */
@Slf4j
@Component
public class KingosoftAdjustmentClient {

    private static final String ADJUSTMENT_PATH = "/student/jxap.jxaptzxx_rpt.jsp";
    private static final String[] ACTION_TYPES = {"补课", "停课", "调课", "调休"};
    /** 教务页面大量使用 Unicode 空格（U+2002/U+2003 等），\\s 不覆盖，必须用 \\p{Z} */
    private static final java.util.regex.Pattern SPACES =
            java.util.regex.Pattern.compile("[\\s\\p{Z}]+");

    private final JwProperties properties;
    private final ObjectMapper objectMapper;

    public KingosoftAdjustmentClient(JwProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 用任一托管会话拉取并解析指定学期的全校调课公告。
     *
     * @param semesterCode 课表侧学期编码 {@code "2025,1"}（1=第一学期 / 2=第二学期），
     *                     同时作为落库归属；为空时按校历推算当前学期
     */
    public List<ScheduleAdjustmentEntity> fetchAdjustments(String cookieHeader, String studentNo,
                                                           String semesterCode) {
        return IntegrationExceptions.wrap(() -> {
            String code = semesterCode == null || semesterCode.isBlank()
                    ? calendarSemesterCode() : semesterCode.trim();
            int[] yearTerm = parseSemesterCode(code);
            int startYear = yearTerm[0];
            int scheduleTerm = yearTerm[1];
            // 调课页的学期编码与课表页不同：0=第一学期、1=第二学期
            int adjustmentXq = scheduleTerm == 2 ? 1 : 0;

            String form = "xh=" + URLEncoder.encode(studentNo == null ? "" : studentNo, StandardCharsets.UTF_8)
                    + "&xn=" + startYear
                    + "&xq=" + adjustmentXq
                    + "&xn1=" + (startYear + 1)
                    + "&_xq=" + adjustmentXq;

            HttpClient client = buildClient();
            HttpResponse<byte[]> response = client.send(
                    HttpRequest.newBuilder(URI.create(properties.getJwBaseUrl() + ADJUSTMENT_PATH))
                            .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                            .header("Cookie", cookieHeader)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header("Referer", properties.getJwBaseUrl() + "/student/jxap.jxaptzxx.html")
                            .POST(HttpRequest.BodyPublishers.ofString(form))
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("调课监控页 HTTP {}", response.statusCode());
                throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
            }
            String html = decodeBody(response.body());
            if (html.contains("凭证已失效")) {
                throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
            }
            return parseAdjustments(html, code);
        }, ErrorCode.JW_SESSION_EXPIRED, "拉取调课通知异常");
    }

    /**
     * 解析调课报表页：按块序分组（头块 → 明细块 → 班级块），块类型用去空白后的文本判定。
     */
    List<ScheduleAdjustmentEntity> parseAdjustments(String html, String semesterCode) {
        Document document = Jsoup.parse(html);
        List<ScheduleAdjustmentEntity> result = new ArrayList<>();
        Header current = null;
        List<Map<String, String>> details = new ArrayList<>();
        List<String> targetClasses = new ArrayList<>();
        StringBuilder raw = new StringBuilder();

        for (Element table : document.select("table")) {
            String flat = squeeze(table.text());
            if (isHeaderBlock(flat)) {
                if (current != null) {
                    flush(result, current, details, targetClasses, raw, semesterCode);
                }
                current = parseHeader(table);
                details = new ArrayList<>();
                targetClasses = new ArrayList<>();
                raw = new StringBuilder(table.outerHtml());
            } else if (current != null) {
                raw.append(table.outerHtml());
                if (flat.contains("调课项目") || flat.contains("调课前")) {
                    parseDetails(table, details);
                } else if (flat.contains("上课班级构成")) {
                    targetClasses.addAll(parseClasses(table.text()));
                }
            }
        }
        if (current != null) {
            flush(result, current, details, targetClasses, raw, semesterCode);
        }
        log.info("调课通知解析完成, semesterCode={}, 条数={}", semesterCode, result.size());
        return result;
    }

    private boolean isHeaderBlock(String flat) {
        return flat.contains("调课时间") || flat.contains("停课时间") || flat.contains("补课时间")
                || (flat.contains("课程") && flat.contains("上课班号") && flat.contains("时间"));
    }

    private Header parseHeader(Element table) {
        Header header = new Header();
        for (Element row : table.select("tr")) {
            Elements cells = row.select("th, td");
            if (cells.isEmpty()) {
                continue;
            }
            String joined = squeeze(row.text());
            if (joined.contains("时间")) {
                for (Element cell : cells) {
                    String raw = clean(cell.text());
                    String compact = squeeze(raw);
                    if (compact.contains("调课时间") || compact.contains("停课时间") || compact.contains("补课时间")) {
                        header.noticeTime = valueAfterColon(raw);
                    }
                    String action = matchAction(raw);
                    if (action != null) {
                        header.actionType = action;
                    }
                }
            }
            for (Element cell : cells) {
                String raw = clean(cell.text());
                String compact = squeeze(raw);
                if (compact.startsWith("课程")) {
                    String value = valueAfterColon(raw);
                    parsesCourse(header, value);
                } else if (compact.startsWith("上课班号") || compact.startsWith("教学班号")) {
                    header.classNo = valueAfterColon(raw);
                }
            }
        }
        return header;
    }

    private void parseDetails(Element table, List<Map<String, String>> details) {
        for (Element row : table.select("tr")) {
            Elements cells = row.select("th, td");
            String joined = clean(row.text());
            if (joined.contains("调课原因") || joined.contains("备注")) {
                String reason = extractBetween(joined, "调课原因", "备注");
                String remark = extractAfter(joined, "备注");
                if (reason != null && !reason.isBlank()) {
                    details.add(detail("调课原因", "", reason));
                }
                if (remark != null && !remark.isBlank()) {
                    details.add(detail("备注", "", remark));
                }
                continue;
            }
            if (cells.size() < 4) {
                continue;
            }
            String label = squeeze(clean(cells.get(1).text()));
            if (label.isEmpty() || label.equals("调课项目")) {
                continue;
            }
            String before = clean(cells.get(2).text());
            String after = clean(cells.get(3).text());
            if (!label.contains("周") && !label.contains("节") && !label.contains("地点")
                    && !label.contains("教师") && !label.contains("授课")) {
                continue;
            }
            details.add(detail(label, before, after));
        }
    }

    private Map<String, String> detail(String item, String before, String after) {
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("item", item);
        detail.put("before", before == null ? "" : before);
        detail.put("after", after == null ? "" : after);
        return detail;
    }

    private List<String> parseClasses(String text) {
        String value = valueAfterColon(clean(text));
        if (value == null || value.isBlank() || "无".equals(value.trim())) {
            return List.of();
        }
        List<String> classes = new ArrayList<>();
        for (String part : value.split("[、，,;；\\s/]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                classes.add(trimmed);
            }
        }
        return classes;
    }

    private void flush(List<ScheduleAdjustmentEntity> result, Header header,
                       List<Map<String, String>> details, List<String> targetClasses,
                       StringBuilder raw, String semesterCode) {
        if (header.courseName == null || header.courseName.isBlank()) {
            log.debug("跳过缺少课程名称的调课块, noticeTime={}", header.noticeTime);
            return;
        }
        ScheduleAdjustmentEntity entity = new ScheduleAdjustmentEntity();
        entity.setSemesterCode(semesterCode);
        entity.setNoticeTime(orDefault(header.noticeTime, ""));
        entity.setActionType(orDefault(header.actionType, "调课"));
        entity.setCourseName(header.courseName);
        entity.setCourseCode(orDefault(header.courseCode, ""));
        entity.setClassNo(header.classNo);
        entity.setTargetClasses(writeJson(targetClasses));
        entity.setDetailJson(details.isEmpty() ? null : writeJson(details));
        entity.setSummary(buildSummary(entity, details));
        entity.setRawText(truncate(raw.toString()));
        result.add(entity);
    }

    private void parsesCourse(Header header, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("[")) {
            int close = trimmed.indexOf(']');
            if (close > 1) {
                header.courseCode = trimmed.substring(1, close).trim();
                header.courseName = trimmed.substring(close + 1).trim();
                return;
            }
        }
        header.courseName = trimmed;
    }

    private String matchAction(String raw) {
        String compact = squeeze(raw);
        for (String action : ACTION_TYPES) {
            if (compact.equals(action) || compact.endsWith("：" + action)) {
                return action;
            }
        }
        return null;
    }

    private String buildSummary(ScheduleAdjustmentEntity entity, List<Map<String, String>> details) {
        StringBuilder summary = new StringBuilder("[").append(entity.getActionType()).append("] ")
                .append(entity.getCourseName());
        List<String> parts = new ArrayList<>();
        for (Map<String, String> detail : details) {
            String item = detail.get("item");
            if ("调课原因".equals(item) || "备注".equals(item)) {
                continue;
            }
            String before = detail.get("before");
            String after = detail.get("after");
            StringBuilder part = new StringBuilder(item).append(" ");
            if (before != null && !before.isBlank()) {
                part.append(before).append(" → ");
            }
            part.append(after);
            parts.add(part.toString());
        }
        if (!parts.isEmpty()) {
            summary.append("：").append(String.join("；", parts));
        }
        if (entity.getClassNo() != null && !entity.getClassNo().isBlank()) {
            summary.append("（班号 ").append(entity.getClassNo()).append("）");
        }
        return summary.length() > 480 ? summary.substring(0, 480) : summary.toString();
    }

    /** 课表侧学期编码 "2025,1" → [2025, 1]；非法/空按校历 */
    private int[] parseSemesterCode(String code) {
        try {
            String[] parts = code.split(",");
            int year = Integer.parseInt(parts[0].trim());
            int term = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
            return new int[]{year, term == 2 ? 2 : 1};
        } catch (Exception exception) {
            String fallback = calendarSemesterCode();
            String[] parts = fallback.split(",");
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        }
    }

    /** 校历推算当前学期（课表约定）：8 月~次年 1 月第一学期，2~7 月第二学期 */
    private String calendarSemesterCode() {
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        int startYear = month >= 8 ? today.getYear() : today.getYear() - 1;
        int term = (month >= 8 || month == 1) ? 1 : 2;
        return startYear + "," + term;
    }

    /** 取冒号后的值（全角/半角冒号都支持） */
    private String valueAfterColon(String raw) {
        if (raw == null) {
            return null;
        }
        int full = raw.indexOf('：');
        int half = raw.indexOf(':');
        int index = full >= 0 ? full : half;
        return index < 0 ? raw.trim() : raw.substring(index + 1).trim();
    }

    private String extractAfter(String raw, String label) {
        if (raw == null) {
            return null;
        }
        int index = raw.indexOf(label);
        if (index < 0) {
            return null;
        }
        return valueAfterColon(raw.substring(index + label.length()));
    }

    /** 取 label 冒号后到下一个标签前的值（调课原因/备注同格时用） */
    private String extractBetween(String raw, String startLabel, String endLabel) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf(startLabel);
        if (start < 0) {
            return null;
        }
        String rest = raw.substring(start + startLabel.length());
        int end = rest.indexOf(endLabel);
        if (end >= 0) {
            rest = rest.substring(0, end);
        }
        return valueAfterColon(rest);
    }

    /** 去掉所有空白（含全角/Unicode 空格），用于标签匹配 */
    private String squeeze(String text) {
        return text == null ? "" : SPACES.matcher(text).replaceAll("");
    }

    /** 折叠空白但保留单空格（用于日期/位置等值） */
    private String clean(String text) {
        return text == null ? "" : SPACES.matcher(text).replaceAll(" ").trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            log.warn("调课明细序列化失败", exception);
            return null;
        }
    }

    private String truncate(String raw) {
        return raw.length() > 20000 ? raw.substring(0, 20000) : raw;
    }

    private String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String decodeBody(byte[] body) {
        String utf8 = new String(body, StandardCharsets.UTF_8);
        String gbk = new String(body, java.nio.charset.Charset.forName("GBK"));
        return utf8.indexOf('\uFFFD') <= gbk.indexOf('\uFFFD') ? utf8 : gbk;
    }

    private HttpClient buildClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    private static class Header {
        private String noticeTime;
        private String actionType;
        private String courseCode;
        private String courseName;
        private String classNo;
    }
}
