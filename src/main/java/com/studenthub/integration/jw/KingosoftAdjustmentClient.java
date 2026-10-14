package com.studenthub.integration.jw;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.util.IntegrationExceptions;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.jwauth.config.JwProperties;
import com.studenthub.modules.schedule.entity.ScheduleAdjustmentEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 青果调课监控客户端：POST /student/jxap.jxaptzxx_rpt.jsp（调课通知报表页）。
 *
 * <p>页面为每条公告一个表格块的报表：字段名→值的行式表格 + 变更明细（周次/节次/地点/教师/授课方式
 * 的 before-after）+ 受影响班级。解析结果对齐同校同平台竞品（绵城助手）的 /jwgl/adjustments
 * 结构（逆向样本 api_samples/jwgl_adjustments.json），原始 HTML 片段按双写约定保留 raw_text。
 * 上游页面形态以 DevJwProbe 实测为准；解析失败时整条跳过并告警，不阻断其余条目。
 */
@Slf4j
@Component
public class KingosoftAdjustmentClient {

    private static final String ADJUSTMENT_PATH = "/student/jxap.jxaptzxx_rpt.jsp";

    private final JwProperties properties;
    private final ObjectMapper objectMapper;

    public KingosoftAdjustmentClient(JwProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 用任一托管会话拉取并解析全校调课公告。semesterCode 用于落库归属；
     * 公告本身不分页（青果报表一次给出本学期全部），返回按页面顺序。
     */
    public List<ScheduleAdjustmentEntity> fetchAdjustments(String cookieHeader, String semesterCode) {
        return IntegrationExceptions.wrap(() -> {
            HttpClient client = buildClient();
            HttpResponse<byte[]> response = client.send(
                    HttpRequest.newBuilder(URI.create(properties.getJwBaseUrl() + ADJUSTMENT_PATH))
                            .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                            .header("Cookie", cookieHeader)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("调课监控页 HTTP {}", response.statusCode());
                throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
            }
            String html = decodeBody(response.body());
            if (html.contains("凭证已失效")) {
                throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
            }
            return parseAdjustments(html, semesterCode);
        }, ErrorCode.JW_SESSION_EXPIRED, "拉取调课通知异常");
    }

    /**
     * 解析调课报表页。青果 jxap 报表的结构是：每条公告一个块，块内为
     * 「字段名/值」两列行 + 可选的变更明细表（项目/变更前/变更后三列）+ 受影响班级行。
     * 解析器只依赖这两类表格结构，不依赖具体文案；字段名做包含匹配（"周 次"/"周次"）。
     */
    List<ScheduleAdjustmentEntity> parseAdjustments(String html, String semesterCode) {
        Document document = Jsoup.parse(html);
        List<ScheduleAdjustmentEntity> result = new ArrayList<>();
        for (Element block : document.select("table")) {
            ScheduleAdjustmentEntity entity = parseBlock(block, html, semesterCode);
            if (entity != null) {
                result.add(entity);
            }
        }
        log.info("调课通知解析完成, semesterCode={}, 条数={}", semesterCode, result.size());
        return result;
    }

    private ScheduleAdjustmentEntity parseBlock(Element block, String fullHtml, String semesterCode) {
        Map<String, String> fields = new LinkedHashMap<>();
        List<Map<String, String>> details = new ArrayList<>();
        // 遍历块内所有行：两列行归字段表，三列表头（含"变更前"或"前"）的表归明细
        for (Element row : block.select("tr")) {
            Elements cells = row.select("th, td");
            if (cells.size() >= 3 && isDetailHeaderRow(cells)) {
                continue;
            }
            if (cells.size() >= 3 && !details.isEmpty() || cells.size() >= 3 && looksLikeDetailRow(cells)) {
                Map<String, String> detail = new LinkedHashMap<>();
                detail.put("item", text(cells.get(0)));
                detail.put("before", text(cells.get(1)));
                detail.put("after", text(cells.get(2)));
                if (!detail.get("item").isBlank()) {
                    details.add(detail);
                }
                continue;
            }
            if (cells.size() == 2) {
                fields.putIfAbsent(text(cells.get(0)), text(cells.get(1)));
            }
        }
        if (fields.isEmpty()) {
            return null;
        }
        String courseName = findValue(fields, "课程名称", "课程");
        String courseCode = findValue(fields, "课程代码", "课程编号");
        if (courseName == null || courseName.isBlank()) {
            return null;
        }
        ScheduleAdjustmentEntity entity = new ScheduleAdjustmentEntity();
        entity.setSemesterCode(semesterCode);
        entity.setNoticeTime(orDefault(findValue(fields, "发布时间", "通知时间", "时间"), ""));
        entity.setActionType(orDefault(findValue(fields, "变更类型", "类型", "调课类型"), "调课"));
        entity.setCourseName(courseName);
        entity.setCourseCode(orDefault(courseCode, ""));
        entity.setClassNo(findValue(fields, "教学班号", "班号", "班级序号"));
        entity.setTargetClasses(writeJson(classesOf(findValue(fields, "面向班级", "涉及班级", "受影响班级"))));
        entity.setDetailJson(details.isEmpty() ? null : writeJson(details));
        entity.setSummary(buildSummary(entity, details));
        entity.setRawText(truncate(block.outerHtml()));
        return entity;
    }

    private boolean isDetailHeaderRow(Elements cells) {
        String first = text(cells.get(0));
        return first.contains("项目") || first.contains("变更项") || first.contains("内容");
    }

    private boolean looksLikeDetailRow(Elements cells) {
        String first = text(cells.get(0));
        return first.contains("周") || first.contains("节") || first.contains("地点")
                || first.contains("教 师") || first.contains("教师") || first.contains("授课方式");
    }

    private String findValue(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (entry.getKey().contains(key)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /** 面向班级：值可能为 "计科B2314、计科B2315" / 顿号逗号空格分隔的一串 */
    private List<String> classesOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> classes = new ArrayList<>();
        for (String part : raw.split("[、，,;；\\s]+")) {
            if (!part.isBlank()) {
                classes.add(part);
            }
        }
        return classes;
    }

    private String buildSummary(ScheduleAdjustmentEntity entity, List<Map<String, String>> details) {
        StringBuilder summary = new StringBuilder("[").append(entity.getActionType()).append("] ")
                .append(entity.getCourseName());
        if (!details.isEmpty()) {
            summary.append(": ");
            for (Map<String, String> detail : details) {
                summary.append(detail.get("item")).append(" ")
                        .append(detail.get("before").isBlank() ? "" : detail.get("before") + " → ")
                        .append(detail.get("after")).append("；");
            }
        }
        return summary.length() > 480 ? summary.substring(0, 480) : summary.toString();
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

    private String text(Element cell) {
        return cell.text().replaceAll("\\s+", " ").trim();
    }

    private String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String decodeBody(byte[] body) {
        String utf8 = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        String gbk = new String(body, java.nio.charset.Charset.forName("GBK"));
        return utf8.indexOf('\uFFFD') <= gbk.indexOf('\uFFFD') ? utf8 : gbk;
    }

    private HttpClient buildClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }
}
