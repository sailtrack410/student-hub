package com.studenthub.modules.aichat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.aichat.controller.dto.AiDefaultModelResponse;
import com.studenthub.modules.aichat.controller.dto.AiDefaultModelSetRequest;
import com.studenthub.modules.aichat.controller.dto.AiModelImportRequest;
import com.studenthub.modules.aichat.controller.dto.AiModelResponse;
import com.studenthub.modules.aichat.controller.dto.AiModelUpdateRequest;
import com.studenthub.modules.aichat.controller.dto.AiProviderCheckResponse;
import com.studenthub.modules.aichat.controller.dto.AiProviderCreateRequest;
import com.studenthub.modules.aichat.controller.dto.AiProviderResponse;
import com.studenthub.modules.aichat.controller.dto.AiProviderUpdateRequest;
import com.studenthub.modules.aichat.entity.AiDefaultModelEntity;
import com.studenthub.modules.aichat.entity.AiModelEntity;
import com.studenthub.modules.aichat.entity.AiProviderEntity;
import com.studenthub.modules.aichat.mapper.AiDefaultModelMapper;
import com.studenthub.modules.aichat.mapper.AiModelMapper;
import com.studenthub.modules.aichat.mapper.AiProviderMapper;
import com.studenthub.modules.aichat.service.AiConfigService;
import com.studenthub.modules.aichat.service.AiProviderAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiProviderAdminServiceImpl implements AiProviderAdminService {

    private static final String TYPE_CHAT = "CHAT";
    private static final String TYPE_VISION = "VISION";
    private static final String TYPE_ASR = "ASR";
    private static final String TYPE_EMBEDDING = "EMBEDDING";
    private static final Pattern VALID_PROVIDER_CODE = Pattern.compile("^[a-z0-9][a-z0-9-]{0,62}$");
    private static final Duration CHECK_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CHECK_READ_TIMEOUT = Duration.ofSeconds(15);

    private final AiProviderMapper providerMapper;
    private final AiModelMapper modelMapper;
    private final AiDefaultModelMapper defaultModelMapper;
    private final AiConfigService aiConfigService;
    private final ObjectMapper objectMapper;

    @Override
    public List<AiProviderResponse> listProviders() {
        Map<Long, Long> counts = countModelsByProvider();
        return providerMapper.selectList(new LambdaQueryWrapper<AiProviderEntity>()
                        .orderByAsc(AiProviderEntity::getId))
                .stream()
                .map(provider -> AiProviderResponse.from(provider,
                        counts.getOrDefault(provider.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiProviderResponse createProvider(Long operatorId, AiProviderCreateRequest request) {
        String code = normalizeProviderCode(request.providerCode(), request.providerName());
        if (providerMapper.selectCount(new LambdaQueryWrapper<AiProviderEntity>()
                .eq(AiProviderEntity::getProviderCode, code)) > 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "供应商编码已存在: " + code);
        }
        AiProviderEntity provider = new AiProviderEntity();
        provider.setProviderCode(code);
        provider.setProviderName(request.providerName().trim());
        provider.setBaseUrl(normalizeBaseUrl(request.baseUrl()));
        provider.setApiKey(request.apiKey().trim());
        provider.setEnabled(true);
        providerMapper.insert(provider);
        aiConfigService.refreshRuntime();
        log.info("AI 供应商已新增, providerId={}, code={}, operatorId={}", provider.getId(), code, operatorId);
        return AiProviderResponse.from(provider, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiProviderResponse updateProvider(Long operatorId, Long providerId, AiProviderUpdateRequest request) {
        AiProviderEntity provider = requireProvider(providerId);
        if (StringUtils.hasText(request.providerName())) {
            provider.setProviderName(request.providerName().trim());
        }
        if (StringUtils.hasText(request.baseUrl())) {
            provider.setBaseUrl(normalizeBaseUrl(request.baseUrl()));
        }
        // apiKey 留空 = 保持原值
        if (StringUtils.hasText(request.apiKey()) && !request.apiKey().isBlank()) {
            provider.setApiKey(request.apiKey().trim());
        }
        if (request.enabled() != null) {
            provider.setEnabled(request.enabled());
        }
        providerMapper.updateById(provider);
        aiConfigService.refreshRuntime();
        log.info("AI 供应商已更新, providerId={}, operatorId={}", providerId, operatorId);
        return AiProviderResponse.from(provider,
                modelMapper.selectCount(new LambdaQueryWrapper<AiModelEntity>()
                        .eq(AiModelEntity::getProviderId, providerId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProvider(Long operatorId, Long providerId) {
        requireProvider(providerId);
        List<Long> modelIds = modelMapper.selectList(new LambdaQueryWrapper<AiModelEntity>()
                        .eq(AiModelEntity::getProviderId, providerId))
                .stream().map(AiModelEntity::getId).toList();
        providerMapper.deleteById(providerId);
        if (!modelIds.isEmpty()) {
            modelMapper.deleteByIds(modelIds);
            clearDefaultsReferencing(modelIds);
        }
        aiConfigService.refreshRuntime();
        log.info("AI 供应商已删除, providerId={}, 级联模型数={}, operatorId={}",
                providerId, modelIds.size(), operatorId);
    }

    @Override
    public AiProviderCheckResponse checkConnectivity(Long providerId) {
        AiProviderEntity provider = requireProvider(providerId);
        LocalDateTime checkedAt = LocalDateTime.now();
        String result;
        boolean ok;
        try {
            String body = modelsClient(provider).get().uri("/models").retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body == null ? "" : body);
            int count = root.path("data").isArray() ? root.path("data").size() : -1;
            ok = count >= 0;
            result = ok ? "连接成功，发现 " + count + " 个可用模型"
                    : "连接成功，但响应不是标准的模型列表";
        } catch (BusinessException exception) {
            ok = false;
            result = exception.getMessage();
        } catch (Exception exception) {
            ok = false;
            result = "连接失败: " + rootMessage(exception);
        }
        persistCheckResult(provider, ok, result, checkedAt);
        return new AiProviderCheckResponse(ok, result, checkedAt);
    }

    @Override
    public List<String> fetchRemoteModels(Long providerId) {
        AiProviderEntity provider = requireProvider(providerId);
        try {
            String body = modelsClient(provider).get().uri("/models").retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body == null ? "" : body);
            if (!root.path("data").isArray()) {
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "供应商响应不是标准模型列表");
            }
            List<String> ids = new ArrayList<>();
            root.path("data").forEach(node -> {
                String id = node.path("id").asText(null);
                if (StringUtils.hasText(id)) {
                    ids.add(id);
                }
            });
            ids.sort(String.CASE_INSENSITIVE_ORDER);
            return ids;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("拉取供应商模型列表失败, providerId={}", providerId, exception);
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "拉取模型列表失败: " + rootMessage(exception));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "解析模型列表失败: " + rootMessage(exception));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importModels(Long operatorId, Long providerId, AiModelImportRequest request) {
        requireProvider(providerId);
        Set<String> existing = modelMapper.selectList(new LambdaQueryWrapper<AiModelEntity>()
                        .eq(AiModelEntity::getProviderId, providerId))
                .stream().map(AiModelEntity::getProviderModelId).collect(Collectors.toSet());
        Set<String> seenInRequest = new HashSet<>();
        int added = 0;
        for (AiModelImportRequest.AiModelImportItem item : request.models()) {
            String modelId = item.providerModelId().trim();
            String type = StringUtils.hasText(item.modelType()) ? item.modelType() : TYPE_CHAT;
            if (!seenInRequest.add(modelId) || existing.contains(modelId)) {
                continue;
            }
            AiModelEntity model = new AiModelEntity();
            model.setProviderId(providerId);
            model.setProviderModelId(modelId);
            model.setModelType(type);
            model.setEnabled(true);
            modelMapper.insert(model);
            existing.add(modelId);
            added++;
        }
        if (added > 0) {
            aiConfigService.refreshRuntime();
        }
        log.info("AI 模型导入完成, providerId={}, 新增={}, 请求={}, operatorId={}",
                providerId, added, request.models().size(), operatorId);
        return added;
    }

    @Override
    public List<AiModelResponse> listModels(String keyword, String modelType) {
        List<AiModelEntity> models = modelMapper.selectList(new LambdaQueryWrapper<AiModelEntity>()
                .like(StringUtils.hasText(keyword), AiModelEntity::getProviderModelId, keyword)
                .eq(StringUtils.hasText(modelType), AiModelEntity::getModelType, modelType)
                .orderByAsc(AiModelEntity::getProviderId)
                .orderByAsc(AiModelEntity::getProviderModelId));
        Map<Long, String> providerNames = providerNameMap();
        return models.stream()
                .map(model -> AiModelResponse.from(model,
                        providerNames.getOrDefault(model.getProviderId(), "未知供应商")))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiModelResponse updateModel(Long operatorId, Long modelId, AiModelUpdateRequest request) {
        AiModelEntity model = requireModel(modelId);
        if (request.enabled() != null) {
            model.setEnabled(request.enabled());
        }
        if (StringUtils.hasText(request.modelType())) {
            model.setModelType(request.modelType());
        }
        modelMapper.updateById(model);
        aiConfigService.refreshRuntime();
        log.info("AI 模型已更新, modelId={}, operatorId={}", modelId, operatorId);
        return AiModelResponse.from(model,
                providerNameMap().getOrDefault(model.getProviderId(), "未知供应商"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(Long operatorId, Long modelId) {
        requireModel(modelId);
        modelMapper.deleteById(modelId);
        clearDefaultsReferencing(List.of(modelId));
        aiConfigService.refreshRuntime();
        log.info("AI 模型已删除, modelId={}, operatorId={}", modelId, operatorId);
    }

    @Override
    public List<AiDefaultModelResponse> listDefaults() {
        Map<Long, AiModelEntity> modelMap = modelMapper.selectList(null).stream()
                .collect(Collectors.toMap(AiModelEntity::getId, m -> m));
        Map<String, Long> defaults = defaultModelMapper.selectList(null).stream()
                .collect(Collectors.toMap(AiDefaultModelEntity::getTypeCode,
                        AiDefaultModelEntity::getModelId));
        Map<Long, String> providerNames = providerNameMap();
        List<AiDefaultModelResponse> result = new ArrayList<>();
        for (String typeCode : List.of(TYPE_CHAT, TYPE_VISION, TYPE_ASR, TYPE_EMBEDDING)) {
            Long modelId = defaults.get(typeCode);
            AiModelEntity model = modelId == null ? null : modelMap.get(modelId);
            result.add(new AiDefaultModelResponse(typeCode, typeName(typeCode),
                    model == null ? null
                            : AiModelResponse.from(model, providerNames.getOrDefault(model.getProviderId(),
                            "未知供应商"))));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiDefaultModelResponse setDefault(Long operatorId, AiDefaultModelSetRequest request) {
        String typeCode = request.typeCode();
        AiDefaultModelEntity existing = defaultModelMapper.selectOne(new LambdaQueryWrapper<AiDefaultModelEntity>()
                .eq(AiDefaultModelEntity::getTypeCode, typeCode)
                .last("LIMIT 1"));
        if (request.modelId() == null) {
            if (existing != null) {
                defaultModelMapper.deleteById(existing.getId());
            }
        } else {
            AiModelEntity model = requireModel(request.modelId());
            if (existing == null) {
                AiDefaultModelEntity def = new AiDefaultModelEntity();
                def.setTypeCode(typeCode);
                def.setModelId(model.getId());
                defaultModelMapper.insert(def);
            } else {
                existing.setModelId(model.getId());
                defaultModelMapper.updateById(existing);
            }
        }
        aiConfigService.refreshRuntime();
        log.info("AI 默认模型已调整, type={}, modelId={}, operatorId={}", typeCode, request.modelId(), operatorId);
        return listDefaults().stream()
                .filter(def -> def.typeCode().equals(typeCode))
                .findFirst().orElseThrow();
    }

    /** 连通性检查/拉取模型共用的短超时客户端（显式直连，绕过系统代理避免境外绕行） */
    private RestClient modelsClient(AiProviderEntity provider) {
        HttpClient httpClient = HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .connectTimeout(CHECK_CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(CHECK_READ_TIMEOUT);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(provider.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey())
                .build();
    }

    private void persistCheckResult(AiProviderEntity provider, boolean ok, String result, LocalDateTime checkedAt) {
        AiProviderEntity update = new AiProviderEntity();
        update.setId(provider.getId());
        update.setLastCheckAt(checkedAt);
        update.setLastCheckOk(ok);
        update.setLastCheckResult(result.length() > 255 ? result.substring(0, 255) : result);
        try {
            providerMapper.updateById(update);
        } catch (Exception exception) {
            log.warn("连通性检查结果落库失败, providerId={}", provider.getId(), exception);
        }
    }

    private void clearDefaultsReferencing(List<Long> modelIds) {
        List<AiDefaultModelEntity> refs = defaultModelMapper.selectList(new LambdaQueryWrapper<AiDefaultModelEntity>()
                .in(AiDefaultModelEntity::getModelId, modelIds));
        if (!refs.isEmpty()) {
            defaultModelMapper.deleteByIds(refs.stream().map(AiDefaultModelEntity::getId).toList());
            log.info("已清除引用被删模型的默认模型, count={}", refs.size());
        }
    }

    private Map<Long, Long> countModelsByProvider() {
        Map<Long, Long> counts = new HashMap<>();
        modelMapper.selectList(null)
                .forEach(model -> counts.merge(model.getProviderId(), 1L, Long::sum));
        return counts;
    }

    private Map<Long, String> providerNameMap() {
        return providerMapper.selectList(null).stream()
                .collect(Collectors.toMap(AiProviderEntity::getId, AiProviderEntity::getProviderName));
    }

    private AiProviderEntity requireProvider(Long providerId) {
        AiProviderEntity provider = providerMapper.selectById(providerId);
        if (provider == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "AI 供应商不存在");
        }
        return provider;
    }

    private AiModelEntity requireModel(Long modelId) {
        AiModelEntity model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "AI 模型不存在");
        }
        return model;
    }

    private String normalizeProviderCode(String requested, String providerName) {
        String candidate = StringUtils.hasText(requested) ? requested.trim().toLowerCase(Locale.ROOT)
                : slugify(providerName);
        if (!StringUtils.hasText(candidate)) {
            candidate = "provider-" + System.currentTimeMillis();
        }
        if (!VALID_PROVIDER_CODE.matcher(candidate).matches()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "供应商编码仅支持小写字母/数字/短横线，且以字母或数字开头: " + candidate);
        }
        return candidate;
    }

    private String slugify(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "服务地址需以 http(s):// 开头");
        }
        return normalized;
    }

    private String typeName(String typeCode) {
        return switch (typeCode) {
            case TYPE_VISION -> "图片识别";
            case TYPE_ASR -> "语音识别";
            case TYPE_EMBEDDING -> "向量模型";
            default -> "聊天模型";
        };
    }

    private String rootMessage(Exception exception) {
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
