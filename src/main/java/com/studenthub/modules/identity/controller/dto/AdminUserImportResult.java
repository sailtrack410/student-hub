package com.studenthub.modules.identity.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 用户导入结果（U07）：同步执行，无异步任务与任务持久化。
 *
 * <p>dryRun=true 表示预览（不写库）；正式导入在同一请求内完成，逐行结果随响应返回。
 * 冲突策略 SKIP=已存在用户跳过；UPDATE=按 CSV 覆盖昵称/手机号/邮箱/学号/院系/身份/组织/角色。</p>
 */
@Schema(description = "用户导入结果")
public record AdminUserImportResult(
        @Schema(description = "冲突策略 SKIP/UPDATE") String strategy,
        @Schema(description = "是否仅预览（不写库）") boolean dryRun,
        @Schema(description = "数据行总数") int total,
        @Schema(description = "新建数") int created,
        @Schema(description = "更新数") int updated,
        @Schema(description = "跳过数") int skipped,
        @Schema(description = "失败数") int failed,
        @Schema(description = "逐行结果") List<AdminUserImportRow> rows) {
}
