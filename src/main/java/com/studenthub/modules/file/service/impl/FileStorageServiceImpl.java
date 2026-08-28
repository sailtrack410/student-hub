package com.studenthub.modules.file.service.impl;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.storage.BinaryStorage;
import com.studenthub.modules.file.config.FileProperties;
import com.studenthub.modules.file.entity.FileStorageEntity;
import com.studenthub.modules.file.mapper.FileStorageMapper;
import com.studenthub.modules.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/pjpeg", "image/png", "image/webp");
    private static final String SIGN_ALGORITHM = "HmacSHA256";

    private final FileStorageMapper fileStorageMapper;
    private final FileProperties fileProperties;
    private final BinaryStorage binaryStorage;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoredFile store(byte[] content, String originalName, String contentType, Long uploaderId) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "上传文件不能为空");
        }
        long maxBytes = (long) fileProperties.getMaxFileSizeMb() * 1024 * 1024;
        if (content.length > maxBytes) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "文件不能超过 " + fileProperties.getMaxFileSizeMb() + "MB");
        }
        String extension = resolveExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "仅支持 jpg、png、webp 图片");
        }
        if (contentType != null && !contentType.isBlank()
                && !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "仅支持上传图片文件");
        }
        String detectedType = detectContentType(content);
        if (detectedType == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文件内容不是有效图片");
        }

        String storageKey = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String objectKey = objectKeyOf(Instant.now(), storageKey);
        binaryStorage.put(objectKey, content, detectedType);

        FileStorageEntity entity = new FileStorageEntity();
        entity.setStorageKey(storageKey);
        entity.setOriginalName(sanitizeName(originalName, extension));
        entity.setContentType(detectedType);
        entity.setFileSize((long) content.length);
        entity.setUploaderId(uploaderId);
        fileStorageMapper.insert(entity);

        log.info("文件上传成功, fileId={}, size={}, uploaderId={}", entity.getId(), content.length, uploaderId);
        return new StoredFile(entity.getId(), signViewPath(entity.getId()), entity.getOriginalName());
    }

    @Override
    public FileStorageEntity requireBySignature(Long fileId, long expiresAtEpochSeconds, String signature) {
        if (!verifySignature(fileId, expiresAtEpochSeconds, signature)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "访问链接无效或已过期");
        }
        FileStorageEntity entity = fileStorageMapper.selectById(fileId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return entity;
    }

    @Override
    public String signViewPath(Long fileId) {
        long expires = Instant.now().plusSeconds(fileProperties.getViewTtlMinutes() * 60L).getEpochSecond();
        return "/api/common/files/view/" + fileId + "?expires=" + expires + "&sign=" + sign(fileId, expires);
    }

    @Override
    public List<String> signViewPaths(List<Long> fileIds) {
        return fileIds == null ? List.of() : fileIds.stream().map(this::signViewPath).toList();
    }

    /**
     * 对象键：{存储日 epoch 天数}/{storageKey}，本地与 MinIO 一致；写入用真实 UTC 时刻。
     */
    private String objectKeyOf(Instant createdAt, String storageKey) {
        return Math.floorDiv(createdAt.toEpochMilli(), 24 * 3600L * 1000) + "/" + storageKey;
    }

    /**
     * 读取时由记录时间还原真实时刻（createdAt 为系统时区墙钟时间）后重建对象键。
     */
    private String objectKeyOf(FileStorageEntity entity) {
        Instant instant = entity.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant();
        return objectKeyOf(instant, entity.getStorageKey());
    }

    @Override
    public SignedContent openBySignature(Long fileId, long expiresAtEpochSeconds, String signature) {
        FileStorageEntity entity = requireBySignature(fileId, expiresAtEpochSeconds, signature);
        InputStreamResource resource = new InputStreamResource(binaryStorage.get(objectKeyOf(entity)));
        return new SignedContent(resource, entity.getContentType(), entity.getFileSize());
    }

    @Override
    public com.fasterxml.jackson.databind.JsonNode replaceImageValuesWithSignedUrls(
            com.fasterxml.jackson.databind.JsonNode formData, com.fasterxml.jackson.databind.JsonNode formSchema) {
        if (formData == null || !formData.isObject() || formSchema == null || !formSchema.isArray()) {
            return formData;
        }
        Set<String> imageKeys = new java.util.HashSet<>();
        formSchema.forEach(field -> {
            if ("image".equals(field.path("type").asText())) {
                imageKeys.add(field.path("key").asText());
            }
        });
        com.fasterxml.jackson.databind.node.ObjectNode result = ((com.fasterxml.jackson.databind.node.ObjectNode) formData).deepCopy();
        boolean changed = false;
        for (String key : imageKeys) {
            com.fasterxml.jackson.databind.JsonNode value = result.get(key);
            if (value == null || !value.isArray() || value.isEmpty()) {
                continue;
            }
            com.fasterxml.jackson.databind.node.ArrayNode urls = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> {
                try {
                    urls.add(signViewPath(Long.parseLong(item.asText())));
                } catch (NumberFormatException ignored) {
                    // 已是路径或非法值，跳过
                }
            });
            if (!urls.isEmpty()) {
                result.set(key, urls);
                changed = true;
            }
        }
        return changed ? result : formData;
    }

    private boolean verifySignature(Long fileId, long expiresAtEpochSeconds, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        if (expiresAtEpochSeconds < Instant.now().getEpochSecond()) {
            return false;
        }
        return constantTimeEquals(sign(fileId, expiresAtEpochSeconds), signature);
    }

    private String sign(Long fileId, long expiresAtEpochSeconds) {
        try {
            Mac mac = Mac.getInstance(SIGN_ALGORITHM);
            mac.init(new SecretKeySpec(fileProperties.getSignSecret().getBytes(StandardCharsets.UTF_8),
                    SIGN_ALGORITHM));
            byte[] digest = mac.doFinal((fileId + ":" + expiresAtEpochSeconds).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("签名计算失败", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String resolveExtension(String originalName) {
        int dotIndex = originalName == null ? -1 : originalName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 按文件头魔数识别真实类型，防止改扩展名绕过；非图片返回 null。
     * JPEG/PNG 魔数含非 ASCII 字节，必须按字节比较。
     */
    private String detectContentType(byte[] content) {
        if (content == null || content.length < 12) {
            return null;
        }
        if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((content[0] & 0xFF) == 0x89 && (content[1] & 0xFF) == 0x50
                && (content[2] & 0xFF) == 0x4E && (content[3] & 0xFF) == 0x47) {
            return "image/png";
        }
        if (content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private String sanitizeName(String originalName, String extension) {
        String name = originalName == null ? "file" : originalName.replaceAll("[\\r\\n\\\\/]", "_");
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }
}
