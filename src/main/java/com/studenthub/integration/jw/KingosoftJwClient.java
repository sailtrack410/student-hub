package com.studenthub.integration.jw;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.util.IntegrationExceptions;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.jwauth.config.JwProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.math.BigInteger;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 青果（Kingosoft）教务系统客户端：复刻 SSO(mcauth) → CAS ticket → 教务会话 的登录链路。
 * 密码仅在此处加密使用，绝不落库、不写日志。
 */
@Slf4j
@Component
public class KingosoftJwClient {

    private static final Pattern HOME_IDENTITY = Pattern.compile("\\[(\\d{6,})\\]([\\u4e00-\\u9fa5·]{2,15})");
    private static final Pattern HIDDEN_XH = Pattern.compile("name=\"xh\"[^>]*value=\"(\\d+)\"");
    /** showUserInfo.action 正文（去标签后）的学生信息行 */
    private static final Pattern STUDENT_INFO = Pattern.compile(
            "\\[(\\d+)\\]([\\u4e00-\\u9fa5·]{2,15})\\s*\\(([^)]+)\\)"
                    + ".*?年级[：:]\\s*([0-9]{4})"
                    + ".*?院\\(系\\)/部[：:]\\s*(\\S+)"
                    + ".*?专业[：:]\\s*(\\S+)"
                    + ".*?班级[：:]\\s*(\\S+)",
            Pattern.DOTALL);

    private final JwProperties properties;
    private volatile PublicKey rsaPublicKey;

    public KingosoftJwClient(JwProperties properties) {
        this.properties = properties;
        reload();
    }

    /** 管理端修改 SSO RSA 公钥后重建本地公钥；非法公钥保留旧值并记录，不打断登录链路。 */
    public synchronized void reload() {
        try {
            this.rsaPublicKey = buildPublicKey(properties.getRsaModulus(), properties.getRsaExponent());
        } catch (Exception exception) {
            if (this.rsaPublicKey == null) {
                throw exception;
            }
            log.error("教务 SSO RSA 公钥配置无效，保留启动时的公钥: {}", exception.getMessage());
        }
    }

