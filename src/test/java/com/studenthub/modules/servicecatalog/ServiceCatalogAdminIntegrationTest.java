package com.studenthub.modules.servicecatalog;

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

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端服务目录回归：受理组织选项、默认优先级/拍照流程/S02 指南字段的读写、服务项复制。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceCatalogAdminIntegrationTest {

    private static final long REPAIR_CATEGORY_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void handlerOrganizationsExposeEnabledOrganizations() throws Exception {
        String adminToken = login("admin");

        MvcResult result = mockMvc.perform(get("/api/admin/catalog/organizations")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode organizations = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertTrue(organizations.isArray() && organizations.size() >= 1, "应返回启用组织");

        JsonNode repairTeam = null;
        for (JsonNode organization : organizations) {
            if (organization.path("organizationId").asLong() == 12L) {
                repairTeam = organization;
            }
        }
        assertNotNull(repairTeam, "应包含维修改造组织 12");
        assertEquals("维修班组", repairTeam.path("organizationName").asText());
        assertEquals("REPAIR_TEAM", repairTeam.path("organizationCode").asText());
        assertEquals("ENABLED", repairTeam.path("status").asText());
    }

    @Test
    void createReadCopyAndUpdateItemWithExtendedFields() throws Exception {
        String adminToken = login("admin");
        String requesterToken = login("requester");

        // 创建：受理组织 + 默认加急 + 拍照流程 + S02 指南/材料/FAQ
        MvcResult createResult = mockMvc.perform(post("/api/admin/catalog/items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":100,"itemCode":"TEST-WORKFLOW","itemName":"流程验证服务",
                                 "summary":"自动化测试服务项","description":"服务说明",
                                 "handlerOrganizationId":12,"slaHours":8,"sortOrder":99,
                                 "defaultPriority":"URGENT","photoWorkflowRequired":true,
                                 "guideContent":"① 提交申请 ② 服务人员上门 ③ 申请人确认",
                                 "materialList":[{"name":"学生证","required":true,"note":"原件核验"}],
                                 "faq":[{"question":"多久能办好？","answer":"一般 1 个工作日"}],
                                 "formSchema":[{"key":"description","label":"问题描述","type":"textarea","required":true}]}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long itemId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").asLong();

        JsonNode created = findItem(listItems(adminToken), itemId);
        assertNotNull(created, "创建后管理端列表应包含服务项");
        assertEquals("URGENT", created.path("defaultPriority").asText());
        assertTrue(created.path("photoWorkflowRequired").asBoolean());
        assertEquals(12, created.path("handlerOrganizationId").asLong());
        assertEquals(8, created.path("slaHours").asInt());
        assertEquals("① 提交申请 ② 服务人员上门 ③ 申请人确认", created.path("guideContent").asText());
        assertEquals("学生证", created.path("materialList").path(0).path("name").asText());
        assertEquals("多久能办好？", created.path("faq").path(0).path("question").asText());

        // 小程序端详情同样返回 S02 字段
        mockMvc.perform(get("/api/app/services/" + itemId)
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultPriority").value("URGENT"))
                .andExpect(jsonPath("$.data.photoWorkflowRequired").value(true))
                .andExpect(jsonPath("$.data.guideContent").exists())
                .andExpect(jsonPath("$.data.materialList", hasSize(1)))
                .andExpect(jsonPath("$.data.faq", hasSize(1)));

        // 复制：生成停用副本，编码追加 -COPY，扩展字段一并复制
        MvcResult copyResult = mockMvc.perform(post("/api/admin/catalog/items/" + itemId + "/copy")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        long copyItemId = objectMapper.readTree(copyResult.getResponse().getContentAsString())
                .path("data").asLong();

        JsonNode copy = findItem(listItems(adminToken), copyItemId);
        assertNotNull(copy, "复制后列表应包含副本服务项");
        assertTrue(copy.path("itemCode").asText().startsWith("TEST-WORKFLOW-COPY"));
        assertEquals("DISABLED", copy.path("status").asText());
        assertEquals("URGENT", copy.path("defaultPriority").asText());
        assertTrue(copy.path("photoWorkflowRequired").asBoolean());
        assertEquals("① 提交申请 ② 服务人员上门 ③ 申请人确认", copy.path("guideContent").asText());
        assertEquals("学生证", copy.path("materialList").path(0).path("name").asText());

        // 更新：关闭拍照流程、改回普通优先级
        mockMvc.perform(post("/api/admin/catalog/items/update")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":%d,"categoryId":100,"itemCode":"TEST-WORKFLOW","itemName":"流程验证服务（改）",
                                 "defaultPriority":"NORMAL","photoWorkflowRequired":false,"slaHours":12}
                                """.formatted(itemId)))
                .andExpect(status().isOk());

        JsonNode updated = findItem(listItems(adminToken), itemId);
        assertNotNull(updated);
        assertEquals("流程验证服务（改）", updated.path("itemName").asText());
        assertEquals("NORMAL", updated.path("defaultPriority").asText());
        assertFalse(updated.path("photoWorkflowRequired").asBoolean());
        assertEquals(12, updated.path("slaHours").asInt());

        // 收尾：删除测试服务项，避免影响其他用例的目录数据
        mockMvc.perform(delete("/api/admin/catalog/items/" + itemId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/catalog/items/" + copyItemId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void invalidDefaultPriorityIsRejected() throws Exception {
        String adminToken = login("admin");
        mockMvc.perform(post("/api/admin/catalog/items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":100,"itemCode":"TEST-BAD-PRIORITY","itemName":"非法优先级",
                                 "defaultPriority":"HIGH"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private JsonNode listItems(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/catalog/items")
                        .param("categoryId", String.valueOf(REPAIR_CATEGORY_ID))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode findItem(JsonNode items, long itemId) {
        for (JsonNode item : items) {
            if (item.path("itemId").asLong() == itemId) {
                return item;
            }
        }
        return null;
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
