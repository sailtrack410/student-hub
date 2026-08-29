package com.studenthub.modules.file.service.impl;

import com.studenthub.modules.file.service.ImageCompressionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Locale;

/** 默认最大边 1600px，JPEG 质量 0.82；PNG 保留透明通道，WebP 不重复编码。 */
@Slf4j
@Service
public class ImageCompressionServiceImpl implements ImageCompressionService {

    private static final int MAX_EDGE = 1600;
    private static final int THUMBNAIL_MAX_EDGE = 480;
    private static final float JPEG_QUALITY = 0.82f;

    @Override
    public CompressedImage compress(byte[] content, String originalName, String contentType) {
        if (content == null || content.length == 0 || contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return new CompressedImage(content, originalName, contentType);
        }
        // ImageIO 原生不支持 WebP，交给后续魔数校验，避免损坏原图。
        if ("image/webp".equalsIgnoreCase(contentType)) {
            return new CompressedImage(content, originalName, contentType);
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            if (source == null) {
                return new CompressedImage(content, originalName, contentType);
            }
            int sourceEdge = Math.max(source.getWidth(), source.getHeight());
            boolean jpeg = "image/jpeg".equalsIgnoreCase(contentType)
                    || "image/jpg".equalsIgnoreCase(contentType)
                    || (originalName != null && originalName.toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg)$"));
            BufferedImage output = sourceEdge > MAX_EDGE ? resize(source, MAX_EDGE, !jpeg) : source;
            byte[] encoded = jpeg ? writeJpeg(output) : writePng(output);
            if (encoded.length >= content.length && sourceEdge <= MAX_EDGE) {
                return new CompressedImage(content, originalName, contentType);
            }
            String fileName = replaceExtension(originalName, jpeg ? "jpg" : "png");
            return new CompressedImage(encoded, fileName, jpeg ? "image/jpeg" : "image/png");
        } catch (Exception exception) {
            log.warn("图片压缩失败，保留原文件, name={}", originalName);
            return new CompressedImage(content, originalName, contentType);
        }
    }

    @Override
    public CompressedImage thumbnail(byte[] content, String originalName, String contentType) {
        if (content == null || content.length == 0 || contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")
                || "image/webp".equalsIgnoreCase(contentType)) {
            return new CompressedImage(content, originalName, contentType);
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            if (source == null) {
                return new CompressedImage(content, originalName, contentType);
            }
            boolean jpeg = "image/jpeg".equalsIgnoreCase(contentType)
                    || "image/jpg".equalsIgnoreCase(contentType)
                    || (originalName != null && originalName.toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg)$"));
            BufferedImage output = resize(source, THUMBNAIL_MAX_EDGE, !jpeg);
            byte[] encoded = jpeg ? writeJpeg(output) : writePng(output);
            String fileName = replaceExtension(originalName, jpeg ? "jpg" : "png");
            return new CompressedImage(encoded, fileName, jpeg ? "image/jpeg" : "image/png");
        } catch (Exception exception) {
            log.warn("缩略图生成失败，使用原文件, name={}", originalName);
            return new CompressedImage(content, originalName, contentType);
        }
    }

    private BufferedImage resize(BufferedImage source, int maxEdge, boolean alpha) {
        double scale = Math.min(1d, maxEdge / (double) Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }

    private byte[] writeJpeg(BufferedImage image) throws Exception {
        BufferedImage rgb = image.getType() == BufferedImage.TYPE_INT_RGB ? image : resize(image, Math.max(image.getWidth(), image.getHeight()), false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ImageIO.write(rgb, "jpg", output);
            return output.toByteArray();
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private byte[] writePng(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private String replaceExtension(String name, String extension) {
        if (name == null || name.isBlank()) {
            return "image." + extension;
        }
        return name.replaceFirst("(?i)\\.[a-z0-9]+$", "." + extension);
    }
}
