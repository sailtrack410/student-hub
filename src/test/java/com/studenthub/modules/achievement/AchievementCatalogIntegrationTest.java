package com.studenthub.modules.achievement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.achievement.entity.AchievementBadgeEntity;
import com.studenthub.modules.achievement.entity.UserAchievementBadgeEntity;
import com.studenthub.modules.achievement.mapper.AchievementBadgeMapper;
import com.studenthub.modules.achievement.mapper.UserAchievementBadgeMapper;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 方案B：徽章字典热更新 + 用户态 + 佩戴 + 管理端图标上传 的端到端验证（H2 + MockMvc）。
 * 覆盖：catalog 版本语义、state 字段、wear action、停用清佩戴、图标上传与公开读取。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AchievementCatalogIntegrationTest {

    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AchievementBadgeMapper badgeMapper;
    @Autowired
    private UserAchievementBadgeMapper userBadgeMapper;

    @Test
    void catalogHotUpdateStateWearAndIconUpload() throws Exception {
        String requester = login("requester");
        String admin = login("admin");

        // 1. state：轻量用户态 + 16 位 hex 版本；19 枚启用勋章都有进度
        JsonNode state = getData("/api/app/achievements/state", requester);
        String version = state.path("catalogVersion").asText();
        assertTrue(version.matches("[0-9a-f]{16}"), "catalogVersion 应为 16 位 hex：" + version);
        assertTrue(state.path("wornBadgeCode").isNull(), "初始未佩戴");
        // devdata 已给 requester 播种服务申请，SERVICE_FIRST_REQUEST 会被评估解锁；只断言它出现在激活列表
        assertTrue(containsCode(state.path("activated"), "SERVICE_FIRST_REQUEST"),
                "devdata 下 SERVICE_FIRST_REQUEST 应已激活");
        assertEquals(19, state.path("progress").size(), "19 枚启用勋章都应返回进度");

        // 2. catalog：首次全量下发；分类名来自字典表；内置码 iconUrl 为 null（走本地素材）
        JsonNode catalog = getData("/api/app/achievements/catalog", requester);
        assertTrue(catalog.path("changed").asBoolean());
        assertEquals(version, catalog.path("catalogVersion").asText(), "state/catalog 版本必须一致");
        assertEquals(5, catalog.path("categories").size(), "5 个分类");
        assertEquals(19, catalog.path("badges").size(), "初始 19 枚勋章");
        assertEquals("社区互动", catalog.path("categories").get(0).path("categoryName").asText());
        assertTrue(catalog.path("badges").get(0).path("iconUrl").isNull(), "内置勋章不下发图标 URL");

        // 3. 版本一致时不重复下发列表
        JsonNode unchanged = getData("/api/app/achievements/catalog?version=" + version, requester);
        assertFalse(unchanged.path("changed").asBoolean());
        assertTrue(unchanged.path("categories").isNull());
        assertTrue(unchanged.path("badges").isNull());

        // 4. 管理端新增勋章 → 版本变化，字典出现新码
        long badgeId = createBadge(admin);
        JsonNode catalogAfterCreate = getData("/api/app/achievements/catalog?version=" + version, requester);
        assertTrue(catalogAfterCreate.path("changed").asBoolean(), "新增勋章必须改变字典版本");
        String newVersion = catalogAfterCreate.path("catalogVersion").asText();
        JsonNode newBadge = findBadge(catalogAfterCreate, "TEST_BADGE_FIX");
        assertNotNull(newBadge);
        assertTrue(newBadge.path("iconUrl").isNull(), "新码未上传图标前 iconUrl 为空");

        // 5. 上传图标 → iconUrl 带版本参数；公开素材地址可读
        mockMvc.perform(multipart("/api/admin/achievements/badges/" + badgeId + "/icon")
                        .file(new MockMultipartFile("file", "badge.png", "image/png", ONE_PIXEL_PNG))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.iconUrl").value("/api/common/assets/badges/TEST_BADGE_FIX.png"));

        JsonNode catalogAfterIcon = getData("/api/app/achievements/catalog?version=" + newVersion, requester);
        assertTrue(catalogAfterIcon.path("changed").asBoolean(), "上传图标必须改变字典版本");
        String iconVersion = catalogAfterIcon.path("catalogVersion").asText();
        JsonNode badgeWithIcon = findBadge(catalogAfterIcon, "TEST_BADGE_FIX");
        assertNotNull(badgeWithIcon);
        assertTrue(badgeWithIcon.path("iconUrl").asText()
                .endsWith("/api/common/assets/badges/TEST_BADGE_FIX.png?v=" + iconVersion));

        mockMvc.perform(get("/api/common/assets/badges/TEST_BADGE_FIX.png"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsByteArray().length > 0));

        // 5.1 清除图标 → 字典回退 iconUrl=null（客户端回落本地素材/占位）
        MvcResult cleared = mockMvc.perform(delete("/api/admin/achievements/badges/" + badgeId + "/icon")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(objectMapper.readTree(cleared.getResponse().getContentAsString())
                .path("data").path("iconUrl").isNull(), "清除后 iconUrl 应为 null");
        JsonNode afterClear = getData("/api/app/achievements/catalog?version=" + iconVersion, requester);
        assertTrue(afterClear.path("changed").asBoolean());
        JsonNode clearedBadge = findBadge(afterClear, "TEST_BADGE_FIX");
        assertNotNull(clearedBadge);
        assertTrue(clearedBadge.path("iconUrl").isNull(), "清除后字典不应再下发图标 URL");

        // 6. 佩戴：未解锁拒绝；非法 action 拒绝；解锁后可佩戴，/state 立即反映
        mockMvc.perform(post("/api/app/achievements/wear")
                        .header("Authorization", "Bearer " + requester)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"badgeCode\":\"COMMUNITY_FIRST_POST\",\"action\":\"WEAR\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/app/achievements/wear")
                        .header("Authorization", "Bearer " + requester)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"badgeCode\":\"COMMUNITY_FIRST_POST\",\"action\":\"WEAR_ALL\"}"))
                .andExpect(status().isBadRequest());

        UserEntity requesterUser = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, "requester"));
        Long requesterId = requesterUser.getId();
        // evaluate 已为每枚启用勋章建过进度行，这里改为解锁已有行（无行时插入）
        UserAchievementBadgeEntity record = userBadgeMapper.selectOne(
                new LambdaQueryWrapper<UserAchievementBadgeEntity>()
                        .eq(UserAchievementBadgeEntity::getUserId, requesterId)
                        .eq(UserAchievementBadgeEntity::getBadgeCode, "COMMUNITY_FIRST_POST"));
        if (record == null) {
            record = new UserAchievementBadgeEntity();
            record.setUserId(requesterId);
            record.setBadgeCode("COMMUNITY_FIRST_POST");
            record.setCurrentProgress(1);
            record.setUnlockedAt(LocalDateTime.now());
            record.setNotified(true);
            userBadgeMapper.insert(record);
        } else {
            UserAchievementBadgeEntity unlock = new UserAchievementBadgeEntity();
            unlock.setId(record.getId());
            unlock.setCurrentProgress(1);
            unlock.setUnlockedAt(LocalDateTime.now());
            unlock.setNotified(true);
            unlock.setVersion(record.getVersion());
            userBadgeMapper.updateById(unlock);
        }

        mockMvc.perform(post("/api/app/achievements/wear")
                        .header("Authorization", "Bearer " + requester)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"badgeCode\":\"COMMUNITY_FIRST_POST\",\"action\":\"WEAR\"}"))
                .andExpect(status().isOk());
        JsonNode wornState = getData("/api/app/achievements/state", requester);
        assertEquals("COMMUNITY_FIRST_POST", wornState.path("wornBadgeCode").asText());
        assertTrue(containsCode(wornState.path("activated"), "COMMUNITY_FIRST_POST"),
                "新解锁的勋章应出现在激活列表");

        // 7. 停用勋章 → 佩戴态被清空
        long communityBadgeId = findBadgeId("COMMUNITY_FIRST_POST");
        mockMvc.perform(post("/api/admin/achievements/badges/" + communityBadgeId + "/status")
                        .param("enabled", "false")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        JsonNode afterDisable = getData("/api/app/achievements/state", requester);
        assertTrue(afterDisable.path("wornBadgeCode").isNull(), "停用勋章必须清空佩戴态");

        // 8. OpenAPI：新端点已注册（operationId 唯一，文档可生成）
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/app/achievements/catalog']").exists())
                .andExpect(jsonPath("$.paths['/api/app/achievements/state']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/achievements/badges/{id}/icon']").exists());

        // 9. 旧接口兼容：仍返回 catalogVersion 与勋章列表
        JsonNode legacy = getData("/api/app/achievements/badges", requester);
        assertEquals(afterDisable.path("catalogVersion").asText(), legacy.path("catalogVersion").asText());
        assertEquals(19, legacy.path("badges").size(), "停用后旧接口只返回启用勋章");

        // 清理：恢复启用、删除测试勋章与测试解锁记录
        mockMvc.perform(post("/api/admin/achievements/badges/" + communityBadgeId + "/status")
                        .param("enabled", "true")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/achievements/badges/" + badgeId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        userBadgeMapper.deleteById(record.getId());
    }

    private long createBadge(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/achievements/badges")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "badgeCode": "TEST_BADGE_FIX",
                                  "badgeName": "测试热更勋章",
                                  "category": "COMMUNITY",
                                  "rarity": "RARE",
                                  "description": "集成测试专用，用后即删",
                                  "metricCode": "POST_COUNT",
                                  "metricThreshold": 1,
                                  "rewardExp": 10,
                                  "sortOrder": 99
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("社区互动"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private long findBadgeId(String badgeCode) {
        AchievementBadgeEntity badge = badgeMapper.selectOne(new LambdaQueryWrapper<AchievementBadgeEntity>()
                .eq(AchievementBadgeEntity::getBadgeCode, badgeCode));
        assertNotNull(badge, "勋章不存在：" + badgeCode);
        return badge.getId();
    }

    private boolean containsCode(JsonNode array, String badgeCode) {
        for (JsonNode node : array) {
            if (badgeCode.equals(node.path("badgeCode").asText())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode findBadge(JsonNode catalog, String badgeCode) {
        List<JsonNode> badges = objectMapper.convertValue(catalog.path("badges"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, JsonNode.class));
        return badges.stream().filter(node -> badgeCode.equals(node.path("badgeCode").asText()))
                .findFirst().orElse(null);
    }

    private JsonNode getData(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/common/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"DevOnly123!\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }
}
