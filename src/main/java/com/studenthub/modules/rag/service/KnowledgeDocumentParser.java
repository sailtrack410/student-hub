package com.studenthub.modules.rag.service;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component @RequiredArgsConstructor
public class KnowledgeDocumentParser {
    private final RagProperties properties;
    public List<Section> parse(byte[] bytes, String fileName) {
        try {
            String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            List<Section> result = switch (ext) {
                case "pdf" -> parsePdf(bytes);
                case "docx" -> parseDocx(bytes);
                case "txt", "md" -> parseText(bytes);
                default -> throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不支持的文档格式");
            };
            int chars = result.stream().mapToInt(s -> s.text().length()).sum();
            if (chars == 0) throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文档没有可解析的文字；扫描版 PDF 请先进行 OCR");
            if (chars > properties.getMaxParsedCharacters()) throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文档解析文字量超过限制");
            return result;
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文档解析失败：" + e.getMessage()); }
    }
    private List<Section> parsePdf(byte[] bytes) throws IOException {
        List<Section> out = new ArrayList<>();
        try (var doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page=1; page<=doc.getNumberOfPages(); page++) {
                stripper.setStartPage(page); stripper.setEndPage(page);
                String text = clean(stripper.getText(doc));
                if (!text.isBlank()) out.add(new Section("第"+page+"页", page, text));
            }
        }
        return out;
    }
    private List<Section> parseDocx(byte[] bytes) throws IOException {
        List<Section> out = new ArrayList<>(); String heading = null; StringBuilder body = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (var p : doc.getParagraphs()) {
                String text=clean(p.getText()); if(text.isBlank()) continue;
                boolean isHeading=p.getStyle()!=null && p.getStyle().toLowerCase(Locale.ROOT).startsWith("heading");
                if(isHeading) { if(body.length()>0) out.add(new Section(heading,null,body.toString())); heading=text; body.setLength(0); }
                else body.append(text).append('\n');
            }
        }
        if(body.length()>0) out.add(new Section(heading,null,body.toString())); return out;
    }
    private List<Section> parseText(byte[] bytes) {
        String text=clean(new String(bytes, StandardCharsets.UTF_8));
        return Arrays.stream(text.split("(?m)(?=^#{1,6}\\s+)")).filter(s->!s.isBlank()).map(s->{String[] lines=s.split("\\n",2);String h=lines[0].startsWith("#")?lines[0].replaceFirst("^#+\\s*",""):null;return new Section(h,null,s);}).toList();
    }
    private String clean(String text) { return text == null ? "" : text.replace("\u0000","").replaceAll("[ \\t]+"," ").replaceAll("\\n{3,}","\n\n").trim(); }
    public record Section(String titlePath, Integer page, String text) {}
}
