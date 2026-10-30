package com.studenthub.modules.dict.service;

import com.studenthub.modules.dict.controller.dto.DictImportResult;
import org.springframework.web.multipart.MultipartFile;

/** 字典 CSV 导入导出。 */
public interface DictCsvService {

    byte[] exportCsv();

    DictImportResult importCsv(MultipartFile file, boolean overwrite);
}
