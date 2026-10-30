package com.studenthub.modules.identity.service;

import com.studenthub.modules.identity.controller.dto.AdminUserImportResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户批量导入（U07）：CSV 模板、预览、同步导入。
 *
 * <p>本期为同步实现：一次请求内完成解析、校验、落库并返回逐行结果，不创建异步任务、不持久化任务记录；
 * 单次最多 500 行数据，超过则拒绝。文件编码要求 UTF-8（支持带 BOM，Excel 另存为「CSV UTF-8」即可）。</p>
 */
public interface AdminUserImportService {

    /**
     * CSV 模板（UTF-8 + BOM，Excel 直接可打开）。
     */
    byte[] templateCsv();

    /**
     * 仅预览：不写库，返回每行将执行的动作（CREATE/UPDATE/SKIP/ERROR）。
     *
     * @param strategy 冲突策略：SKIP 或 UPDATE
     */
    AdminUserImportResult preview(MultipartFile file, String strategy);

    /**
     * 正式导入：同步落库并返回逐行结果。
     *
     * @param operatorId 操作人 ID（写入 created_by/updated_by）
     * @param strategy   冲突策略：SKIP 或 UPDATE
     */
    AdminUserImportResult execute(Long operatorId, MultipartFile file, String strategy);
}
