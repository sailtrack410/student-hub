package com.studenthub.modules.dict.service.impl;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.dict.controller.dto.DictImportResult;
import com.studenthub.modules.dict.controller.dto.DictItemResponse;
import com.studenthub.modules.dict.controller.dto.DictItemUpsertRequest;
import com.studenthub.modules.dict.controller.dto.DictTypeResponse;
import com.studenthub.modules.dict.controller.dto.DictTypeUpsertRequest;
import com.studenthub.modules.dict.service.DictCsvService;
import com.studenthub.modules.dict.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 字典 CSV 导入导出：UTF-8，首行表头，支持引号转义；逐行校验，单行失败不影响其他行。 */
@Service
@RequiredArgsConstructor
public class DictCsvServiceImpl implements DictCsvService {

    private static final String HEADER = "typeCode,typeName,itemCode,itemLabel,itemValue,sortOrder,status,extra,remark";
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private final DictService dictService;

    @Override
    public byte[] exportCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append(HEADER).append('\n');
        for (DictTypeResponse type : dictService.listTypes("", null)) {
            if (!"MANUAL".equals(type.source())) {
                continue;
            }
            List<DictItemResponse> items = dictService.listItems(type.typeCode(), true);
            if (items.isEmpty()) {
                csv.append(csv(type.typeCode())).append(',').append(csv(type.typeName())).append(',')
                        .append(",,,0,ENABLED,,").append(csv(type.remark())).append('\n');
            }
            for (DictItemResponse item : items) {
                csv.append(csv(type.typeCode())).append(',')
                        .append(csv(type.typeName())).append(',')
                        .append(csv(item.itemCode())).append(',')
                        .append(csv(item.itemLabel())).append(',')
                        .append(csv(item.itemValue())).append(',')
                        .append(item.sortOrder() == null ? 0 : item.sortOrder()).append(',')
                        .append(csv(item.status())).append(',')
                        .append(csv(item.extra())).append(',')
                        .append(csv(item.remark())).append('\n');
            }
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public DictImportResult importCsv(MultipartFile file, boolean overwrite) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "请上传 CSV 文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "CSV 文件不能超过 2MB");
        }
        List<String> lines;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().toList();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "CSV 读取失败");
        }
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "CSV 内容为空");
        }
        int start = 0;
        String first = lines.get(0);
        if (first != null && first.startsWith("\uFEFF")) {
            first = first.substring(1);
        }
        if (first != null && first.toLowerCase().contains("typecode")) {
            start = 1;
        }

        int total = 0;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        Map<String, DictTypeResponse> typeCache = new HashMap<>();
        for (DictTypeResponse type : dictService.listTypes("", null)) {
            if ("MANUAL".equals(type.source())) {
                typeCache.put(type.typeCode(), type);
            }
        }
        Map<String, Map<String, DictItemResponse>> itemCache = new HashMap<>();

        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!StringUtils.hasText(line)) {
                continue;
            }
            total++;
            List<String> columns;
            try {
                columns = parseLine(line);
            } catch (IllegalArgumentException exception) {
                errors.add("第 " + (i + 1) + " 行: " + exception.getMessage());
                skipped++;
                continue;
            }
            while (columns.size() < 9) {
                columns.add("");
            }
            String typeCode = columns.get(0).trim();
            String typeName = columns.get(1).trim();
            String itemCode = columns.get(2).trim();
            String itemLabel = columns.get(3).trim();
            String itemValue = columns.get(4).trim();
            String sortOrder = columns.get(5).trim();
            String status = columns.get(6).trim();
            String extra = columns.get(7).trim();
            String remark = columns.get(8).trim();
            int lineNo = i + 1;
            if (!StringUtils.hasText(typeCode) || !StringUtils.hasText(typeName)
                    || !StringUtils.hasText(itemCode) || !StringUtils.hasText(itemLabel)) {
                errors.add("第 " + lineNo + " 行: typeCode/typeName/itemCode/itemLabel 不能为空");
                skipped++;
                continue;
            }
            try {
                DictTypeResponse type = typeCache.get(typeCode);
                if (type == null) {
                    type = dictService.createType(new DictTypeUpsertRequest(typeCode, typeName, null, "ENABLED"));
                    typeCache.put(typeCode, type);
                } else if (!typeName.equals(type.typeName())) {
                    type = dictService.updateType(type.id(),
                            new DictTypeUpsertRequest(typeCode, typeName, type.remark(), type.status()));
                    typeCache.put(typeCode, type);
                }
                Map<String, DictItemResponse> items = itemCache.computeIfAbsent(typeCode,
                        code -> {
                            Map<String, DictItemResponse> byCode = new HashMap<>();
                            for (DictItemResponse item : dictService.listItems(code, true)) {
                                byCode.put(item.itemCode(), item);
                            }
                            return byCode;
                        });
                DictItemResponse existing = items.get(itemCode);
                int parsedSort = parseSort(sortOrder);
                String parsedStatus = normalizeStatus(status);
                DictItemUpsertRequest request = new DictItemUpsertRequest(typeCode, itemCode, itemLabel,
                        StringUtils.hasText(itemValue) ? itemValue : null, parsedSort, parsedStatus,
                        StringUtils.hasText(extra) ? extra : null, StringUtils.hasText(remark) ? remark : null);
                if (existing == null) {
                    DictItemResponse item = dictService.createItem(request);
                    items.put(itemCode, item);
                    created++;
                } else if (overwrite) {
                    DictItemResponse item = dictService.updateItem(existing.id(), request);
                    items.put(itemCode, item);
                    updated++;
                } else {
                    skipped++;
                }
            } catch (BusinessException exception) {
                errors.add("第 " + lineNo + " 行: " + exception.getMessage());
                skipped++;
            } catch (RuntimeException exception) {
                errors.add("第 " + lineNo + " 行: 导入失败");
                skipped++;
            }
        }
        dictService.refreshCache();
        return new DictImportResult(total, created, updated, skipped, errors);
    }

    private int parseSort(String raw) {
        if (!StringUtils.hasText(raw)) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "sortOrder 必须是整数");
        }
    }

    private String normalizeStatus(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "ENABLED";
        }
        String value = raw.trim().toUpperCase();
        if (!"ENABLED".equals(value) && !"DISABLED".equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "status 仅支持 ENABLED/DISABLED");
        }
        return value;
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** 解析单行 CSV：支持双引号包裹、逗号与转义双引号。 */
    static List<String> parseLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("引号未闭合");
        }
        result.add(current.toString());
        return result;
    }
}
