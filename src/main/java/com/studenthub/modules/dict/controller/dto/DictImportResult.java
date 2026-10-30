package com.studenthub.modules.dict.controller.dto;

import java.util.List;

/** 字典 CSV 导入结果：逐行错误不影响其他行。 */
public record DictImportResult(
        int total,
        int created,
        int updated,
        int skipped,
        List<String> errors) {
}
