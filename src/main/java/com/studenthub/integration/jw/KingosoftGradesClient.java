package com.studenthub.integration.jw;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.util.IntegrationExceptions;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.jwauth.config.JwProperties;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 青果教务「学业成绩」客户端。
 *
 * <p>真实数据链路（绵阳城市学院青果，G_SCHOOL_CODE=14045）：
 * <ol>
 *   <li>查询壳 {@code GET /student/xscj.stuckcj.jsp}：校验会话，页面 hidden 字段带 rxnj/nj；</li>
 *   <li>数据页 {@code POST /student/xscj.stuckcj_data.jsp}：GBK 表格，列
 *       序号/课程/学分/总学时/类别/修读性质/考核方式/成绩/获得学分/绩点/学分绩点/备注；
 *       每学期一个「学年学期：」标题行，表尾为合计行（课程数/学分/获得学分/绩点/平均成绩/加权平均）。</li>
 * </ol>
 * 参数语义实测：sjxz1=入学以来、sjxz2=学年、sjxz3=学期；ysyx=yscj 原始成绩 / yxcj 有效成绩；
 * 有效成绩会过滤补考原始记录（本账号 18 门 vs 原始 19 门），与竞品 totalCourses=18 一致。
 */
@Slf4j
@Component
public class KingosoftGradesClient {

    private static final Pattern RNJ = Pattern.compile("id=\"rxnj\"[^>]*value=\"([^\"]*)\"");
    private static final Pattern NJ = Pattern.compile("id=\"nj\"[^>]*value=\"([^\"]*)\"");
    private static final Pattern COURSE_CODE = Pattern.compile("^\\[([^\\]]+)\\](.*)$");

    private final JwProperties properties;

    public KingosoftGradesClient(JwProperties properties) {
        this.properties = properties;
    }

