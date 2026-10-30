package com.studenthub.modules.community;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * 管理端社区深度能力集成测试（V71）：
 * 帖子批量下架/恢复 → 举报 SLA/证据 → 举报批量处置 + 模板 → 申诉批量复核 →
 * 分区批量排序 → 话题推荐/合并 → 屏蔽词正则与命中统计。
 * 全部走 Controller→Service→Mapper + H2 真实库，不 mock。
 */
class AdminCommunityContentOpsIntegrationTest extends AdminApiTestSupport {

    @Test
    void batchPostStatusReportsAppealsSectionsTopicsAndBannedWords() throws Exception {
        String admin = login("admin");
        String requester = login("requester");

        JsonNode sections = ok(getJson("/api/admin/community/sections", admin, Map.of()));
        assertTrue(sections.size() >= 2, "开发种子应至少有 2 个分区");
        long sectionId = sections.get(0).path("sectionId").asLong();

        // ---------------- 1) 帖子批量下架 / 恢复（C02） ----------------
        long p1 = createPost(requester, sectionId, "批量处置测试甲：内容正常", null);
        long p2 = createPost(requester, sectionId, "批量处置测试乙：内容正常", null);
        long p3 = createPost(requester, sectionId, "批量处置测试丙：内容正常", null);

        JsonNode hideResult = ok(postJson("/api/admin/community/posts/batch-status", admin,
                Map.of("postIds", List.of(p1, p2, p3, 999999999L), "action", "HIDE",
                        "reason", "集成测试批量下架")));
        assertEquals(3, hideResult.path("updated").asInt());
        assertEquals(1, hideResult.path("skipped").asInt(), "不存在的帖子应计入 skipped");
        assertTrue(hideResult.path("skippedReasons").size() >= 1);

        JsonNode hiddenPage = ok(getJson("/api/admin/community/posts", admin,
                Map.of("status", "HIDDEN", "pageSize", "100", "keyword", "批量处置测试")));
        assertEquals(3, hiddenPage.path("total").asInt());

        JsonNode restoreResult = ok(postJson("/api/admin/community/posts/batch-status", admin,
                Map.of("postIds", List.of(p1, p2, p3), "action", "RESTORE")));
        assertEquals(3, restoreResult.path("updated").asInt());

        JsonNode restoreAgain = ok(postJson("/api/admin/community/posts/batch-status", admin,
                Map.of("postIds", List.of(p1, p2, p3), "action", "RESTORE")));
        assertEquals(0, restoreAgain.path("updated").asInt());
        assertEquals(3, restoreAgain.path("skipped").asInt(), "未下架帖子恢复应全部跳过");

        // 参数校验：空列表 / 非法动作
        call(postRequest("/api/admin/community/posts/batch-status", admin,
                Map.of("postIds", List.of(), "action", "HIDE")), 400);
        call(postRequest("/api/admin/community/posts/batch-status", admin,
                Map.of("postIds", List.of(p1), "action", "DELETE")), 400);

        // ---------------- 2) 举报 SLA / 证据 / 批量处置（C03） ----------------
        long reportP1 = report(admin, p1);
        JsonNode reportPage = ok(getJson("/api/admin/community/reports", admin,
                Map.of("status", "PENDING", "pageSize", "100")));
        JsonNode reportRow = findById(reportPage.path("records"), "id", reportP1);
        assertNotNull(reportRow, "举报应出现在待处理队列");
        assertTrue(reportRow.hasNonNull("dueAt"), "举报应带 24h 处理时限");
        long remaining = reportRow.path("remainingMinutes").asLong();
        assertTrue(remaining > 1300 && remaining <= 1440, "刚提交的举报剩余时限应接近 24h: " + remaining);
        assertFalse(reportRow.path("overdue").asBoolean());
        assertTrue(reportRow.path("sameTargetReportCount").asLong() >= 1, "同对象累计举报数应 ≥1");
        assertTrue(reportRow.path("evidenceUrls").isArray());

        JsonNode templates = ok(getJson("/api/admin/community/moderation-templates", admin,
                Map.of("templateType", "REPORT_NOTE")));
        assertTrue(templates.size() >= 4, "内置处理模板应有 4 条以上");
        long builtinTemplateId = templates.get(0).path("id").asLong();
        assertTrue(templates.get(0).path("builtin").asBoolean());

        JsonNode batchReject = ok(postJson("/api/admin/community/reports/batch-handle", admin,
                Map.of("reportIds", List.of(reportP1), "action", "REJECT",
                        "note", "证据不足，举报不成立", "templateId", builtinTemplateId)));
        assertEquals(1, batchReject.path("updated").asInt());

        JsonNode batchRejectAgain = ok(postJson("/api/admin/community/reports/batch-handle", admin,
                Map.of("reportIds", List.of(reportP1), "action", "REJECT", "note", "重复处理")));
        assertEquals(0, batchRejectAgain.path("updated").asInt());
        assertEquals(1, batchRejectAgain.path("skipped").asInt(), "已处理举报应跳过");

        long reportP2 = report(admin, p2);
        JsonNode batchAccept = ok(postJson("/api/admin/community/reports/batch-handle", admin,
                Map.of("reportIds", List.of(reportP2), "action", "ACCEPT",
                        "contentAction", "HIDE", "note", "举报成立，内容下架")));
        assertEquals(1, batchAccept.path("updated").asInt());
        JsonNode hiddenP2 = ok(getJson("/api/admin/community/posts/" + p2, admin, Map.of()));
        assertEquals("HIDDEN", hiddenP2.path("status").asText());

        // 起诉申诉 + 批量复核通过（C04）
        ok(postJson("/api/app/community/appeals", requester,
                Map.of("targetType", "POST", "targetId", p2, "reasonCode", "MISJUDGED",
                        "reason", "内容为正常学习讨论，申请复核")));
        JsonNode appealPage = ok(getJson("/api/admin/community/appeals", admin,
                Map.of("status", "PENDING", "pageSize", "100")));
        JsonNode appealRow = findById(appealPage.path("records"), "targetId", p2);
        assertNotNull(appealRow, "申诉提交后应出现在待处理队列");
        long appealId = appealRow.path("appealId").asLong();
        assertTrue(appealRow.hasNonNull("dueAt"), "申诉应带 24h 复核时限");
        JsonNode batchAppeal = ok(postJson("/api/admin/community/appeals/batch-handle", admin,
                Map.of("appealIds", List.of(appealId, 999999999L), "action", "APPROVE",
                        "note", "复核通过，恢复内容")));
        assertEquals(1, batchAppeal.path("updated").asInt());
        assertEquals(1, batchAppeal.path("skipped").asInt());
        JsonNode restoredP2 = ok(getJson("/api/admin/community/posts/" + p2, admin, Map.of()));
        assertEquals("ENABLED", restoredP2.path("status").asText());

        // 处理模板 CRUD：内置不可删，自建可删
        JsonNode createdTemplate = ok(postJson("/api/admin/community/moderation-templates", admin,
                Map.of("templateType", "REPORT_NOTE", "templateName", "集成测试模板",
                        "reasonCode", "ABUSE", "content", "测试模板正文", "sortOrder", 99,
                        "status", "ENABLED")));
        long customTemplateId = createdTemplate.path("id").asLong();
        assertFalse(createdTemplate.path("builtin").asBoolean());
        JsonNode updatedTemplate = ok(putJson("/api/admin/community/moderation-templates/" + customTemplateId,
                admin, Map.of("templateType", "REPORT_NOTE", "templateName", "集成测试模板-改",
                        "reasonCode", "ABUSE", "content", "测试模板正文（改）", "sortOrder", 98,
                        "status", "ENABLED")));
        assertEquals("集成测试模板-改", updatedTemplate.path("templateName").asText());
        call(delete("/api/admin/community/moderation-templates/" + builtinTemplateId)
                .header("Authorization", "Bearer " + admin), 400);
        deleteJson("/api/admin/community/moderation-templates/" + customTemplateId, admin);

        // ---------------- 3) 分区批量排序（C05） ----------------
        List<Long> orderedIds = new ArrayList<>();
        for (JsonNode section : sections) {
            orderedIds.add(0, section.path("sectionId").asLong());
        }
        JsonNode reorder = ok(postJson("/api/admin/community/sections/reorder", admin,
                Map.of("sectionIds", orderedIds)));
        assertEquals(orderedIds.size(), reorder.path("updated").asInt());
        JsonNode reordered = ok(getJson("/api/admin/community/sections", admin, Map.of()));
        assertEquals(orderedIds.get(0).longValue(), reordered.get(0).path("sectionId").asLong());
        assertEquals(1, reordered.get(0).path("sortOrder").asInt());

        // ---------------- 4) 话题推荐 / 合并（C06） ----------------
        long topicA = ok(postJson("/api/admin/community/topics", admin,
                Map.of("name", "集成话题A", "heat", 5, "status", "ENABLED"))).path("topicId").asLong();
        long topicB = ok(postJson("/api/admin/community/topics", admin,
                Map.of("name", "集成话题B", "heat", 7, "status", "ENABLED"))).path("topicId").asLong();
        long topicPost = createPost(requester, sectionId, "带话题的帖子：集成话题A", List.of("集成话题A"));
        JsonNode mergedTopic = ok(postJson("/api/admin/community/topics/" + topicA + "/merge", admin,
                Map.of("targetTopicId", topicB)));
        assertEquals(topicB, mergedTopic.path("topicId").asLong());
        assertEquals(13, mergedTopic.path("heat").asInt(), "创建帖子使话题A热度+1，合并后热度应累加");
        JsonNode topicsAfterMerge = ok(getJson("/api/admin/community/topics", admin, Map.of()));
        JsonNode sourceTopic = findById(topicsAfterMerge, "topicId", topicA);
        assertNotNull(sourceTopic);
        assertEquals(topicB, sourceTopic.path("mergedIntoId").asLong());
        assertEquals("DISABLED", sourceTopic.path("status").asText());

        JsonNode recommended = ok(postJson("/api/admin/community/topics/" + topicB + "/recommend?recommended=true",
                admin, null));
        assertTrue(recommended.path("recommended").asBoolean());
        JsonNode topicStats = ok(getJson("/api/admin/community/topics/" + topicB + "/stats", admin, Map.of()));
        assertTrue(topicStats.path("postCount").asLong() >= 1, "合并后目标话题应包含迁入帖子");
        assertNotNull(topicPost);

        // ---------------- 5) 屏蔽词：字面量 / 正则 / 命中统计（C07） ----------------
        long literalId = ok(postJson("/api/admin/community/banned-words", admin,
                Map.of("word", "违禁测试词", "remark", "集成测试", "status", "ENABLED",
                        "matchType", "LITERAL"))).path("id").asLong();
        long hiddenByLiteral = createPost(requester, sectionId, "正文中包含违禁测试词，应被自动隐藏", null);
        JsonNode literalPost = ok(getJson("/api/admin/community/posts/" + hiddenByLiteral, admin, Map.of()));
        assertEquals("HIDDEN", literalPost.path("status").asText(), "命中屏蔽词应自动隐藏");

        long regexId = ok(postJson("/api/admin/community/banned-words", admin,
                Map.of("word", "测试(广告|营销)", "remark", "正则", "status", "ENABLED",
                        "matchType", "REGEX"))).path("id").asLong();
        long hiddenByRegex = createPost(requester, sectionId, "这里出现测试广告字样，应命中正则", null);
        JsonNode regexPost = ok(getJson("/api/admin/community/posts/" + hiddenByRegex, admin, Map.of()));
        assertEquals("HIDDEN", regexPost.path("status").asText(), "正则命中应自动隐藏");

        JsonNode words = ok(getJson("/api/admin/community/banned-words", admin, Map.of()));
        JsonNode literalRow = findById(words, "id", literalId);
        assertNotNull(literalRow);
        assertTrue(literalRow.path("hitCount").asInt() >= 1, "字面量命中次数应累计");
        assertTrue(literalRow.hasNonNull("lastHitAt"));
        JsonNode regexRow = findById(words, "id", regexId);
        assertNotNull(regexRow);
        assertTrue(regexRow.path("hitCount").asInt() >= 1, "正则命中次数应累计");

        // 非法正则：语法错误 / 嵌套量词 / 过长 都必须在保存前被拒绝
        call(postRequest("/api/admin/community/banned-words", admin,
                Map.of("word", "(", "status", "ENABLED", "matchType", "REGEX")), 400);
        call(postRequest("/api/admin/community/banned-words", admin,
                Map.of("word", "(a+)+$", "status", "ENABLED", "matchType", "REGEX")), 400);
        call(postRequest("/api/admin/community/banned-words", admin,
                Map.of("word", "a".repeat(201), "status", "ENABLED", "matchType", "REGEX")), 400);

        // 导出屏蔽词包含新增列（匹配方式 / 命中次数）
        byte[] exported = raw(get("/api/admin/community/banned-words/export")
                .header("Authorization", "Bearer " + admin), 200);
        assertTrue(exported.length > 0);

        // 清理，避免影响其它测试
        deleteJson("/api/admin/community/banned-words/" + literalId, admin);
        deleteJson("/api/admin/community/banned-words/" + regexId, admin);
        deleteJson("/api/admin/community/topics/" + topicA, admin);
        deleteJson("/api/admin/community/topics/" + topicB, admin);
    }

    private long createPost(String token, long sectionId, String content, List<String> topics) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sectionId", sectionId);
        body.put("postType", "DYNAMIC");
        body.put("content", content);
        if (topics != null) {
            body.put("topics", topics);
        }
        return ok(postJson("/api/app/community/posts", token, body)).path("postId").asLong();
    }

    private long report(String reporterToken, long postId) throws Exception {
        ok(postJson("/api/app/community/reports", reporterToken,
                Map.of("targetType", "POST", "targetId", postId, "reasonCode", "ABUSE",
                        "reasonDetail", "集成测试举报")));
        JsonNode page = ok(getJson("/api/admin/community/reports", reporterToken,
                Map.of("pageSize", "100")));
        JsonNode row = null;
        for (JsonNode item : page.path("records")) {
            if (item.path("targetId").asLong() == postId) {
                row = item;
            }
        }
        assertNotNull(row, "举报提交后应出现在列表");
        return row.path("id").asLong();
    }

    private JsonNode findById(JsonNode array, String field, long id) {
        for (JsonNode item : array) {
            if (item.path(field).asLong() == id) {
                return item;
            }
        }
        return null;
    }
}
