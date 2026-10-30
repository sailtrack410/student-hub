package com.studenthub.modules.file.service;

import com.studenthub.modules.file.entity.FileStorageEntity;

import java.util.List;

public interface FileStorageService {

    /**
     * 校验并保存上传文件，返回文件 ID 与短时签名访问路径。
     */
    StoredFile store(byte[] content, String originalName, String contentType, Long uploaderId);

    /**
     * 校验并保存音频文件（mp3/aac/m4a/wav 等），供语音转写使用；返回文件 ID。
     */
    StoredFile storeAudio(byte[] content, String originalName, String contentType, Long uploaderId);

    /** 保存经严格扩展名、MIME 与大小校验的知识库文档。 */
    StoredFile storeKnowledgeDocument(byte[] content, String originalName, String contentType, Long uploaderId,
                                      int maxSizeMb);

    /**
     * 读取已存储文件；仅校验签名，签名 URL 本身即访问凭证。
     */
    FileStorageEntity requireBySignature(Long fileId, long expiresAtEpochSeconds, String signature);

    /**
     * 为指定文件生成短时签名访问路径（/api/common/files/view/{id}?expires=..&sign=..）。
     */
    String signViewPath(Long fileId);

    /**
     * 将 sys_user.avatar_url 解析成可直接展示的地址：
     * 本地上传的头像存的是文件 ID，换取短时签名路径；QQ 等外部头像存的是 http(s) 地址，原样返回。
     */
    String resolveAvatarUrl(String rawAvatar);

    /** 为列表首屏生成后端缩略图地址；缩略图由本地 API 从对象存储读取并压缩。 */
    String signThumbnailPath(Long fileId);

    List<String> signViewPaths(List<Long> fileIds);

    /**
     * 将表单数据中图片类型字段的文件 ID 数组替换为短时签名访问路径，用于详情展示。
     */
    com.fasterxml.jackson.databind.JsonNode replaceImageValuesWithSignedUrls(
            com.fasterxml.jackson.databind.JsonNode formData, com.fasterxml.jackson.databind.JsonNode formSchema);

    /**
     * 校验签名后打开文件内容，一次性返回流与元信息。
     */
    SignedContent openBySignature(Long fileId, long expiresAtEpochSeconds, String signature);

    /**
     * 按文件 ID 读取文件字节与内容类型（服务端内部使用，如 AI 识图/转写），
     * 不做签名校验，仅供已鉴权的内部流程按受控 fileId 使用。
     */
    StoredContent readContent(Long fileId);

    record SignedContent(org.springframework.core.io.InputStreamResource resource,
                         String contentType, long fileSize) {
    }

    record StoredContent(byte[] bytes, String contentType, String originalName) {
    }

    record StoredFile(Long fileId, String viewPath, String fileName) {
    }
}