    /**
     * 拉取学业成绩。
     *
     * @param range     ALL 入学以来 / YEAR 学年 / TERM 学期（缺省 ALL）
     * @param scoreType EFFECTIVE 有效成绩 / RAW 原始成绩（缺省 EFFECTIVE）
     * @param xn        学年起始年（如 2025），YEAR/TERM 模式下使用
     * @param xq        学期（学期模式下使用，教务页面下拉值）
     */
    public JwGradeReport fetchGrades(String cookieHeader, String range, String scoreType, String xn, String xq) {
        return IntegrationExceptions.wrap(() -> {
            HttpClient client = buildClient();

            // 1. 查询壳：拿会话校验与 rxnj/nj 默认值
            String shell = getString(client, cookieHeader, "/student/xscj.stuckcj.jsp");
            if (shell.length() < 1000 || shell.contains("凭证已失效")) {
                log.info("学业成绩壳页异常，判定会话失效, length={}", shell.length());
                throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
            }
            String rxnj = firstGroup(RNJ, shell);
            String nj = firstGroup(NJ, shell);

            String sjxz = switch (normalizeRange(range)) {
                case "YEAR" -> "sjxz2";
                case "TERM" -> "sjxz3";
                default -> "sjxz1";
            };
            String ysyx = "RAW".equals(normalizeScoreType(scoreType)) ? "yscj" : "yxcj";

            String form = "sjxz=" + sjxz
                    + "&ysyx=" + ysyx
                    + "&zx=1&fx=1&wz=0"
                    + "&rxnj=" + (rxnj == null ? "" : rxnj)
                    + "&nj=" + (nj == null ? "" : nj);
            if (xn != null && !xn.isBlank()) {
                form += "&xn=" + xn.trim();
                try {
                    form += "&xn1=" + (Integer.parseInt(xn.trim()) + 1);
                } catch (NumberFormatException ignored) {
                    // 非法学年交给教务侧处理，不在此中断
                }
            }
            if ("TERM".equals(normalizeRange(range)) && xq != null && !xq.isBlank()) {
                form += "&xq=" + xq.trim();
            }

            HttpResponse<byte[]> response = client.send(
                    HttpRequest.newBuilder(URI.create(properties.getJwBaseUrl() + "/student/xscj.stuckcj_data.jsp"))
                            .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                            .header("Cookie", cookieHeader)
                            .header("Referer", properties.getJwBaseUrl() + "/student/xscj.stuckcj.jsp")
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form))
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.info("学业成绩数据页返回非 200, status={}", response.statusCode());
                throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
            }

            Document document = Jsoup.parse(decodeBody(response.body()));
            return parse(document, normalizeRange(range), normalizeScoreType(scoreType));
        }, ErrorCode.JW_SESSION_EXPIRED, "拉取教务成绩异常");
    }

    private JwGradeReport parse(Document document, String range, String scoreType) {
        List<JwGradeTerm> terms = new ArrayList<>();
        List<JwGradeCourse> current = null;
        String currentTerm = null;
        JwGradeSummary summary = null;

        for (Element row : document.select("tr")) {
            List<String> cells = row.select("td").stream().map(Element::text).map(String::trim).toList();
            if (cells.isEmpty()) {
                continue;
            }
            // 学期标题行：单列「学年学期：2025-2026学年第一学期」
            if (cells.size() == 1 && cells.get(0).contains("学年学期")) {
                if (currentTerm != null && current != null) {
                    terms.add(buildTerm(currentTerm, current));
                }
                currentTerm = cells.get(0).replace("学年学期：", "").replace("学年学期:", "").trim();
                current = new ArrayList<>();
                continue;
            }
            // 合计行：首列为「合计」
            if ("合计".equals(cells.get(0))) {
                summary = parseSummary(cells);
                continue;
            }
            // 课程行：首列序号为数字且列数足够
            if (current == null || !cells.get(0).matches("\\d+") || cells.size() < 12) {
                continue;
            }
            current.add(parseCourse(cells));
        }
        if (currentTerm != null && current != null) {
            terms.add(buildTerm(currentTerm, current));
        }
        return new JwGradeReport(range, scoreType, terms, summary == null ? emptySummary() : summary);
    }

    private JwGradeCourse parseCourse(List<String> cells) {
        String courseCell = cells.get(1);
        String courseCode = null;
        String courseName = courseCell;
        Matcher matcher = COURSE_CODE.matcher(courseCell);
        if (matcher.find()) {
            courseCode = matcher.group(1).trim();
            courseName = matcher.group(2).trim();
        }
        BigDecimal earnedCredits = decimal(cells.get(8));
        return new JwGradeCourse(
                parseInt(cells.get(0)),
                courseCode,
                courseName,
                decimal(cells.get(2)),
                parseInt(cells.get(3)),
                cells.get(4),
                cells.get(5),
                cells.get(6),
                cells.get(7),
                earnedCredits,
                decimal(cells.get(9)),
                decimal(cells.get(10)),
                cells.get(11),
                earnedCredits != null && earnedCredits.compareTo(BigDecimal.ZERO) > 0);
    }

    private JwGradeTerm buildTerm(String termName, List<JwGradeCourse> courses) {
        BigDecimal credits = courses.stream()
                .map(JwGradeCourse::credit)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new JwGradeTerm(termName, courses.size(), credits, List.copyOf(courses));
    }

    /**
     * 合计行解析。表尾先有一行指标名（修读课程环节数/学分/获得学分/获得绩点/获得学分绩点/
     * 获得平均学分绩点/平均成绩/加权平均成绩），紧随其后是「合计」+ 8 个数值。
     */
    private JwGradeSummary parseSummary(List<String> cells) {
        return new JwGradeSummary(
                cells.size() > 1 ? parseInt(cells.get(1)) : null,
                cells.size() > 2 ? decimal(cells.get(2)) : null,
                cells.size() > 3 ? decimal(cells.get(3)) : null,
                cells.size() > 4 ? decimal(cells.get(4)) : null,
                cells.size() > 5 ? decimal(cells.get(5)) : null,
                cells.size() > 6 ? decimal(cells.get(6)) : null,
                cells.size() > 7 ? decimal(cells.get(7)) : null,
                cells.size() > 8 ? decimal(cells.get(8)) : null);
    }

    private JwGradeSummary emptySummary() {
        return new JwGradeSummary(null, null, null, null, null, null, null, null);
    }

    private static String normalizeRange(String range) {
        return "YEAR".equalsIgnoreCase(range) ? "YEAR" : "TERM".equalsIgnoreCase(range) ? "TERM" : "ALL";
    }

    private static String normalizeScoreType(String scoreType) {
        return "RAW".equalsIgnoreCase(scoreType) ? "RAW" : "EFFECTIVE";
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank() || !value.matches("-?\\d+(\\.\\d+)?")) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    private static Integer parseInt(String value) {
        if (value == null || !value.trim().matches("\\d+")) {
            return null;
        }
        return Integer.parseInt(value.trim());
    }

    private String getString(HttpClient client, String cookieHeader, String path) throws Exception {
        HttpResponse<byte[]> response = client.send(
                HttpRequest.newBuilder(URI.create(properties.getJwBaseUrl() + path))
                        .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                        .header("Cookie", cookieHeader)
                        .header("Referer", properties.getJwBaseUrl() + "/student/xscj.stuckcj.jsp")
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
        }
        return decodeBody(response.body());
    }

    /** 教务页面 GBK/UTF-8 混用：选替换符更少的一种（与课表客户端一致） */
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

    /** 学业成绩报表（按学期分组 + 表尾合计） */
    public record JwGradeReport(String range, String scoreType,
                                List<JwGradeTerm> terms, JwGradeSummary summary) {
    }

    public record JwGradeTerm(String termName, int courseCount, BigDecimal credits,
                              List<JwGradeCourse> courses) {
    }

    public record JwGradeCourse(Integer sequence, String courseCode, String courseName,
                                BigDecimal credit, Integer hours, String category,
                                String courseType, String examType, String score,
                                BigDecimal earnedCredits, BigDecimal gradePoint,
                                BigDecimal creditGradePoint, String remark, boolean passed) {
    }

    /** 合计行：课程数/学分/获得学分/获得绩点/获得学分绩点/获得平均学分绩点/平均成绩/加权平均成绩 */
    public record JwGradeSummary(Integer courseCount, BigDecimal credits, BigDecimal earnedCredits,
                                 BigDecimal earnedGradePoints, BigDecimal earnedCreditGradePoints,
                                 BigDecimal averageGradePoint, BigDecimal averageScore,
                                 BigDecimal weightedAverageScore) {
    }
}