    /**
     * 获取算术题验证码。SSO 返回 JSON：{kaptchaType, uid, content(data:URL base64), timeout}，
     * uid 以服务端返回为准（与登录时的 id 参数绑定）。
     */
    public JwCaptcha fetchCaptcha(String uid) {
        return IntegrationExceptions.wrap(() -> {
            HttpClient client = buildClient(new CookieManager());
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(properties.getSsoBaseUrl() + "/mcauth/kaptcha?uid=" + uid))
                            .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("获取教务验证码失败, status={}", response.statusCode());
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "验证码获取失败，请稍后重试");
            }
            String content = parseJsonField(response.body(), "content");
            String serverUid = parseJsonField(response.body(), "uid");
            if (content == null || !content.contains("base64,")) {
                log.warn("教务验证码响应缺少图片内容");
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "验证码获取失败，请稍后重试");
            }
            String base64 = content.substring(content.indexOf("base64,") + "base64,".length());
            return new JwCaptcha(serverUid != null ? serverUid : uid, base64);
        }, ErrorCode.INTERNAL_ERROR, "请求教务验证码异常");
    }

    /**
     * 执行完整登录链路，成功返回教务会话 Cookie 与解析出的身份信息。
     *
     * @throws BusinessException JW_AUTH_FAILED 账号、密码或验证码错误
     */
    public JwLoginResult login(String account, String password, String captchaUid, String captchaCode) {
        CookieManager cookieManager = new CookieManager();
        return IntegrationExceptions.wrap(() -> {
            HttpClient client = buildClient(cookieManager);

            // 1. SSO tickets：RSA 加密密码换取 ticket
            String form = "username=" + urlEncode(account)
                    + "&password=" + urlEncode(encryptPassword(password))
                    + "&service=" + urlEncode(properties.getCasService())
                    + "&loginType="
                    + "&id=" + urlEncode(captchaUid)
                    + "&code=" + urlEncode(captchaCode);
            HttpResponse<String> ticketResponse = client.send(
                    HttpRequest.newBuilder(URI.create(properties.getSsoBaseUrl() + "/mcauth/v1/tickets"))
                            .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (ticketResponse.statusCode() != 200) {
                log.info("SSO tickets 认证未通过, status={}", ticketResponse.statusCode());
                throw new BusinessException(ErrorCode.JW_AUTH_FAILED);
            }
            // 失败响应：{"data":{"code":"CODEFALSE",...}}；成功响应：{"data":{"tgt":"...","ticket":"..."}}
            // （部分环境为顶层字段，两种位置都解析）
            String body = ticketResponse.body();
            String failCode = parseJsonField(body, "code", "data");
            if (failCode == null) {
                failCode = parseJsonField(body, "code");
            }
            if (failCode != null && !failCode.isBlank()) {
                log.info("SSO tickets 拒绝, code={}", failCode);
                throw new BusinessException(ErrorCode.JW_AUTH_FAILED, describeReject(failCode));
            }
            String ticket = parseJsonField(body, "ticket");
            if (ticket == null || ticket.isBlank()) {
                ticket = parseJsonField(body, "ticket", "data");
            }
            if (ticket == null || ticket.isBlank()) {
                log.info("SSO tickets 响应缺少 ticket");
                throw new BusinessException(ErrorCode.JW_AUTH_FAILED);
            }

            // 2. ticket 换教务会话（跟随 302 链，CookieManager 自动收集 JSESSIONID）
            // 落地页即带访问令牌参数的 homes.action?v=...，其中直接含有身份信息
            HttpResponse<byte[]> casResponse = client.send(
                    HttpRequest.newBuilder(URI.create(properties.getJwBaseUrl() + "/caslogin?ticket=" + urlEncode(ticket)))
                            .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            URI finalUri = casResponse.uri();
            if (!finalUri.getHost().contains("jwgl")) {
                log.info("CAS 登录未落到教务域名, final={}", finalUri.getHost());
                throw new BusinessException(ErrorCode.JW_AUTH_FAILED);
            }

            String cookieHeader = cookiesForJwgl(cookieManager);
            if (cookieHeader.isBlank()) {
                log.info("CAS 登录后未取得教务会话 Cookie");
                throw new BusinessException(ErrorCode.JW_AUTH_FAILED);
            }

            // 3. 从落地页解析身份（含子框架与编码兜底）
            JwProfile profile = extractProfile(casResponse.body(), casResponse.headers(), client, cookieHeader);
            profile = mergeStudentInfo(client, cookieHeader, profile);
            return new JwLoginResult(cookieHeader, finalUri.toString(), profile.ssoAccount(), profile.jwName(),
                    profile.identityKind(), profile.jwStudentNo(),
                    profile.grade(), profile.college(), profile.major(), profile.className());
        }, ErrorCode.JW_AUTH_FAILED, "教务登录链路异常");
    }

    /** 用托管会话 Cookie 访问登录落地页，拉取个人信息 */
    public JwProfile fetchProfile(String cookieHeader, String homeUrl) {
        return IntegrationExceptions.wrap(() -> {
            HttpClient client = buildClient(new CookieManager());
            HttpResponse<byte[]> response = client.send(
                    HttpRequest.newBuilder(URI.create(homeUrl))
                            .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                            .header("Cookie", cookieHeader)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
            }
            JwProfile profile = extractProfile(response.body(), response.headers(), client, cookieHeader);
            return mergeStudentInfo(client, cookieHeader, profile);
        }, ErrorCode.JW_SESSION_EXPIRED, "拉取教务个人信息异常");
    }

    /**
     * 学生账号追加解析年级/院系/专业/班级（showUserInfo.action，GBK）。
     * 解析失败不影响认证与基本信息返回。
     */
    private JwProfile mergeStudentInfo(HttpClient client, String cookieHeader, JwProfile profile) {
        if (!"STUDENT".equals(profile.identityKind())) {
            return profile;
        }
        try {
            HttpResponse<byte[]> response = client.send(
                    HttpRequest.newBuilder(URI.create(properties.getJwBaseUrl() + "/frame/desk/showUserInfo.action"))
                            .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                            .header("Cookie", cookieHeader)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return profile;
            }
            // GBK 优先尝试，其次 UTF-8；去标签与 &ensp; 后按标签字段解析
            for (String charset : new String[]{"GBK", "UTF-8"}) {
                String text = new String(response.body(), charset)
                        .replaceAll("<[^>]+>", " ")
                        .replace("&ensp;", " ")
                        .replaceAll("[ \t]+", " ");
                Matcher matcher = STUDENT_INFO.matcher(text);
                if (matcher.find()) {
                    return new JwProfile(profile.ssoAccount(), profile.jwName(), profile.identityKind(),
                            profile.jwStudentNo(), matcher.group(4), matcher.group(5),
                            matcher.group(6), matcher.group(7));
                }
            }
            log.info("学生信息页未解析出年级院系专业班级");
        } catch (Exception exception) {
            log.warn("解析学生年级院系专业班级失败", exception);
        }
        return profile;
    }

    /**
     * 解析教务页面中的身份信息：失效提示页直接判定失效；主页未命中时遍历子框架；
     * UTF-8/GBK 双编码变体逐一尝试。
     */
    private JwProfile extractProfile(byte[] body, HttpHeaders headers, HttpClient client, String cookieHeader) {
        List<String> variants = decodeVariants(body, headers);
        for (String html : variants) {
            if (html.contains("凭证已失效")) {
                log.info("教务页面返回会话失效提示页");
                throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
            }
        }
        for (String html : variants) {
            Matcher matcher = HOME_IDENTITY.matcher(html);
            if (matcher.find()) {
                return buildProfile(matcher, html, client, cookieHeader);
            }
        }

        // 主页可能是框架集：身份在子框架页里
        List<String> frameUrls = extractFrameUrls(variants.get(0));
        log.info("教务主页未直接命中身份，尝试子框架 {} 个", frameUrls.size());
        for (String frameUrl : frameUrls) {
            try {
                HttpResponse<byte[]> frameResponse = client.send(
                        HttpRequest.newBuilder(URI.create(absoluteUrl(frameUrl)))
                                .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                                .header("Cookie", cookieHeader)
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (frameResponse.statusCode() != 200) {
                    continue;
                }
                for (String html : decodeVariants(frameResponse.body(), frameResponse.headers())) {
                    Matcher matcher = HOME_IDENTITY.matcher(html);
                    if (matcher.find()) {
                        return buildProfile(matcher, html, client, cookieHeader);
                    }
                }
            } catch (Exception exception) {
                log.warn("抓取教务子框架失败, url={}", frameUrl);
            }
        }

        // SetMainInfo.jsp 是按会话生成的主信息脚本，含账号/姓名/身份/内部学号等全部字段
        try {
            HttpResponse<byte[]> infoResponse = client.send(
                    HttpRequest.newBuilder(URI.create(properties.getJwBaseUrl() + "/frame/home/js/SetMainInfo.jsp"))
                            .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                            .header("Cookie", cookieHeader)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (infoResponse.statusCode() == 200) {
                for (String js : decodeVariants(infoResponse.body(), infoResponse.headers())) {
                    JwProfile profile = parseSetMainInfo(js);
                    if (profile != null) {
                        return profile;
                    }
                }
                java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/jw-setmaininfo.js"),
                        decodeVariants(infoResponse.body(), infoResponse.headers()).get(0));
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("抓取 SetMainInfo 失败", exception);
        }

        log.info("教务页面及子框架均未解析到身份信息, homeLength={}, frames={}", variants.get(0).length(), frameUrls.size());
        dumpForDebug(variants.get(0), body);
        throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
    }

    /** 解析 SetMainInfo.jsp 的会话级 JS 变量 */
    private JwProfile parseSetMainInfo(String js) {
        String loginId = parseJsVar(js, "_loginid");
        String userName = parseJsVar(js, "_userName");
        if (loginId == null || userName == null) {
            return null;
        }
        String userType = parseJsVar(js, "_usertype");
        String identityKind = userType != null && (userType.startsWith("TEA") || userType.startsWith("TCH"))
                ? "TEACHER" : "STUDENT";
        return new JwProfile(loginId, userName, identityKind, parseJsVar(js, "G_USER_CODE"),
                null, null, null, null);
    }

    private String parseJsVar(String js, String name) {
        Matcher matcher = Pattern.compile("var\\s+" + name + "\\s*=\\s*'([^']*)'").matcher(js);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** 开发期诊断：身份解析失败时把原始落地页写到 /tmp 供分析 */
    private void dumpForDebug(String html, byte[] rawBody) {
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/jw-landing.html"), html);
            java.nio.file.Files.write(java.nio.file.Path.of("/tmp/jw-landing.bin"), rawBody);
        } catch (Exception exception) {
            log.warn("诊断转储失败", exception);
        }
    }


    private JwProfile buildProfile(Matcher matcher, String html, HttpClient client, String cookieHeader) {
        String ssoAccount = matcher.group(1);
        String jwName = matcher.group(2);
        String identityKind = detectIdentityKind(html, ssoAccount);
        String studentNo = fetchInternalStudentNo(client, cookieHeader, identityKind);
        return new JwProfile(ssoAccount, jwName, identityKind, studentNo, null, null, null, null);
    }

    /** 提取页面里的 frame/iframe 地址 */
    private List<String> extractFrameUrls(String html) {
        List<String> urls = new java.util.ArrayList<>();
        Matcher matcher = Pattern.compile("(?:frame|iframe)[^>]*?src=\"([^\"]+)\"").matcher(html);
        while (matcher.find()) {
            String src = matcher.group(1);
            if (src.startsWith("http") || src.endsWith(".jsp") || src.endsWith(".action")) {
                urls.add(src);
            }
        }
        return urls;
    }

    private String absoluteUrl(String src) {
        if (src.startsWith("http")) {
            return src;
        }
        return properties.getJwBaseUrl() + (src.startsWith("/") ? src : "/" + src);
    }

    /** 按响应头编码解码；无头时同时给出 UTF-8 与 GBK 两个变体 */
    private List<String> decodeVariants(byte[] body, HttpHeaders headers) {
        List<String> variants = new java.util.ArrayList<>();
        String headerCharset = headers.firstValue("Content-Type")
                .filter(value -> value.toLowerCase().contains("charset"))
                .map(value -> value.replaceAll("(?i).*charset=([^;]+).*", "$1").trim())
                .orElse(null);
        if (headerCharset != null) {
            variants.add(decode(body, headers));
            return variants;
        }
        try {
            variants.add(new String(body, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // 忽略
        }
        try {
            variants.add(new String(body, "GBK"));
        } catch (Exception ignored) {
            // 忽略
        }
        return variants;
    }

    /** 学生账号访问课表页可取得教务内部学号；教师账号拿不到则留空 */
    private String fetchInternalStudentNo(HttpClient client, String cookieHeader, String identityKind) {
        if (!"STUDENT".equals(identityKind)) {
            return null;
        }
        try {
            HttpResponse<byte[]> response = client.send(
                    HttpRequest.newBuilder(URI.create(properties.getJwBaseUrl()
                                    + "/student/xkjg.wdkb.jsp?menucode=S20301&bqflag=1"))
                            .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                            .header("Cookie", cookieHeader)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return null;
            }
            String html = decode(response.body(), response.headers());
            Matcher matcher = HIDDEN_XH.matcher(html);
            return matcher.find() ? matcher.group(1) : null;
        } catch (Exception exception) {
            log.warn("解析教务内部学号失败", exception);
            return null;
        }
    }

    /**
     * 身份识别为启发式：主页同时出现教师标识词优先判教师，否则默认学生；
     * 用户随后仍可在个人中心自行调整身份。
     */
    private String detectIdentityKind(String html, String ssoAccount) {
        String studentAccountMarker = "学生";
        if (html.contains("教师个人课表") || html.contains("教师工作量")
                || (html.contains("教师") && !html.contains(studentAccountMarker + "课表") && !html.contains("学生评教"))) {
            return "TEACHER";
        }
        if (ssoAccount.length() >= 10) {
            return "STUDENT";
        }
        return "STUDENT";
    }

    private String cookiesForJwgl(CookieManager cookieManager) {
        List<HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
        return cookies.stream()
                .filter(cookie -> cookie.getDomain() != null && cookie.getDomain().contains("jwgl"))
                .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                .collect(Collectors.joining("; "));
    }

    /** 按 SSO 拒绝码给出可操作的提示（不含敏感信息） */
    private String describeReject(String code) {
        return switch (code) {
            case "CODEFALSE" -> "验证码错误或已过期，请刷新后重新输入";
            case "NOUSER" -> "账号不存在，请使用学校统一身份账号（学号/工号）登录";
            case "PASSFALSE", "PASSWORDFALSE" -> "密码错误，请确认统一身份平台密码";
            default -> "账号、密码或验证码错误";
        };
    }

    private String parseJsonField(String body, String field) {
        if (body == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** 在 body 顶层或指定父对象（一级嵌套）内解析字符串字段 */
    private String parseJsonField(String body, String field, String parentKey) {
        if (body == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\"" + parentKey + "\"\\s*:\\s*\\{[^{}]*\"" + field
                + "\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 复刻学校登录页前端（经典 rsa.js：BigInt/Barrett）的加密算法：
     * 口令按 ASCII 取码、零填充到 chunkSize（2×模数高位数字序号，1024 位模数下为 126 字节），
     * 按 16 位小端数字构造大整数，做无填充裸 RSA（value^e mod n），
     * 结果以每数字 4 个 hex 字符输出（跳过前导零数字），多块之间以空格连接。
     */
    private String encryptPassword(String password) {
        byte[] plain = password.getBytes(StandardCharsets.US_ASCII);
        BigInteger modulus = new BigInteger(1, hexToBytes(properties.getRsaModulus()));
        BigInteger exponent = new BigInteger(1, hexToBytes(properties.getRsaExponent()));
        int chunkSize = 2 * ((modulus.bitLength() - 1) / 16);

        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < plain.length || offset == 0; offset += chunkSize) {
            int len = Math.min(chunkSize, plain.length - offset);
            byte[] block = new byte[chunkSize];
            System.arraycopy(plain, offset, block, 0, len);
            // 小端字节序 → 大整数
            byte[] littleEndianBigNumber = new byte[chunkSize];
            for (int i = 0; i < chunkSize; i++) {
                littleEndianBigNumber[i] = block[chunkSize - 1 - i];
            }
            BigInteger value = new BigInteger(1, littleEndianBigNumber);
            String hex = toFrontendHex(value.modPow(exponent, modulus));
            result.append(hex);
            if (offset + chunkSize >= plain.length) {
                break;
            }
            result.append(' ');
        }
        return result.toString();
    }

    /** 前端 biToHex：从最高非零数字开始，每个 16 位数字固定输出 4 个 hex 字符 */
    private String toFrontendHex(BigInteger value) {
        String hex = value.toString(16);
        int digits = (value.bitLength() - 1) / 16 + 1;
        int width = digits * 4;
        if (hex.length() > width) {
            // 去掉高位多余的零（BigInteger 不产生，防御性保留）
            hex = hex.substring(hex.length() - width);
        }
        while (hex.length() < width) {
            hex = "0" + hex;
        }
        return hex;
    }

    private PublicKey buildPublicKey(String modulusHex, String exponentHex) {
        try {
            RSAPublicKeySpec spec = new RSAPublicKeySpec(
                    new BigInteger(1, hexToBytes(modulusHex)),
                    new BigInteger(1, hexToBytes(exponentHex)));
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception exception) {
            throw new IllegalStateException("SSO RSA 公钥初始化失败", exception);
        }
    }

    private byte[] hexToBytes(String hex) {
        int length = hex.length();
        // 模数可能带前导 00，奇数长度时补齐
        String normalized = length % 2 == 0 ? hex : "0" + hex;
        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(normalized.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private String decode(byte[] body, HttpHeaders headers) {
        String charset = headers.firstValue("Content-Type")
                .filter(value -> value.toLowerCase().contains("charset"))
                .map(value -> value.replaceAll("(?i).*charset=([^;]+).*", "$1").trim())
                .orElse("UTF-8");
        try {
            return new String(body, charset);
        } catch (Exception exception) {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private HttpClient buildClient(CookieManager cookieManager) {
        return HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    public record JwLoginResult(String cookieHeader, String homeUrl, String ssoAccount, String jwName,
                                String identityKind, String jwStudentNo,
                                String grade, String college, String major, String className) {
    }

    public record JwCaptcha(String uid, String imageBase64) {
    }

    public record JwProfile(String ssoAccount, String jwName, String identityKind, String jwStudentNo,
                            String grade, String college, String major, String className) {
    }
}
