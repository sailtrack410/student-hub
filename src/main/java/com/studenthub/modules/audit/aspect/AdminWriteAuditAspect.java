package com.studenthub.modules.audit.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studenthub.common.privacy.PhoneMasker;
import com.studenthub.modules.audit.service.AuditLogService;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import com.studenthub.modules.identity.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端写操作统一审计：对 /api/admin/** 的 POST/PUT/DELETE 成功调用自动留痕。
 *
 * <p>不要求在 60+ 个 Controller 方法上逐个加注解，新增管理端写接口默认获得审计能力；
 * 配置批量更新已由 SystemConfigServiceImpl 记录带 diff 的 CONFIG_UPDATE，此处跳过避免双写。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AdminWriteAuditAspect {

    private static final Set<String> WRITE_METHODS = Set.of(
            HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.PATCH.name(), HttpMethod.DELETE.name());
    private static final Set<String> SKIP_PATHS = Set.of("/api/admin/system/config");
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "oldpassword", "newpassword", "secret", "token",
            "accesstoken", "refreshtoken", "credential", "authorization");

    private final AuditLogService auditLogService;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    @Around("execution(public * com.studenthub.modules..controller..*(..))")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = currentRequest();
        if (!shouldAudit(request)) {
            return joinPoint.proceed();
        }
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable throwable) {
            // 失败写操作同样留痕（V71）：记录请求参数与错误摘要后原样抛出，不改变业务语义。
            try {
                recordFailure(joinPoint, request, throwable);
            } catch (Exception exception) {
                log.error("管理端失败写操作审计失败, path={}", request.getRequestURI(), exception);
            }
            throw throwable;
        }
        try {
            record(joinPoint, request, result);
        } catch (Exception exception) {
            // 审计能力不能反向阻断业务；异常仅记录服务端日志。
            log.error("管理端写操作审计失败, path={}", request.getRequestURI(), exception);
        }
        return result;
    }

    private void recordFailure(ProceedingJoinPoint joinPoint, HttpServletRequest request, Throwable throwable) {
        AuthenticatedUser operator = currentUserProvider.currentUser().orElse(null);
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String action = actionOf(method, request);
        String resourceType = resourceTypeOf(request.getRequestURI());
        String resourceId = resourceIdOf(request.getRequestURI(), null);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("method", request.getMethod());
        after.put("path", request.getRequestURI());
        Map<String, Object> args = sanitizedArgs(joinPoint.getArgs());
        if (!args.isEmpty()) {
            after.put("request", args);
        }
        after.put("error", throwable.getClass().getSimpleName());
        auditLogService.recordFailure(operator == null ? 0L : operator.userId(),
                operator == null ? "SYSTEM" : operator.displayName(),
                action, resourceType, resourceId, throwable.getMessage(), after);
    }

    private boolean shouldAudit(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/admin/") || SKIP_PATHS.contains(uri)) {
            return false;
        }
        return WRITE_METHODS.contains(request.getMethod());
    }

    private void record(ProceedingJoinPoint joinPoint, HttpServletRequest request, Object result) {
        AuthenticatedUser operator = currentUserProvider.currentUser().orElse(null);
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String action = actionOf(method, request);
        String resourceType = resourceTypeOf(request.getRequestURI());
        String resourceId = resourceIdOf(request.getRequestURI(), result);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("method", request.getMethod());
        after.put("path", request.getRequestURI());
        Map<String, Object> args = sanitizedArgs(joinPoint.getArgs());
        if (!args.isEmpty()) {
            after.put("request", args);
        }
        Map<String, Object> resultSummary = summarizeResult(result);
        if (!resultSummary.isEmpty()) {
            after.put("result", resultSummary);
        }
        // Service 主动提供的变更前快照优先（V71）：批量处置/状态迁移/配置保存等场景在修改前写入。
        Object before = AuditTrailContext.getBefore();

        auditLogService.record(operator == null ? 0L : operator.userId(),
                operator == null ? "SYSTEM" : operator.displayName(),
                action, resourceType, resourceId, before, after);
    }

    private String actionOf(Method method, HttpServletRequest request) {
        Operation operation = method.getAnnotation(Operation.class);
        if (operation != null && StringUtils.hasText(operation.operationId())) {
            return toUpperSnake(operation.operationId());
        }
        String path = request.getRequestURI().substring("/api/admin/".length());
        return request.getMethod() + "_" + path.replace('/', '_').replace('-', '_').toUpperCase();
    }

    private String resourceTypeOf(String uri) {
        String path = uri.substring("/api/admin/".length());
        String[] parts = path.split("/");
        String root = parts.length == 0 ? "ADMIN" : parts[0];
        if ("community".equals(root) && parts.length > 1) {
            root = "community_" + parts[1];
        } else if ("system".equals(root) && parts.length > 1) {
            root = "system_" + parts[1];
        } else if ("official-feed".equals(root)) {
            root = "official_feed";
        }
        return root.replace('-', '_').toUpperCase();
    }

    private String resourceIdOf(String uri, Object result) {
        String path = uri.substring("/api/admin/".length());
        String[] parts = path.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d+")) {
                return parts[i];
            }
        }
        if (result == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.valueToTree(result);
            for (String key : List.of("adId", "userId", "id", "campusId", "roleId", "itemId")) {
                JsonNode value = node.get(key);
                if (value != null && !value.isNull()) {
                    return value.asText();
                }
            }
        } catch (Exception ignored) {
            // 结果不是可序列化 DTO 时，资源 ID 可能为空；不阻断审计。
        }
        return null;
    }

    private Map<String, Object> sanitizedArgs(Object[] args) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (args == null) {
            return out;
        }
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg == null || arg instanceof HttpServletRequest || arg instanceof HttpServletResponse) {
                continue;
            }
            if (arg instanceof MultipartFile file) {
                Map<String, Object> fileInfo = new LinkedHashMap<>();
                fileInfo.put("name", file.getOriginalFilename());
                fileInfo.put("size", file.getSize());
                out.put("file" + i, fileInfo);
                continue;
            }
            if (arg instanceof MultipartFile[] files) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (MultipartFile file : files) {
                    if (file == null) continue;
                    Map<String, Object> fileInfo = new LinkedHashMap<>();
                    fileInfo.put("name", file.getOriginalFilename());
                    fileInfo.put("size", file.getSize());
                    list.add(fileInfo);
                }
                out.put("files" + i, list);
                continue;
            }
            try {
                JsonNode node = objectMapper.valueToTree(arg);
                mask(node);
                out.put("arg" + i, node);
            } catch (Exception exception) {
                out.put("arg" + i, String.valueOf(arg));
            }
        }
        return out;
    }

    /** 递归脱敏；手机号只保留掩码，密码/Token/凭证直接替换。 */
    private void mask(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            List<String> keys = new ArrayList<>();
            while (fields.hasNext()) {
                keys.add(fields.next().getKey());
            }
            for (String key : keys) {
                String lower = key.toLowerCase();
                JsonNode value = object.get(key);
                if (SENSITIVE_KEYS.contains(lower)) {
                    object.put(key, "***");
                } else if (lower.contains("phone") || lower.contains("mobile")) {
                    object.put(key, PhoneMasker.mask(value == null ? null : value.asText()));
                } else {
                    mask(value);
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::mask);
        }
    }

    private Map<String, Object> summarizeResult(Object result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (result == null) {
            return summary;
        }
        try {
            JsonNode node = objectMapper.valueToTree(result);
            JsonNode data = node.has("data") ? node.get("data") : node;
            if (data != null && data.isObject()) {
                for (String key : List.of("adId", "userId", "id", "campusId", "status", "reviewStatus")) {
                    JsonNode value = data.get(key);
                    if (value != null && !value.isNull()) {
                        summary.put(key, value.asText());
                    }
                }
            }
        } catch (Exception ignored) {
            // 非 JSON 结果不记录摘要。
        }
        return summary;
    }

    private String toUpperSnake(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                out.append('_');
            }
            out.append(Character.toUpperCase(c));
        }
        return out.toString();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
