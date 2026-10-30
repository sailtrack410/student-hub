package com.studenthub.modules.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.studenthub.modules.community.AdminApiTestSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 审计 SYS10 集成测试（V71）：失败写操作留痕、完整 before JSON、
 * 结果状态筛选、操作人/资源回显与全量 CSV 导出（含权限校验）。
 */
class AdminAuditOpsIntegrationTest extends AdminApiTestSupport {

    @Test
    void failedWriteBeforeJsonAndCsvExport() throws Exception {
        String admin = login("admin");
        String requester = login("requester");

        // 1) 失败的写操作（业务 404）应留痕为 FAILED，并带错误摘要
        call(postRequest("/api/admin/community/content/POST/999999999/hide", admin,
                Map.of("reason", "集成测试失败审计")), 404);
        JsonNode failedPage = ok(getJson("/api/admin/audit-logs", admin,
                Map.of("resultStatus", "FAILED", "pageSize", "50")));
        assertTrue(failedPage.path("total").asInt() >= 1, "失败写操作应写入审计日志");
        JsonNode failedRow = failedPage.path("records").get(0);
        assertEquals("FAILED", failedRow.path("resultStatus").asText());
        assertNotNull(failedRow.path("errorMessage").asText());
        assertNotNull(failedRow.path("operatorName").asText());
        assertNotNull(failedRow.path("resourceType").asText());

        // 2) 成功的批量写操作应带 before JSON（帖子原状态）
        long sectionId = ok(getJson("/api/admin/community/sections", admin, Map.of()))
                .get(0).path("sectionId").asLong();
        Map<String, Object> postBody = new LinkedHashMap<>();
        postBody.put("sectionId", sectionId);
        postBody.put("postType", "DYNAMIC");
        postBody.put("content", "用于审计 before JSON 的测试帖子");
        long postId = ok(postJson("/api/app/community/posts", requester, postBody)).path("postId").asLong();
        ok(postJson("/api/admin/community/posts/batch-status", admin,
                Map.of("postIds", List.of(postId), "action", "HIDE", "reason", "审计测试下架")));

        JsonNode batchPage = ok(getJson("/api/admin/audit-logs", admin,
                Map.of("action", "ADMIN_BATCH_COMMUNITY_POST_STATUS", "pageSize", "20")));
        assertTrue(batchPage.path("total").asInt() >= 1, "批量处置应有审计记录");
        JsonNode batchRow = batchPage.path("records").get(0);
        assertEquals("SUCCESS", batchRow.path("resultStatus").asText());
        String beforeJson = batchRow.path("beforeJson").asText();
        assertTrue(beforeJson.contains("posts") && beforeJson.contains(String.valueOf(postId)),
                "before JSON 应包含帖子原状态: " + beforeJson);

        // 3) 全量 CSV 导出：BOM + 表头 + 数据行
        byte[] csv = raw(get("/api/admin/audit-logs/export")
                .header("Authorization", "Bearer " + admin), 200);
        assertTrue(csv.length > 3);
        assertEquals((byte) 0xEF, csv[0]);
        assertEquals((byte) 0xBB, csv[1]);
        assertEquals((byte) 0xBF, csv[2]);
        String text = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.contains("操作时间") && text.contains("变更前JSON"), "CSV 表头应完整");
        assertTrue(text.contains("ADMIN_BATCH_COMMUNITY_POST_STATUS"), "CSV 应包含批量处置记录");

        // 4) 权限：无 system:audit:export 的用户不可导出
        call(get("/api/admin/audit-logs/export").header("Authorization", "Bearer " + requester), 403);
        call(get("/api/admin/audit-logs").header("Authorization", "Bearer " + requester), 403);
    }
}
