package com.studenthub.integration.jw;

import com.studenthub.common.ErrorCode;
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

    private final JwProperties properties;
    private final PublicKey rsaPublicKey;

    public KingosoftJwClient(JwProperties properties) {
        this.properties = properties;
        this.rsaPublicKey = buildPublicKey(properties.getRsaModulus(), properties.getRsaExponent());
    }

    /**
     * 获取算术题验证码。SSO 返回 JSON：{kaptchaType, uid, content(data:URL base64), timeout}，
     * uid 以服务端返回为准（与登录时的 id 参数绑定）。
     */
    public JwCaptcha fetchCaptcha(String uid) {
        try {
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
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("请求教务验证码异常", exception);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "验证码获取失败，请稍后重试");
        }
    }

    /**
     * 执行完整登录链路，成功返回教务会话 Cookie 与解析出的身份信息。
     *
     * @throws BusinessException JW_AUTH_FAILED 账号、密码或验证码错误
     */
    public JwLoginResult login(String account, String password, String captchaUid, String captchaCode) {
        CookieManager cookieManager = new CookieManager();
        try {
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
            if (failCode != null && !failCode.isBlank()) {
                log.info("SSO tickets 拒绝, code={}", failCode);
                throw new BusinessException(ErrorCode.JW_AUTH_FAILED);
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
            HttpResponse<String> casResponse = client.send(
                    HttpRequest.newBuilder(URI.create(properties.getJwBaseUrl() + "/caslogin?ticket=" + urlEncode(ticket)))
                            .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
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

            // 3. 访问主页确认会话有效并解析身份（姓名/学号/身份类别）
            JwProfile profile = fetchProfileInternal(client, cookieHeader);
            return new JwLoginResult(cookieHeader, profile.ssoAccount(), profile.jwName(),
                    profile.identityKind(), profile.jwStudentNo());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("教务登录链路异常", exception);
            throw new BusinessException(ErrorCode.JW_AUTH_FAILED);
        }
    }

    /** 用教务会话 Cookie 拉取个人信息 */
    public JwProfile fetchProfile(String cookieHeader) {
        try {
            HttpClient client = buildClient(new CookieManager());
            return fetchProfileInternal(client, cookieHeader);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("拉取教务个人信息异常", exception);
            throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
        }
    }

    private JwProfile fetchProfileInternal(HttpClient client, String cookieHeader) throws Exception {
        HttpResponse<byte[]> homeResponse = client.send(
                HttpRequest.newBuilder(URI.create(properties.getJwBaseUrl() + "/frame/homes.action"))
                        .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                        .header("Cookie", cookieHeader)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (homeResponse.statusCode() != 200) {
            log.info("教务主页返回非 200, status={}", homeResponse.statusCode());
            throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
        }
        // 青果页面存在 GBK/UTF-8 两种编码，逐个尝试身份匹配
        List<String> homeVariants = decodeVariants(homeResponse.body(), homeResponse.headers());
        for (String html : homeVariants) {
            if (html.contains("凭证已失效")) {
                log.info("教务主页返回会话失效提示页");
                throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
            }
            Matcher matcher = HOME_IDENTITY.matcher(html);
            if (matcher.find()) {
                String ssoAccount = matcher.group(1);
                String jwName = matcher.group(2);
                String identityKind = detectIdentityKind(html, ssoAccount);
                String studentNo = fetchInternalStudentNo(client, cookieHeader, identityKind);
                return new JwProfile(ssoAccount, jwName, identityKind, studentNo);
            }
        }

        // 主页可能是框架集：身份在子框架页里，逐个抓取匹配
        List<String> frameUrls = extractFrameUrls(homeVariants.get(0));
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
                        String ssoAccount = matcher.group(1);
                        String jwName = matcher.group(2);
                        String identityKind = detectIdentityKind(html, ssoAccount);
                        String studentNo = fetchInternalStudentNo(client, cookieHeader, identityKind);
                        return new JwProfile(ssoAccount, jwName, identityKind, studentNo);
                    }
                }
            } catch (Exception exception) {
                log.warn("抓取教务子框架失败, url={}", frameUrl);
            }
        }
        log.info("教务主页及子框架均未解析到身份信息, homeLength={}, frames={}",
                homeVariants.get(0).length(), frameUrls.size());
        throw new BusinessException(ErrorCode.JW_SESSION_EXPIRED);
    }

    /** 提取主页里的 frame/iframe 地址 */
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
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    public record JwLoginResult(String cookieHeader, String ssoAccount, String jwName,
                                String identityKind, String jwStudentNo) {
    }

    public record JwCaptcha(String uid, String imageBase64) {
    }

    public record JwProfile(String ssoAccount, String jwName, String identityKind, String jwStudentNo) {
    }
}
