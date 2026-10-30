package com.studenthub.modules.officialfeed.integration;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 学校官网（*.mycc.edu.cn）通知抓取：列表发现、正文/图片解析。
 * 仅允许本校域名，重定向后再次校验；正文按原始顺序产出 TEXT/IMAGE 块。
 */
@Slf4j
@Component
public class OfficialSiteClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 student-hub-official/1.0";
    private static final int TIMEOUT_MS = 15_000;
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;
    private static final int MAX_IMAGES = 9;
    private static final int MAX_TEXT_BLOCK = 2_000;

    private static final Pattern ARTICLE_PATH = Pattern.compile("^https?://([a-z0-9-]+\\.)*mycc\\.edu\\.cn/info/\\d+/\\d+\\.htm$");
    private static final Pattern PUBLISH_DATE = Pattern.compile(
            "发布(?:时间|日期)[:：]?\\s*(\\d{4})[-年/](\\d{1,2})[-月/](\\d{1,2})(?:\\s+(\\d{1,2}):(\\d{2}))?");
    private static final Set<String> BLOCK_TAGS = Set.of(
            "p", "div", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6",
            "section", "article", "table", "blockquote", "ul", "ol");

    /** 供管理端保存来源时校验地址，不发起请求。 */
    public void validateSourceUrl(String url) {
        validateUrl(url);
    }

    public List<String> discover(String listUrl, int maxPages) {
        validateUrl(listUrl);        int pages = Math.max(1, Math.min(maxPages <= 0 ? 1 : maxPages, 5));
        LinkedHashSet<String> articles = new LinkedHashSet<>();
        List<Map.Entry<Integer, String>> pageLinks = new ArrayList<>();
        Document first = fetchDocument(listUrl);
        collectArticles(first, articles);
        pageLinks.addAll(pagination(first, listUrl));
        for (int i = 0; i < Math.min(pages - 1, pageLinks.size()); i++) {
            Document page = fetchDocument(pageLinks.get(i).getValue());
            collectArticles(page, articles);
        }
        return new ArrayList<>(articles);
    }

    public OfficialArticle fetchArticle(String url) {
        validateUrl(url);
        Document doc = fetchDocument(url);
        Element content = doc.selectFirst(".v_news_content");
        if (content == null) {
            content = doc.selectFirst("[id^=vsb_content]");
        }
        if (content == null) {
            return null;
        }
        List<OfficialBlock> blocks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        walk(content, blocks, buffer);
        flush(buffer, blocks);
        if (blocks.isEmpty()) {
            blocks.addAll(fallbackBlocks(doc, content));
        }
        if (blocks.isEmpty()) {
            return null;
        }
        StringBuilder plain = new StringBuilder();
        for (OfficialBlock block : blocks) {
            if (block.isText()) {
                if (!plain.isEmpty()) {
                    plain.append('\n').append('\n');
                }
                plain.append(block.text());
            }
        }
        String title = cleanTitle(doc.title());
        if (title.isBlank()) {
            title = plain.length() > 40 ? plain.substring(0, 40) : plain.toString();
        }
        return new OfficialArticle(title, parsePublishedAt(doc.text()), List.copyOf(blocks), plain.toString());
    }

    public DownloadedImage fetchImage(String url) {
        validateUrl(url);
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .ignoreContentType(true)
                    .followRedirects(true)
                    .execute();
            validateUrl(response.url().toString());
            byte[] bytes = response.bodyAsBytes();
            String contentType = response.contentType();
            if (bytes.length == 0 || contentType == null) {
                return null;
            }
            String normalized = contentType.toLowerCase();
            if (!normalized.startsWith("image/")) {
                return null;
            }
            return new DownloadedImage(bytes, normalized.split(";")[0].trim(), fileNameOf(url, normalized));
        } catch (Exception exception) {
            log.warn("官网图片下载失败, url={}, reason={}", url, exception.getMessage());
            return null;
        }
    }

    private Document fetchDocument(String url) {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .followRedirects(true)
                    .execute();
            validateUrl(response.url().toString());
            return response.parse();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "抓取学校官网失败：" + exception.getMessage());
        }
    }

    private void collectArticles(Document doc, LinkedHashSet<String> articles) {
        for (Element anchor : doc.select("a[href]")) {
            String url = anchor.absUrl("href");
            if (!url.isBlank() && ARTICLE_PATH.matcher(url).matches()) {
                articles.add(url.split("#")[0]);
            }
        }
    }

    /** 翻页链接：栏目路径下的 /N.htm，按页码升序；第 1 页为列表页本身。 */
    private List<Map.Entry<Integer, String>> pagination(Document doc, String listUrl) {
        String prefix = listUrl.replaceAll("\\.htm$", "") + "/";
        TreeMap<Integer, String> pages = new TreeMap<>();
        for (Element anchor : doc.select("a[href]")) {
            String url = anchor.absUrl("href");
            if (url.startsWith(prefix) && url.matches(".*/\\d+\\.htm$")) {
                String file = url.substring(url.lastIndexOf('/') + 1);
                try {
                    pages.put(Integer.parseInt(file.substring(0, file.length() - 4)), url);
                } catch (NumberFormatException ignored) {
                    // 非页码链接忽略
                }
            }
        }
        return new ArrayList<>(pages.entrySet());
    }

    /** 正文为空时的兜底：内嵌 PDF、附件链接或正文纯文本（部分公示整页只有 PDF）。 */
    private List<OfficialBlock> fallbackBlocks(Document doc, Element content) {
        List<OfficialBlock> blocks = new ArrayList<>();
        Matcher pdf = Pattern.compile("showVsbpdfIframe\\(\"([^\"]+\\.pdf)\"").matcher(doc.html());
        if (pdf.find()) {
            blocks.add(OfficialBlock.text("正文为内嵌 PDF，原文附件：" + resolve(doc.baseUri(), pdf.group(1))));
        }
        for (Element anchor : content.select("a[href]")) {
            String href = anchor.absUrl("href");
            if (href.matches("(?i).*\\.(pdf|docx?|xlsx?|zip|rar)(\\?.*)?$")) {
                String label = anchor.text().isBlank() ? "附件" : anchor.text().trim();
                blocks.add(OfficialBlock.text(label + "：" + href));
            }
        }
        if (blocks.isEmpty()) {
            String text = content.text().replace('\u00a0', ' ')
                    .replaceAll("[\\s\\p{Z}]+", " ").trim();
            if (text.length() > 10) {
                blocks.add(OfficialBlock.text(text));
            }
        }
        return blocks;
    }

    private String resolve(String base, String raw) {
        try {
            return URI.create(base).resolve(raw).toString();
        } catch (Exception ignored) {
            return raw;
        }
    }

    private void walk(Node node, List<OfficialBlock> blocks, StringBuilder buffer) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode textNode) {
                String text = textNode.text();
                if (!text.isBlank()) {
                    if (!buffer.isEmpty()) {
                        buffer.append(' ');
                    }
                    buffer.append(text);
                }
                continue;
            }
            if (!(child instanceof Element element)) {
                continue;
            }
            String tag = element.tagName().toLowerCase();
            if ("script".equals(tag) || "style".equals(tag)) {
                continue;
            }
            if ("img".equals(tag)) {
                flush(buffer, blocks);
                String primary = element.absUrl("src");
                String src = primary.isBlank() ? element.absUrl("data-src") : primary;
                if (src.startsWith("http") && blocks.stream().filter(OfficialBlock::isImage)
                        .noneMatch(block -> block.imageUrl().equals(src))
                        && blocks.stream().filter(OfficialBlock::isImage).count() < MAX_IMAGES) {
                    blocks.add(OfficialBlock.image(src));
                }
                continue;
            }
            if ("br".equals(tag)) {
                flush(buffer, blocks);
                continue;
            }
            if (BLOCK_TAGS.contains(tag)) {
                flush(buffer, blocks);
                walk(element, blocks, buffer);
                flush(buffer, blocks);
            } else {
                walk(element, blocks, buffer);
            }
        }
    }

    private void flush(StringBuilder buffer, List<OfficialBlock> blocks) {
        String text = buffer.toString().replace('\u00a0', ' ')
                .replaceAll("[\\s\\p{Z}]+", " ").trim();
        buffer.setLength(0);
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > MAX_TEXT_BLOCK) {
            text = text.substring(0, MAX_TEXT_BLOCK);
        }
        blocks.add(OfficialBlock.text(text));
    }

    private LocalDateTime parsePublishedAt(String pageText) {
        Matcher matcher = PUBLISH_DATE.matcher(pageText);
        if (!matcher.find()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.of(Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
            LocalTime time = matcher.group(4) == null
                    ? LocalTime.MIN
                    : LocalTime.of(Integer.parseInt(matcher.group(4)), Integer.parseInt(matcher.group(5)));
            return LocalDateTime.of(date, time);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String cleanTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.replaceAll("\\s+", " ")
                .replaceAll("\\s*[-—]\\s*绵阳城市学院.*$", "").trim();
    }

    private String fileNameOf(String url, String contentType) {
        String path = URI.create(url).getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.isBlank() || !name.contains(".")) {
            String ext = contentType.contains("png") ? "png" : (contentType.contains("webp") ? "webp" : "jpg");
            return "notice." + ext;
        }
        return name.length() > 80 ? name.substring(name.length() - 80) : name;
    }

    private void validateUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "仅支持 http/https 地址");
            }
            String host = uri.getHost();
            if (host == null || !(host.equals("mycc.edu.cn") || host.endsWith(".mycc.edu.cn"))) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "仅允许抓取学校官网域名");
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "地址格式不合法");
        }
    }

    public record OfficialArticle(String title, LocalDateTime publishedAt, List<OfficialBlock> blocks,
                                  String plainText) {
    }

    public record OfficialBlock(String type, String text, String imageUrl) {
        public static OfficialBlock text(String text) {
            return new OfficialBlock("TEXT", text, null);
        }

        public static OfficialBlock image(String imageUrl) {
            return new OfficialBlock("IMAGE", null, imageUrl);
        }

        public boolean isText() {
            return "TEXT".equals(type);
        }

        public boolean isImage() {
            return "IMAGE".equals(type);
        }
    }

    public record DownloadedImage(byte[] bytes, String contentType, String fileName) {
    }
}
