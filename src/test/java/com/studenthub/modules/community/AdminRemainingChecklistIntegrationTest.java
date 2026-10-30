package com.studenthub.modules.community;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 本轮补齐项接口回归：H01 请假、C01 社区概览、C12 置顶、C13 计划库、U04 审核、I01 成本。 */
class AdminRemainingChecklistIntegrationTest extends AdminApiTestSupport {

    @Test
    void leaveDedicatedApiAndPermission() throws Exception {
        String admin = login("admin");
        String requester = login("requester");

        JsonNode page = ok(getJson("/api/admin/leave-requests", admin, Map.of("pageSize", "5")));
        assertNotNull(page.path("records"));
        JsonNode stats = ok(getJson("/api/admin/leave-requests/stats", admin, Map.of()));
        assertTrue(stats.hasNonNull("pending") && stats.hasNonNull("approved")
                && stats.hasNonNull("cancelled"), "请假统计字段应完整");

        call(get("/api/admin/leave-requests").header("Authorization", "Bearer " + requester), 403);
        call(get("/api/admin/leave-requests/stats").header("Authorization", "Bearer " + requester), 403);
    }

    @Test
    void communityOverviewRangeAndCsv() throws Exception {
        String admin = login("admin");
        JsonNode seven = ok(getJson("/api/admin/community/overview", admin, Map.of("days", "7")));
        assertEquals(7, seven.path("days").asInt());
        assertEquals(7, seven.path("trend").size());
        assertEquals(seven.path("activeUsers").asLong(), seven.path("activeUsers7d").asLong());
        assertNotNull(seven.path("topics"));

        JsonNode thirty = ok(getJson("/api/admin/community/overview", admin, Map.of("days", "30")));
        assertEquals(30, thirty.path("trend").size());
        call(get("/api/admin/community/overview").param("days", "14")
                .header("Authorization", "Bearer " + admin), 400);

        byte[] csv = raw(get("/api/admin/community/overview/export").param("days", "7")
                .header("Authorization", "Bearer " + admin), 200);
        assertTrue(csv.length > 3);
        assertEquals((byte) 0xEF, csv[0]);
        assertEquals((byte) 0xBB, csv[1]);
        assertEquals((byte) 0xBF, csv[2]);
        String text = new String(csv, StandardCharsets.UTF_8);
        assertTrue(text.contains("时间范围") && text.contains("每日发帖趋势"), "CSV 应包含区间与趋势");
    }

    @Test
    void postPinUnpinAndAdPlanCrud() throws Exception {
        String admin = login("admin");
        String requester = login("requester");
        long sectionId = ok(getJson("/api/admin/community/sections", admin, Map.of()))
                .get(0).path("sectionId").asLong();
        Map<String, Object> postBody = new LinkedHashMap<>();
        postBody.put("sectionId", sectionId);
        postBody.put("postType", "DYNAMIC");
        postBody.put("content", "置顶接口回归帖子 " + System.currentTimeMillis());
        String postId = ok(postJson("/api/app/community/posts", requester, postBody)).path("postId").asText();
        ok(postJson("/api/admin/community/posts/" + postId + "/pin", admin, Map.of("pinned", true)));
        JsonNode pinned = ok(getJson("/api/admin/community/posts", admin, Map.of("pageSize", "5")));
        boolean found = false;
        for (JsonNode row : pinned.path("records")) {
            if (postId.equals(row.path("postId").asText())) {
                found = row.path("pinned").asBoolean();
            }
        }
        assertTrue(found, "置顶后列表应回显 pinned=true");
        ok(postJson("/api/admin/community/posts/" + postId + "/pin", admin, Map.of("pinned", false)));

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planName", "接口回归投放计划");
        plan.put("objective", "AWARENESS");
        plan.put("slot", "FEED");
        plan.put("startAt", "2026-10-20T00:00:00");
        plan.put("endAt", "2026-11-20T00:00:00");
        plan.put("status", "DRAFT");
        plan.put("remark", "integration-test");
        JsonNode created = ok(postJson("/api/admin/community/ad-plans", admin, plan));
        String planId = created.path("planId").asText();
        assertFalse(planId.isBlank());

        JsonNode plans = ok(getJson("/api/admin/community/ad-plans", admin, Map.of("pageSize", "20")));
        boolean listed = false;
        for (JsonNode row : plans.path("records")) {
            if (planId.equals(row.path("planId").asText())) {
                listed = true;
            }
        }
        assertTrue(listed, "新建投放计划应可分页查询");
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/admin/community/ad-plans/" + planId)
                .header("Authorization", "Bearer " + admin), 200);
    }

    @Test
    void identityReviewTemplatesAndDetail() throws Exception {
        String admin = login("admin");
        JsonNode templates = ok(getJson("/api/admin/identity-review/reason-templates", admin, Map.of()));
        assertTrue(templates.size() >= 1, "应有内置拒绝原因模板");

        JsonNode users = ok(getJson("/api/admin/users", admin, Map.of("pageSize", "1")));
        assertTrue(users.path("total").asLong() > 0);
        String userId = users.path("records").get(0).path("userId").asText();
        JsonNode detail = ok(getJson("/api/admin/identity-review/users/" + userId, admin, Map.of()));
        assertEquals(userId, detail.path("userId").asText());
        assertNotNull(detail.path("materials"));
        assertNotNull(detail.path("timeline"));
        assertFalse(detail.path("rejectReasons").isEmpty(), "详情应返回拒绝模板");
    }

    @Test
    void aiCostOverviewAndPricing() throws Exception {
        String admin = login("admin");
        String requester = login("requester");
        JsonNode cost = ok(getJson("/api/admin/ai/usage/cost", admin, Map.of("days", "7")));
        assertEquals(7, cost.path("trend").size());
        assertNotNull(cost.path("kpis").path("pricingConfigured"));
        JsonNode models = cost.path("models");
        assertTrue(models.size() > 0, "应存在模型配置");
        String modelId = models.get(0).path("modelId").asText();

        ok(postJson("/api/admin/ai/models/" + modelId + "/pricing", admin,
                Map.of("inputPricePer1k", 1.5, "outputPricePer1k", 2.5)));
        JsonNode priced = ok(getJson("/api/admin/ai/usage/cost", admin, Map.of("days", "7")));
        boolean configured = false;
        for (JsonNode model : priced.path("models")) {
            if (modelId.equals(model.path("modelId").asText())) {
                configured = model.path("pricingConfigured").asBoolean();
            }
        }
        assertTrue(configured, "保存单价后模型应显示已配置");

        // 清理单价，避免影响其他测试的成本断言
        ok(postJson("/api/admin/ai/models/" + modelId + "/pricing", admin, Map.of()));
        call(get("/api/admin/ai/usage/cost").header("Authorization", "Bearer " + requester), 403);
    }
}
