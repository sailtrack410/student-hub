package com.studenthub.modules.file.service;

/**
 * 图片上传前的统一压缩入口。所有业务图片上传都应先经过该服务。
 */
public interface ImageCompressionService {

    CompressedImage compress(byte[] content, String originalName, String contentType);

    /** 生成列表展示用缩略图，最大边 480px。 */
    CompressedImage thumbnail(byte[] content, String originalName, String contentType);

    record CompressedImage(byte[] bytes, String fileName, String contentType) {
    }
}
