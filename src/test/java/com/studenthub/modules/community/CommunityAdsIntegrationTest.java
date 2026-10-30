package com.studenthub.modules.community;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 社区商业化链路集成测试（V66）：
 * 官方账号类型约束 → 官方直投广告 CRUD/上下架 → 混合信息流插入 →
 * 曝光/点击/落地/停留事件 → 跳出率可测与不可测（外部浏览器/Deeplink 返回 null）→ 频控与删除。
 * 使用 H2（MODE=MySQL）+ Flyway + MockMvc，走完整 Controller→Service→Mapper 链路。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunityAdsIntegrationTest {

    private static final long SECTION_ID = 6001L;
    private static final String JSESSION = "test-session";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void officialAccountsDirectAdsTrackingAndFeedChain() throws Exception {
        String admin = login("admin");
        String requester = login("requester");

        // ------------------------------------------------------------------
        // 1. 官方账号：类型校验、创建、列表、编辑、启停、权限
        // ------------------------------------------------------------------
        JsonNode human = ok(postJson("/api/admin/community/official-accounts", admin, accountBody(
                "OFFICIAL_HUMAN", "校学生会", "张老师", "学生会官方运营号", "校学生会", "APPROVED", null)));
        long humanId = human.path("userId").asLong();
        assertEquals("OFFICIAL_HUMAN", human.path("accountType").asText());
        assertEquals("APPROVED", human.path("verificationStatus").asText());
        assertTrue(human.path("official").asBoolean());
        assertTrue(human.path("userId").isTextual(), "雪花 ID 应按字符串下发");

        // 非法账号类型
        JsonNode badType = call(postRequest("/api/admin/community/official-accounts", admin, accountBody(
                "ROBOT", "非法账号", null, null, null, null, null)), 400);
        assertEquals("COMMON_400", badType.path("code").asText());
        // 人工运营号必须填写运营人
        JsonNode missingOperator = call(postRequest("/api/admin/community/official-accounts", admin, accountBody(
                "OFFICIAL_HUMAN", "缺少运营人", null, null, null, null, null)), 400);
        assertEquals("COMMON_400", missingOperator.path("code").asText());

        JsonNode auto = ok(postJson("/api/admin/community/official-accounts", admin, accountBody(
                "OFFICIAL_AUTO", "教务处", null, "学校官网通知自动发布主体", "教务处", "APPROVED", null)));
        long autoId = auto.path("userId").asLong();
        assertEquals("OFFICIAL_AUTO", auto.path("accountType").asText());

        JsonNode adService = ok(postJson("/api/admin/community/official-accounts", admin, accountBody(
                "AD_SERVICE", "校园便利店", "王店长", "校园合作商户推广号", null, "APPROVED", null)));
        long adServiceId = adService.path("userId").asLong();
        assertEquals("AD_SERVICE", adService.path("accountType").asText());
        assertFalse(adService.path("official").asBoolean(), "广告服务号不得伪装成官方");

        JsonNode accountPage = ok(getJson("/api/admin/community/official-accounts", admin,
                Map.of("pageNum", "1", "pageSize", "50")));
        assertTrue(accountPage.path("total").asInt() >= 3);
        boolean sawAdService = false;
        for (JsonNode row : accountPage.path("records")) {
            assertFalse("NORMAL".equals(row.path("accountType").asText()), "列表只返回官方类账号");
            if ("AD_SERVICE".equals(row.path("accountType").asText())) {
                sawAdService = true;
            }
        }
        assertTrue(sawAdService);

        JsonNode keywordPage = ok(getJson("/api/admin/community/official-accounts", admin,
                Map.of("pageNum", "1", "pageSize", "10", "keyword", "校学生会")));
        assertEquals(1, keywordPage.path("total").asInt());

        // 编辑：展示名/运营人/认证状态/启停
        JsonNode edited = ok(postJson("/api/admin/community/official-accounts/" + humanId, admin, accountBody(
                "OFFICIAL_HUMAN", "校学生会运营组", "李老师", "更新后的简介", "校学生会", "APPROVED", "DISABLED")));
        assertEquals("校学生会运营组", edited.path("displayName").asText());
        assertEquals("李老师", edited.path("operatorName").asText());
        assertEquals("DISABLED", edited.path("status").asText());

        JsonNode enabled = ok(postJson("/api/admin/community/official-accounts/" + humanId + "/status", admin,
                Map.of("status", "ENABLED")));
        assertEquals("ENABLED", enabled.path("status").asText());

        // 无社区运营权限的普通用户不可访问
        JsonNode forbidden = call(get("/api/admin/community/official-accounts")
                .header("Authorization", "Bearer " + requester), 403);
        assertEquals("AUTH_403", forbidden.path("code").asText());

        // ------------------------------------------------------------------
        // 2. 官方抓取号必须 OFFICIAL_AUTO
        // ------------------------------------------------------------------
        Map<String, Object> sourceBody = new LinkedHashMap<>();
        sourceBody.put("sourceName", "集成测试来源");
        sourceBody.put("listUrl", "https://www.mycc.edu.cn/index/tzgg.htm");
        sourceBody.put("sectionId", SECTION_ID);
        sourceBody.put("authorId", humanId);
        sourceBody.put("maxPages", 1);
        sourceBody.put("maxArticles", 1);
        sourceBody.put("maxAgeDays", 30);
        sourceBody.put("enabled", false);
        JsonNode humanSource = call(postRequest("/api/admin/official-feed/sources", admin, sourceBody), 400);
        assertEquals("COMMON_400", humanSource.path("code").asText());

        sourceBody.put("authorId", autoId);
        ok(postJson("/api/admin/official-feed/sources", admin, sourceBody));

        // 已绑定抓取来源的自动号不能改为人工号
        JsonNode blockedTypeChange = call(postRequest("/api/admin/community/official-accounts/" + autoId, admin,
                accountBody("OFFICIAL_HUMAN", "教务处", "赵老师", "改为人工", "教务处", "APPROVED", "ENABLED")), 409);
        assertEquals("REQUEST_409_01", blockedTypeChange.path("code").asText());
        // 保持 OFFICIAL_AUTO 时可以编辑其他字段
        JsonNode autoEdited = ok(postJson("/api/admin/community/official-accounts/" + autoId, admin,
                accountBody("OFFICIAL_AUTO", "教务处通知", null, "官网通知", "教务处", "APPROVED", "ENABLED")));
        assertEquals("教务处通知", autoEdited.path("displayName").asText());

        // ------------------------------------------------------------------
        // 3. 官方直投广告 CRUD、时间窗与上下架
        // ------------------------------------------------------------------
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Map<String, Object> adABody = adBody("校园咖啡新品", "校园咖啡", 10, "IN_APP_WEBVIEW",
                now.minusHours(1), now.plusHours(2), "ENABLED");
        JsonNode adANode = ok(postJson("/api/admin/community/direct-ads", admin, adABody));
        long adAId = adANode.path("adId").asLong();
        assertEquals("DRAFT", adANode.path("status").asText(), "创建后默认草稿，不直接上架");
        assertEquals("DRAFT", adANode.path("reviewStatus").asText(), "创建后默认待提交审核");
        assertTrue(adANode.path("bounceMeasurable").asBoolean());

        // 未审核通过不允许上架
        JsonNode unapprovedEnable = call(postRequest(
                "/api/admin/community/direct-ads/" + adAId + "/status", admin,
                Map.of("status", "ENABLED")), 409);
        assertEquals("REQUEST_409_01", unapprovedEnable.path("code").asText());

        // 高优先级但未上架的广告不得进入信息流
        Map<String, Object> adBBody = adBody("未上架广告", "测试品牌B", 100, "IN_APP_WEBVIEW",
                now.minusHours(1), now.plusHours(2), "DRAFT");
        long adBId = ok(postJson("/api/admin/community/direct-ads", admin, adBBody)).path("adId").asLong();

        // 不可测落地类型的广告（草稿，仅直接调事件接口做统计验证）
        long adCId = ok(postJson("/api/admin/community/direct-ads", admin, adBody(
                "外部浏览器广告", "测试品牌C", 5, "EXTERNAL_BROWSER",
                now.minusHours(1), now.plusHours(2), "DRAFT"))).path("adId").asLong();
        long adDId = ok(postJson("/api/admin/community/direct-ads", admin, adBody(
                "Deeplink 广告", "测试品牌D", 4, "DEEPLINK",
                now.minusHours(1), now.plusHours(2), "DRAFT"))).path("adId").asLong();

        // 非法参数：跳转类型、时间窗、分区
        Map<String, Object> badLanding = adBody("非法跳转", "测试", 0, "TELEPORT",
                now.minusHours(1), now.plusHours(1), "DRAFT");
        call(postRequest("/api/admin/community/direct-ads", admin, badLanding), 400);
        Map<String, Object> badWindow = adBody("非法时间窗", "测试", 0, "IN_APP_WEBVIEW",
                now.plusHours(2), now.plusHours(1), "DRAFT");
        call(postRequest("/api/admin/community/direct-ads", admin, badWindow), 400);
        Map<String, Object> badSection = adBody("非法分区", "测试", 0, "IN_APP_WEBVIEW",
                now.minusHours(1), now.plusHours(1), "DRAFT");
        badSection.put("sectionId", 999999);
        call(postRequest("/api/admin/community/direct-ads", admin, badSection), 400);
        Map<String, Object> badUrl = adBody("非法落地地址", "测试", 0, "IN_APP_WEBVIEW",
                now.minusHours(1), now.plusHours(1), "DRAFT");
        badUrl.put("targetUrl", "javascript:alert(1)");
        call(postRequest("/api/admin/community/direct-ads", admin, badUrl), 400);
        Map<String, Object> badDeeplink = adBody("非法 Deeplink", "测试", 0, "DEEPLINK",
                now.minusHours(1), now.plusHours(1), "DRAFT");
        badDeeplink.put("targetUrl", "intent://scan/#Intent;scheme=zxing;end");
        call(postRequest("/api/admin/community/direct-ads", admin, badDeeplink), 400);

        // 编辑：草稿状态可编辑，优先级（排序）与品牌名
        Map<String, Object> adAUpdate = adBody("校园咖啡新品", "校园咖啡升级品牌", 20, "IN_APP_WEBVIEW",
                now.minusHours(1), now.plusHours(2), "DRAFT");
        JsonNode updatedAdA = ok(postJson("/api/admin/community/direct-ads/" + adAId, admin, adAUpdate));
        assertEquals(20, updatedAdA.path("priority").asInt());
        assertEquals("校园咖啡升级品牌", updatedAdA.path("brandName").asText());
        assertEquals("DRAFT", updatedAdA.path("reviewStatus").asText());
        JsonNode adDetail = ok(getJson("/api/admin/community/direct-ads/" + adAId, admin, Map.of()));
        assertEquals(adAId, adDetail.path("adId").asLong());

        // 提交审核：PENDING；重复提交幂等；审核通过后上架
        JsonNode submittedA = ok(postJson("/api/admin/community/direct-ads/" + adAId + "/submit", admin, null));
        assertEquals("PENDING", submittedA.path("reviewStatus").asText());
        String firstSubmittedAt = submittedA.path("submittedAt").asText();
        JsonNode resubmittedA = ok(postJson("/api/admin/community/direct-ads/" + adAId + "/submit", admin, null));
        assertEquals("PENDING", resubmittedA.path("reviewStatus").asText());
        assertEquals(firstSubmittedAt, resubmittedA.path("submittedAt").asText(), "重复提交应幂等");
        // 审核中不可编辑
        JsonNode pendingEdit = call(postRequest("/api/admin/community/direct-ads/" + adAId, admin, adAUpdate), 409);
        assertEquals("REQUEST_409_01", pendingEdit.path("code").asText());
        JsonNode approvedA = ok(postJson("/api/admin/community/direct-ads/" + adAId + "/review", admin,
                Map.of("action", "APPROVE")));
        assertEquals("APPROVED", approvedA.path("reviewStatus").asText());
        assertFalse(approvedA.path("reviewedAt").isNull());
        JsonNode enabledA = ok(postJson("/api/admin/community/direct-ads/" + adAId + "/status", admin,
                Map.of("status", "ENABLED")));
        assertEquals("ENABLED", enabledA.path("status").asText());
        // 审核通过后不可再编辑
        JsonNode approvedEdit = call(postRequest("/api/admin/community/direct-ads/" + adAId, admin, adAUpdate), 409);
        assertEquals("REQUEST_409_01", approvedEdit.path("code").asText());

        JsonNode adPage = ok(getJson("/api/admin/community/direct-ads", admin,
                Map.of("pageNum", "1", "pageSize", "50", "status", "ENABLED")));
        assertTrue(adPage.path("total").asInt() >= 1);
        JsonNode approvedPage = ok(getJson("/api/admin/community/direct-ads", admin,
                Map.of("pageNum", "1", "pageSize", "50", "reviewStatus", "APPROVED")));
        boolean adAApproved = false;
        for (JsonNode row : approvedPage.path("records")) {
            assertEquals("APPROVED", row.path("reviewStatus").asText());
            if (row.path("adId").asLong() == adAId) {
                adAApproved = true;
            }
        }
        assertTrue(adAApproved, "reviewStatus 过滤应返回已通过广告");
        call(getRequest("/api/admin/community/direct-ads", admin, Map.of("reviewStatus", "UNKNOWN")), 400);

        // 已过结束时间的广告即使审核通过也不允许上架
        Map<String, Object> expiredDraft = adBody("过期草稿", "测试", 1, "IN_APP_WEBVIEW",
                now.minusHours(2), now.minusHours(1), "DRAFT");
        long expiredDraftId = ok(postJson("/api/admin/community/direct-ads", admin, expiredDraft))
                .path("adId").asLong();
        ok(postJson("/api/admin/community/direct-ads/" + expiredDraftId + "/submit", admin, null));
        ok(postJson("/api/admin/community/direct-ads/" + expiredDraftId + "/review", admin,
                Map.of("action", "APPROVE")));
        JsonNode cannotEnable = call(postRequest("/api/admin/community/direct-ads/" + expiredDraftId + "/status",
                admin, Map.of("status", "ENABLED")), 409);
        assertEquals("REQUEST_409_01", cannotEnable.path("code").asText());

        // ------------------------------------------------------------------
        // 4. 混合信息流：8 条自然内容后插入 1 条广告，且不重复
        // ------------------------------------------------------------------
        for (int i = 1; i <= 8; i++) {
            ok(postJson("/api/app/community/posts", requester,
                    Map.of("sectionId", SECTION_ID, "content", "信息流广告测试内容 " + i)));
        }
        JsonNode feed = feed(requester);
        JsonNode feedRecords = feed.path("records");
        assertEquals(1, countItemType(feedRecords, "AD"), "每 8 条自然内容只插入 1 条广告，单页去重");
        int firstAdIndex = firstAdIndex(feedRecords);
        assertEquals(8, firstAdIndex, "广告应插入在第 8 条自然内容之后");
        JsonNode feedAd = feedRecords.get(firstAdIndex).path("ad");
        assertEquals(adAId, feedAd.path("adId").asLong(), "未上架的高优先级广告不得插入");
        assertFalse(feedAd.path("clickId").asText().isBlank());
        assertTrue(feedAd.path("measurable").asBoolean());
        assertEquals("推广", feedAd.path("sponsoredLabel").asText());
        assertEquals(20, feedAd.path("priority").asInt());
        String clickId = feedAd.path("clickId").asText();

        // 下架后信息流不再出现广告；重新上架后恢复
        ok(postJson("/api/admin/community/direct-ads/" + adAId + "/status", admin, Map.of("status", "DISABLED")));
        assertEquals(0, countItemType(feed(requester).path("records"), "AD"));
        ok(postJson("/api/admin/community/direct-ads/" + adAId + "/status", admin, Map.of("status", "ENABLED")));
        assertEquals(1, countItemType(feed(requester).path("records"), "AD"));

        // 更高优先级的广告排在前面（草稿 → 提交 → 审核通过 → 上架）
        long adEId = ok(postJson("/api/admin/community/direct-ads", admin, adBody(
                "最高优先级广告", "测试品牌E", 30, "IN_APP_WEBVIEW",
                now.minusHours(1), now.plusHours(2), "ENABLED"))).path("adId").asLong();
        approveAndEnableAd(admin, adEId);
        JsonNode priorityRecords = feed(requester).path("records");
        assertEquals(1, countItemType(priorityRecords, "AD"));
        assertEquals(adEId, priorityRecords.get(firstAdIndex(priorityRecords)).path("ad").path("adId").asLong());
        ok(postJson("/api/admin/community/direct-ads/" + adEId + "/status", admin, Map.of("status", "DISABLED")));

        // 未登录不可访问信息流
        call(get("/api/app/community/feed").param("sectionId", String.valueOf(SECTION_ID)), 401);

        // ------------------------------------------------------------------
        // 5. 可测落地链路：曝光 → 点击 → 落地加载 → 停留/跳出 → 统计
        // ------------------------------------------------------------------
        JsonNode impression = ok(postJson("/api/app/community/direct-ads/" + adAId + "/impression",
                requester, Map.of("clickId", clickId, "sessionId", JSESSION)));
        assertEquals(clickId, impression.path("clickId").asText());
        // 幂等：同一 clickId 重复曝光不重复计数
        ok(postJson("/api/app/community/direct-ads/" + adAId + "/impression",
                requester, Map.of("clickId", clickId, "sessionId", JSESSION)));

        JsonNode click = ok(postJson("/api/app/community/direct-ads/" + adAId + "/click",
                requester, Map.of("clickId", clickId, "sessionId", JSESSION)));
        assertEquals(clickId, click.path("clickId").asText());
        assertTrue(click.path("measurable").asBoolean());
        assertEquals("IN_APP_WEBVIEW", click.path("landingType").asText());
        // 幂等：重复点击
        ok(postJson("/api/app/community/direct-ads/" + adAId + "/click",
                requester, Map.of("clickId", clickId, "sessionId", JSESSION)));

        // 防刷：伪造 clickId 但没有曝光时，点击归因必须失败
        JsonNode forgedClick = call(postRequest("/api/app/community/direct-ads/" + adAId + "/click",
                requester, Map.of("clickId", "forged-" + System.nanoTime(), "sessionId", JSESSION)), 400);
        assertEquals("AD_400_01", forgedClick.path("code").asText());

        JsonNode landing = ok(postJson("/api/app/community/direct-ads/landing/loaded",
                requester, Map.of("clickId", clickId, "sessionId", JSESSION)));
        assertTrue(landing.path("measurable").asBoolean());

        JsonNode exit = ok(postJson("/api/app/community/direct-ads/landing/exit",
                requester, Map.of("clickId", clickId, "dwellMs", 1000)));
        assertTrue(exit.path("bounced").asBoolean(), "1000ms < 5s 应判定跳出");
        assertEquals(5, exit.path("bounceThresholdSeconds").asInt());
        // 幂等：重复停留上报
        ok(postJson("/api/app/community/direct-ads/landing/exit",
                requester, Map.of("clickId", clickId, "dwellMs", 1000)));

        JsonNode stats = ok(getJson("/api/admin/community/direct-ads/" + adAId + "/stats", admin, Map.of()));
        assertEquals(1, stats.path("impressions").asInt(), "重复曝光上报应幂等");
        assertEquals(1, stats.path("clicks").asInt(), "重复点击上报应幂等");
        assertEquals(1.0, stats.path("ctr").asDouble(), 0.0001);
        assertEquals(1, stats.path("measurableLandings").asInt());
        assertEquals(1, stats.path("bounced").asInt());
        assertEquals(1.0, stats.path("bounceRate").asDouble(), 0.0001);
        assertEquals(1000.0, stats.path("avgDwellMs").asDouble(), 0.1);
        assertTrue(stats.path("bounceMeasurable").asBoolean());
        assertEquals(0, stats.path("unmeasurableClicks").asInt());

        // 按不同 N 重算：N=1s 时 1000ms 不算跳出
        JsonNode statsN1 = ok(getJson("/api/admin/community/direct-ads/" + adAId + "/stats", admin,
                Map.of("bounceThresholdSeconds", "1")));
        assertEquals(0, statsN1.path("bounced").asInt());
        assertEquals(0.0, statsN1.path("bounceRate").asDouble(), 0.0001);

        // ------------------------------------------------------------------
        // 6. 不可测落地：外部浏览器/Deeplink 的跳出率必须为 null
        // ------------------------------------------------------------------
        JsonNode externalClick = ok(postJson("/api/app/community/direct-ads/" + adCId + "/click",
                requester, Map.of("sessionId", JSESSION)));
        assertFalse(externalClick.path("measurable").asBoolean());
        String externalClickId = externalClick.path("clickId").asText();
        assertEquals("EXTERNAL_BROWSER", externalClick.path("landingType").asText());
        JsonNode externalLanding = call(postRequest("/api/app/community/direct-ads/landing/loaded",
                requester, Map.of("clickId", externalClickId)), 400);
        assertEquals("COMMON_400", externalLanding.path("code").asText());
        call(postRequest("/api/app/community/direct-ads/landing/exit",
                requester, Map.of("clickId", externalClickId, "dwellMs", 500)), 400);

        JsonNode externalStats = ok(getJson("/api/admin/community/direct-ads/" + adCId + "/stats", admin, Map.of()));
        assertEquals(0, externalStats.path("impressions").asInt());
        assertEquals(1, externalStats.path("clicks").asInt());
        assertEquals(0, externalStats.path("measurableLandings").asInt());
        assertEquals(1, externalStats.path("unmeasurableClicks").asInt());
        assertFalse(externalStats.path("bounceMeasurable").asBoolean());
        assertTrue(externalStats.path("bounceRate").isNull(), "外部浏览器跳出率必须为 null 而非 0");
        assertTrue(externalStats.path("avgDwellMs").isNull());
        JsonNode externalBreakdown = breakdown(externalStats, "EXTERNAL_BROWSER");
        assertFalse(externalBreakdown.path("measurable").asBoolean());
        assertTrue(externalBreakdown.path("bounceRate").isNull());

        JsonNode deeplinkClick = ok(postJson("/api/app/community/direct-ads/" + adDId + "/click",
                requester, Map.of("sessionId", JSESSION)));
        assertFalse(deeplinkClick.path("measurable").asBoolean());
        JsonNode deeplinkStats = ok(getJson("/api/admin/community/direct-ads/" + adDId + "/stats", admin, Map.of()));
        assertTrue(deeplinkStats.path("bounceRate").isNull(), "Deeplink 跳出率必须为 null");
        JsonNode deeplinkBreakdown = breakdown(deeplinkStats, "DEEPLINK");
        assertFalse(deeplinkBreakdown.path("measurable").asBoolean());
        assertTrue(deeplinkBreakdown.path("bounceRate").isNull());

        // 无任何事件的广告：CTR 0、跳出率 null，不得出现 0% 跳出；
        // 但落地类型本身可测时 bounceMeasurable 应为 true，前端展示「暂无数据」而非「不可测」。
        JsonNode emptyStats = ok(getJson("/api/admin/community/direct-ads/" + adBId + "/stats", admin, Map.of()));
        assertEquals(0, emptyStats.path("impressions").asInt());
        assertEquals(0, emptyStats.path("clicks").asInt());
        assertEquals(0.0, emptyStats.path("ctr").asDouble(), 0.0001);
        assertTrue(emptyStats.path("bounceRate").isNull());
        assertTrue(emptyStats.path("bounceMeasurable").asBoolean());

        // 外部浏览器/Deeplink 广告虽然记录点击，但不进入信息流（未上架）
        for (JsonNode item : feed(requester).path("records")) {
            if (!"AD".equals(item.path("itemType").asText())) {
                continue;
            }
            long shownId = item.path("ad").path("adId").asLong();
            assertFalse(shownId == adCId || shownId == adDId || shownId == adBId);
        }

        // ------------------------------------------------------------------
        // 7. 频控：单用户单广告单日曝光上限生效后该广告不再投放
        // ------------------------------------------------------------------
        Map<String, Object> capConfig = new LinkedHashMap<>();
        capConfig.put("key", "community.ad_user_ad_daily_cap");
        capConfig.put("value", "1");
        capConfig.put("campusId", 0);
        ok(postJson("/api/admin/system/config", admin, Map.of("items", List.of(capConfig))));
        JsonNode frequencyFeed = feed(requester);
        assertEquals(0, countItemType(frequencyFeed.path("records"), "AD"),
                "requester 已曝光过广告 A，单广告上限为 1 时不应再投放");

        // ------------------------------------------------------------------
        // 8. 删除：逻辑删除后详情 404、列表与信息流不再出现
        // ------------------------------------------------------------------
        ok(deleteJson("/api/admin/community/direct-ads/" + adCId, admin));
        JsonNode deletedDetail = call(get("/api/admin/community/direct-ads/" + adCId)
                .header("Authorization", "Bearer " + admin), 404);
        assertEquals("COMMON_404", deletedDetail.path("code").asText());
        JsonNode afterDelete = ok(getJson("/api/admin/community/direct-ads", admin,
                Map.of("pageNum", "1", "pageSize", "50")));
        for (JsonNode row : afterDelete.path("records")) {
            assertTrue(row.path("adId").asLong() != adCId, "删除后列表不应再出现");
        }
        // 广告服务号仍可用于账号列表（未被删除）
        JsonNode accountsAfter = ok(getJson("/api/admin/community/official-accounts", admin,
                Map.of("pageNum", "1", "pageSize", "50")));
        boolean adServiceStillThere = false;
        for (JsonNode row : accountsAfter.path("records")) {
            if (row.path("userId").asLong() == adServiceId) {
                adServiceStillThere = true;
            }
        }
        assertTrue(adServiceStillThere);
    }

    @Test
    void adReviewAdvertiserCrudAndSlotAds() throws Exception {
        String admin = login("admin");
        String requester = login("requester");
        LocalDateTime now = LocalDateTime.now().withNano(0);

        // ------------------------------------------------------------------
        // 1. 广告主 CRUD、权限与校区隔离
        // ------------------------------------------------------------------
        JsonNode forbiddenAdvertiser = call(postRequest("/api/admin/community/advertisers", requester,
                advertiserBody("无权限广告主", null, null, null)), 403);
        assertEquals("AUTH_403", forbiddenAdvertiser.path("code").asText());

        JsonNode advertiser = ok(postJson("/api/admin/community/advertisers", admin,
                advertiserBody("集成测试广告主", "王老师", "13800000000", null)));
        long advertiserId = advertiser.path("advertiserId").asLong();
        assertTrue(advertiser.path("advertiserId").isTextual(), "雪花 ID 应按字符串下发");
        assertEquals("ENABLED", advertiser.path("status").asText());

        JsonNode advertiserPage = ok(getJson("/api/admin/community/advertisers", admin,
                Map.of("keyword", "集成测试", "pageNum", "1", "pageSize", "10")));
        assertTrue(advertiserPage.path("total").asInt() >= 1);
        assertEquals(advertiserId, advertiserPage.path("records").get(0).path("advertiserId").asLong());

        JsonNode advertiserDetail = ok(getJson("/api/admin/community/advertisers/" + advertiserId, admin,
                Map.of()));
        assertEquals("集成测试广告主", advertiserDetail.path("name").asText());

        JsonNode advertiserUpdated = ok(postJson("/api/admin/community/advertisers/" + advertiserId, admin,
                advertiserBody("集成测试广告主（改）", "李老师", "13900000000", "ENABLED")));
        assertEquals("集成测试广告主（改）", advertiserUpdated.path("name").asText());
        assertEquals("李老师", advertiserUpdated.path("contactName").asText());

        ok(postJson("/api/admin/community/advertisers/" + advertiserId + "/status", admin,
                Map.of("status", "DISABLED")));
        JsonNode disabledAdvertisers = ok(getJson("/api/admin/community/advertisers", admin,
                Map.of("status", "DISABLED", "pageSize", "10")));
        boolean foundDisabled = false;
        for (JsonNode row : disabledAdvertisers.path("records")) {
            if (row.path("advertiserId").asLong() == advertiserId) {
                foundDisabled = true;
            }
        }
        assertTrue(foundDisabled, "停用的广告主可按 status 过滤出来");
        JsonNode advertiserReEnabled = ok(postJson("/api/admin/community/advertisers/" + advertiserId + "/status",
                admin, Map.of("status", "ENABLED")));
        assertEquals("ENABLED", advertiserReEnabled.path("status").asText());
        call(postRequest("/api/admin/community/advertisers/" + advertiserId + "/status", admin,
                Map.of("status", "BANNED")), 400);
        call(getRequest("/api/admin/community/advertisers/999999999", admin, Map.of()), 404);

        // 关联不存在广告主 → 400
        Map<String, Object> badAdvertiserAd = adBody("非法广告主广告", "测试", 0, "IN_APP_WEBVIEW",
                now.minusHours(1), now.plusHours(2), "DRAFT");
        badAdvertiserAd.put("advertiserId", 999999999L);
        call(postRequest("/api/admin/community/direct-ads", admin, badAdvertiserAd), 400);

        // ------------------------------------------------------------------
        // 2. 审核状态机：提交/重复提交/非法编辑/驳回重提/越权审核
        // ------------------------------------------------------------------
        Map<String, Object> adXBody = adBody("审核流广告", "审核品牌", 5, "IN_APP_WEBVIEW",
                now.minusHours(1), now.plusHours(2), "DRAFT");
        adXBody.put("advertiserId", advertiserId);
        JsonNode adX = ok(postJson("/api/admin/community/direct-ads", admin, adXBody));
        long adXId = adX.path("adId").asLong();
        assertEquals(advertiserId, adX.path("advertiserId").asLong());
        assertEquals("集成测试广告主（改）", adX.path("advertiserName").asText());
        assertEquals("DRAFT", adX.path("reviewStatus").asText());

        // 草稿直接审核 → 409
        JsonNode draftReview = call(postRequest("/api/admin/community/direct-ads/" + adXId + "/review", admin,
                Map.of("action", "APPROVE")), 409);
        assertEquals("REQUEST_409_01", draftReview.path("code").asText());

        JsonNode pendingX = ok(postJson("/api/admin/community/direct-ads/" + adXId + "/submit", admin, null));
        assertEquals("PENDING", pendingX.path("reviewStatus").asText());
        String submittedAt = pendingX.path("submittedAt").asText();
        JsonNode pendingAgain = ok(postJson("/api/admin/community/direct-ads/" + adXId + "/submit", admin, null));
        assertEquals(submittedAt, pendingAgain.path("submittedAt").asText(), "重复提交应幂等");

        // 非法审核动作 / 驳回缺原因
        call(postRequest("/api/admin/community/direct-ads/" + adXId + "/review", admin,
                Map.of("action", "TELEPORT")), 400);
        call(postRequest("/api/admin/community/direct-ads/" + adXId + "/review", admin,
                Map.of("action", "REJECT")), 400);

        // 越权审核：普通用户 403
        JsonNode forbiddenReview = call(postRequest("/api/admin/community/direct-ads/" + adXId + "/review",
                requester, Map.of("action", "APPROVE")), 403);
        assertEquals("AUTH_403", forbiddenReview.path("code").asText());

        // 审核驳回
        JsonNode rejected = ok(postJson("/api/admin/community/direct-ads/" + adXId + "/review", admin,
                Map.of("action", "REJECT", "reason", "落地页信息不完整")));
        assertEquals("REJECTED", rejected.path("reviewStatus").asText());
        assertEquals("落地页信息不完整", rejected.path("reviewReason").asText());
        call(postRequest("/api/admin/community/direct-ads/" + adXId + "/status", admin,
                Map.of("status", "ENABLED")), 409);
        // 驳回后可编辑并重新提交
        JsonNode reEdited = ok(postJson("/api/admin/community/direct-ads/" + adXId, admin, adXBody));
        assertEquals("REJECTED", reEdited.path("reviewStatus").asText());
        JsonNode resubmitted = ok(postJson("/api/admin/community/direct-ads/" + adXId + "/submit", admin, null));
        assertEquals("PENDING", resubmitted.path("reviewStatus").asText());
        JsonNode reApproved = ok(postJson("/api/admin/community/direct-ads/" + adXId + "/review", admin,
                Map.of("action", "APPROVE")));
        assertEquals("APPROVED", reApproved.path("reviewStatus").asText());
        assertTrue(reApproved.path("reviewReason").isNull(), "通过后应清空驳回原因");
        ok(postJson("/api/admin/community/direct-ads/" + adXId + "/status", admin,
                Map.of("status", "ENABLED")));
        call(postRequest("/api/admin/community/direct-ads/" + adXId + "/review", admin,
                Map.of("action", "APPROVE")), 409);
        call(postRequest("/api/admin/community/direct-ads/" + adXId, admin, adXBody), 409);

        // ------------------------------------------------------------------
        // 3. App 广告位：审核通过 + 时间窗 + slot 过滤 + 最多 3 条 + clickId
        // ------------------------------------------------------------------
        long bannerTop = createApprovedSlotAd(admin, "BANNER", 50, now.minusHours(1), now.plusHours(3));
        long bannerSecond = createApprovedSlotAd(admin, "BANNER", 40, now.minusHours(1), now.plusHours(3));
        long bannerThird = createApprovedSlotAd(admin, "BANNER", 30, now.minusHours(1), now.plusHours(3));
        long bannerLow = createApprovedSlotAd(admin, "BANNER", 20, now.minusHours(1), now.plusHours(3));
        long serviceHallAd = createApprovedSlotAd(admin, "SERVICE_HALL", 60,
                now.minusHours(1), now.plusHours(3));
        // 审核未通过（草稿）
        Map<String, Object> draftBannerBody = adBody("草稿 BANNER", "草稿品牌", 90, "IN_APP_WEBVIEW",
                now.minusHours(1), now.plusHours(3), "DRAFT");
        draftBannerBody.put("slot", "BANNER");
        long draftBanner = ok(postJson("/api/admin/community/direct-ads", admin, draftBannerBody))
                .path("adId").asLong();
        // 未到投放开始时间（审核通过 + 已上架）
        long futureBanner = createApprovedSlotAd(admin, "BANNER", 70,
                now.plusHours(1), now.plusHours(3));
        // 已停投
        long disabledBanner = createApprovedSlotAd(admin, "BANNER", 80,
                now.minusHours(1), now.plusHours(3));
        ok(postJson("/api/admin/community/direct-ads/" + disabledBanner + "/status", admin,
                Map.of("status", "DISABLED")));

        JsonNode banners = ok(getJson("/api/app/community/slot-ads", requester, Map.of("slot", "BANNER")));
        assertEquals(3, banners.size(), "最多返回 3 条，且只包含审核通过、窗口内、已上架的广告");
        List<Long> bannerIds = new ArrayList<>();
        for (JsonNode row : banners) {
            bannerIds.add(row.path("adId").asLong());
            assertFalse(row.path("clickId").asText().isBlank(), "广告位应返回 clickId");
            assertTrue(row.path("measurable").asBoolean());
            assertEquals("推广", row.path("sponsoredLabel").asText());
        }
        assertEquals(bannerTop, bannerIds.get(0), "按优先级倒序");
        assertEquals(bannerSecond, bannerIds.get(1));
        assertEquals(bannerThird, bannerIds.get(2));
        assertFalse(bannerIds.contains(bannerLow), "第 4 条被条数上限截断");
        assertFalse(bannerIds.contains(draftBanner), "审核未通过不返回");
        assertFalse(bannerIds.contains(futureBanner), "未到开始时间不返回");
        assertFalse(bannerIds.contains(disabledBanner), "已停投不返回");

        JsonNode bannersAgain = ok(getJson("/api/app/community/slot-ads", requester,
                Map.of("slot", "BANNER")));
        assertNotEquals(banners.get(0).path("clickId").asText(),
                bannersAgain.get(0).path("clickId").asText(), "每次读取应生成新的 clickId");

        JsonNode hallAds = ok(getJson("/api/app/community/slot-ads", requester,
                Map.of("slot", "SERVICE_HALL")));
        assertEquals(1, hallAds.size());
        assertEquals(serviceHallAd, hallAds.get(0).path("adId").asLong(), "slot 过滤应只返回对应广告位");

        call(getRequest("/api/app/community/slot-ads", requester, Map.of("slot", "FEED")), 400);
        call(get("/api/app/community/slot-ads").param("slot", "BANNER"), 401);
    }

    // ----------------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------------

    private Map<String, Object> accountBody(String accountType, String displayName, String operatorName,
                                            String description, String officialTitle,
                                            String verificationStatus, String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountType", accountType);
        body.put("displayName", displayName);
        if (operatorName != null) body.put("operatorName", operatorName);
        if (description != null) body.put("description", description);
        if (officialTitle != null) body.put("officialTitle", officialTitle);
        if (verificationStatus != null) body.put("verificationStatus", verificationStatus);
        if (status != null) body.put("status", status);
        return body;
    }

    private Map<String, Object> adBody(String title, String brandName, int priority, String landingType,
                                       LocalDateTime startAt, LocalDateTime endAt, String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("brandName", brandName);
        body.put("description", "集成测试广告描述");
        body.put("coverUrl", "https://example.com/ad-cover.jpg");
        body.put("targetUrl", "https://example.com/campaign");
        body.put("landingType", landingType);
        body.put("sectionId", SECTION_ID);
        body.put("slot", "FEED");
        body.put("priority", priority);
        body.put("startAt", startAt.toString());
        body.put("endAt", endAt.toString());
        body.put("status", status);
        body.put("bounceThresholdSeconds", 5);
        body.put("ctaText", "立即查看");
        body.put("sponsoredLabel", "推广");
        return body;
    }

    private Map<String, Object> advertiserBody(String name, String contactName, String contactPhone,
                                               String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("remark", "集成测试广告主备注");
        if (contactName != null) body.put("contactName", contactName);
        if (contactPhone != null) body.put("contactPhone", contactPhone);
        if (status != null) body.put("status", status);
        return body;
    }

    /** 广告走完整审核流：提交 → 审核通过 → 上架。 */
    private void approveAndEnableAd(String admin, long adId) throws Exception {
        ok(postJson("/api/admin/community/direct-ads/" + adId + "/submit", admin, null));
        ok(postJson("/api/admin/community/direct-ads/" + adId + "/review", admin,
                Map.of("action", "APPROVE")));
        ok(postJson("/api/admin/community/direct-ads/" + adId + "/status", admin,
                Map.of("status", "ENABLED")));
    }

    private long createApprovedSlotAd(String admin, String slot, int priority,
                                      LocalDateTime startAt, LocalDateTime endAt) throws Exception {
        Map<String, Object> body = adBody("广告位 " + slot + " " + priority, "广告位品牌", priority,
                "IN_APP_WEBVIEW", startAt, endAt, "DRAFT");
        body.put("slot", slot);
        long adId = ok(postJson("/api/admin/community/direct-ads", admin, body)).path("adId").asLong();
        approveAndEnableAd(admin, adId);
        return adId;
    }

    private JsonNode feed(String token) throws Exception {
        return ok(getJson("/api/app/community/feed", token,
                Map.of("sectionId", String.valueOf(SECTION_ID), "pageNum", "1", "pageSize", "10",
                        "sessionId", JSESSION)));
    }

    private int countItemType(JsonNode records, String itemType) {
        int count = 0;
        for (JsonNode item : records) {
            if (itemType.equals(item.path("itemType").asText())) {
                count++;
            }
        }
        return count;
    }

    private int firstAdIndex(JsonNode records) {
        for (int i = 0; i < records.size(); i++) {
            if ("AD".equals(records.get(i).path("itemType").asText())) {
                return i;
            }
        }
        return -1;
    }

    private JsonNode breakdown(JsonNode stats, String landingType) {
        for (JsonNode row : stats.path("landingBreakdown")) {
            if (landingType.equals(row.path("landingType").asText())) {
                return row;
            }
        }
        throw new AssertionError("landingBreakdown 中缺少 " + landingType);
    }

    private String login(String username) throws Exception {
        JsonNode response = ok(postJson("/api/common/auth/login", null,
                Map.of("username", username, "password", "DevOnly123!")));
        return response.path("accessToken").asText();
    }

    private JsonNode ok(JsonNode result) {
        assertTrue(result.path("success").asBoolean(), "接口应成功: " + result);
        return result.path("data");
    }

    private JsonNode getJson(String path, String token, Map<String, String> params) throws Exception {
        return call(getRequest(path, token, params), 200);
    }

    private MockHttpServletRequestBuilder getRequest(String path, String token, Map<String, String> params) {
        MockHttpServletRequestBuilder builder = get(path);
        params.forEach(builder::param);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private JsonNode postJson(String path, String token, Object body) throws Exception {
        return call(postRequest(path, token, body), 200);
    }

    private MockHttpServletRequestBuilder postRequest(String path, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = post(path).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(objectMapper.writeValueAsString(body));
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private JsonNode deleteJson(String path, String token) throws Exception {
        return call(delete(path).header("Authorization", "Bearer " + token), 200);
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(builder)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().is(expectedStatus))
                .andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNotNull(content);
        assertFalse(content.isBlank(), "响应体不应为空, status=" + expectedStatus);
        return objectMapper.readTree(content);
    }

}
