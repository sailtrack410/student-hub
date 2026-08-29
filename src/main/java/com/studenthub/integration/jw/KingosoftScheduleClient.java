package com.studenthub.integration.jw;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.jwauth.config.JwProperties;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 青果教务课表客户端。
 *
 * <p>真实数据链路（绵阳城市学院青果，页面为 GBK/UTF-8 混合编码）：
 * <ol>
 *   <li>壳页 {@code /frame/desk/showLessonSchedule4User.action}：含当前学年学期 js 变量；</li>
 *   <li>数据页 POST {@code /frame/desk/showLessonScheduleDetail.action}，参数 {@code xnxq=学年,学期}；
 *       返回“周视图”表格（行=节次，列=周一到周日），单元格为 {@code 课程名 教师(周次 地点)} 多行。</li>
 * </ol>
 */
@Slf4j
@Component
public class KingosoftScheduleClient {

    /** 壳页里的当前学年/学期变量 */
    private static final Pattern DQ_XNXQ = Pattern.compile("var\\s+dqxn\\s*=\\s*\"(\\d+)\";\\s*var\\s+dqxq\\s*=\\s*\"(\\d+)\";");
    /** 数据页标题里的学期名 */
    private static final Pattern SEMESTER_TITLE = Pattern.compile("(\\d{4}-\\d{4}学年.+?)教学安排表");

    private final JwProperties properties;

    public KingosoftScheduleClient(JwProperties properties) {
        this.properties = properties;
    }

    /**
     * 拉取个人课表（当前学年学期）。semesterCode 形如 {@code 2025,1}，为空时取当前学期。
     */
    public JwSchedulePage fetchSchedulePage(String cookieHeader, String semesterCode) {
        try {
            HttpClient client = buildClient();

            // 1. 壳页：拿当前学年学期并解析要用的 xnxq
            String shell = getString(client, cookieHeader,
                    "/frame/desk/showLessonSchedule4User.action");
            Matcher matcher = DQ_XNXQ.matcher(shell);
            if (!matcher.find()) {
                log.info("课表壳页未解析到当前学年学期，判定会话失效");
                throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
            }
            String currentXnxq = matcher.group(1) + "," + matcher.group(2);
            String chosen = semesterCode == null || semesterCode.isBlank() ? currentXnxq : semesterCode;

            // 2. 数据页：POST 周视图课表
            HttpResponse<byte[]> response = client.send(
                    HttpRequest.newBuilder(URI.create(properties.getJwBaseUrl()
                                    + "/frame/desk/showLessonScheduleDetail.action"))
                            .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                            .header("Cookie", cookieHeader)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString("weeks=&xnxq=" + chosen))
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.info("教务课表数据页返回非 200, status={}", response.statusCode());
                throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
            }

            byte[] body = response.body();
            String page = decodeBody(body);
            Document document = Jsoup.parse(page);

            String semesterName = currentXnxq;
            Matcher titleMatcher = SEMESTER_TITLE.matcher(page);
            if (titleMatcher.find()) {
                semesterName = titleMatcher.group(1);
            }

            List<JwCourseEntry> courses = parseGrid(document);
            return new JwSchedulePage(chosen, semesterName, courses);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("拉取教务课表异常", exception);
            throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
        }
    }

    /**
     * 解析周视图表格：行=节次（含 rowspan 时段），列=周一到周日。
     * {@code 课程名 教师(周次 地点)} 按 &lt;br/&gt; 拆多行。
     */
    private List<JwCourseEntry> parseGrid(Document document) {
        List<JwCourseEntry> entries = new ArrayList<>();
        Element tbody = document.selectFirst("tbody");
        if (tbody == null) {
            return entries;
        }
        for (Element row : tbody.select("tr")) {
            Elements tds = row.select("td");
            int sectionIndex;
            int dayStart;
            if (tds.size() >= 9) {
                // 首行含“时段”cell（上午/下午/晚上）
                sectionIndex = 1;
                dayStart = 2;
            } else if (tds.size() >= 8) {
                // 时段 cell 被 rowspan 省略
                sectionIndex = 0;
                dayStart = 1;
            } else {
                continue;
            }
            String sectionText = tds.get(sectionIndex).text().trim();
            Matcher sectionMatcher = Pattern.compile("^(\\d+)").matcher(sectionText);
            if (!sectionMatcher.find()) {
                continue;
            }
            int section = Integer.parseInt(sectionMatcher.group(1));
            for (int day = 0; day < 7 && dayStart + day < tds.size(); day++) {
                Element cell = tds.get(dayStart + day);
                for (String line : splitLines(cell)) {
                    JwCourseEntry entry = parseCourseLine(line, day + 1, section);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            }
        }
        return entries;
    }

    /** 拆出单元格里的课程行，去掉所有标签与空白 */
    private List<String> splitLines(Element cell) {
        String html = cell.html();
        html = html.replaceAll("(?i)<br\\s*/?>", "\n");
        List<String> lines = new ArrayList<>();
        for (String raw : html.split("\n")) {
            String line = Jsoup.parse(raw).text().trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** 解析单行：{@code 课程名 教师(周次 地点)} */
    private JwCourseEntry parseCourseLine(String line, int day, int section) {
        int open = line.indexOf('(');
        int close = line.lastIndexOf(')');
        if (open <= 0 || close <= open) {
            log.debug("跳过无法解析的课程行: {}", line);
            return null;
        }
        String left = line.substring(0, open).trim();
        String inside = line.substring(open + 1, close);

        int lastSpace = left.lastIndexOf(' ');
        if (lastSpace <= 0) {
            log.debug("跳过无法拆分教师/课程: {}", line);
            return null;
        }
        String courseName = left.substring(0, lastSpace).trim();
        String teacher = left.substring(lastSpace + 1).trim();

        int weekSpace = inside.indexOf(' ');
        if (weekSpace <= 0) {
            log.debug("跳过无法拆分周次/地点: {}", line);
            return null;
        }
        String weeks = inside.substring(0, weekSpace).trim();
        String location = inside.substring(weekSpace + 1).trim();

        int parity = 0;
        if (weeks.endsWith("双周") || weeks.endsWith("双")) {
            parity = 2;
            weeks = weeks.replace("双周", "").replace("双", "");
        } else if (weeks.endsWith("单周") || weeks.endsWith("单")) {
            parity = 1;
            weeks = weeks.replace("单周", "").replace("单", "");
        }

        return new JwCourseEntry(null, null, courseName, teacher,
                null, location, day, section, section, weeks, parity, line);
    }

    private String getString(HttpClient client, String cookieHeader, String path) throws Exception {
        HttpResponse<byte[]> response = client.send(
                HttpRequest.newBuilder(URI.create(properties.getJwBaseUrl() + path))
                        .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                        .header("Cookie", cookieHeader)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
        }
        return decodeBody(response.body());
    }

    private String decodeBody(byte[] body) {
        String utf8 = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        String gbk = new String(body, java.nio.charset.Charset.forName("GBK"));
        // 课表页为 GBK，直接用 GBK；若含 UTF-8 特征（替换符少）则用 UTF-8
        return utf8.indexOf('\uFFFD') <= gbk.indexOf('\uFFFD') ? utf8 : gbk;
    }

    private HttpClient buildClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    /** 课表单条上课安排（一门课可拆多行为多个周次/地点） */
    public record JwCourseEntry(String classCode, String courseCode, String courseName, String teacher,
                                String nature, String location, int dayOfWeek, int startSection, int endSection,
                                String weeks, int weekParity, String rawText) {
    }

    public record JwSchedulePage(String semesterCode, String semesterName, List<JwCourseEntry> courses) {
    }
}