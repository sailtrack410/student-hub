package com.studenthub.modules.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.util.Digests;
import com.studenthub.modules.device.entity.UserDeviceSessionEntity;
import com.studenthub.modules.device.mapper.UserDeviceSessionMapper;
import com.studenthub.modules.identity.entity.OrganizationEntity;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.entity.UserRoleEntity;
import com.studenthub.modules.identity.mapper.OrganizationMapper;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 管理端身份运营（U01/U02/U03/U05/U06/U07）H2 集成测试：
 * 用户详情与脱敏、登录设备在线探测与下线、角色新建/复制/删除保护、组织树/负责人/删除保护/排序、
 * CSV 导入预览与同步导入（SKIP/UPDATE）、SSO 未接入状态。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminIdentityOpsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Autowired
    private OrganizationMapper organizationMapper;
    @Autowired
    private UserDeviceSessionMapper deviceSessionMapper;

    @Test
    void userDetailMasksPhoneAndExposesRolesAndOrganization() throws Exception {
        String admin = login("admin");
        // 给种子用户补一个手机号，验证详情接口只返回脱敏值
        UserEntity user = userMapper.selectById(1003L);
        UserEntity update = new UserEntity();
        update.setId(1003L);
        update.setPhone("13800000000");
        update.setVersion(user.getVersion());
        userMapper.updateById(update);

        JsonNode detail = ok(getJson("/api/admin/users/1003", admin, Map.of(), 1L));
        assertEquals("worker", detail.path("username").asText());
        assertEquals("138****0000", detail.path("phone").asText());
        assertEquals("维修班组", detail.path("defaultOrganizationName").asText());
        assertTrue(detail.path("roles").size() >= 1);
        assertEquals("服务人员", detail.path("roles").get(0).path("roleName").asText());
        assertEquals("LOGISTICS", detail.path("identityType").asText());
        assertEquals("ENABLED", detail.path("status").asText());
        assertEquals(1L, detail.path("campusId").asLong());
        assertFalse(detail.has("passwordHash"), "详情不能返回密码哈希");
        assertFalse(detail.has("wechatOpenId"), "详情不能返回第三方 openId");

        // 非用户管理权限访问 403
        String requester = login("requester");
        JsonNode forbidden = call(get("/api/admin/users/1003")
                .header("Authorization", "Bearer " + requester)
                .header("X-Campus-Id", "1"), 403);
        assertEquals("AUTH_403", forbidden.path("code").asText());
    }

    @Test
    void deviceListDetectsOnlineStateAndRevokeKillsSession() throws Exception {
        String admin = login("admin");
        String worker = login("worker");
        // 用 worker 的真实会话摘要登记一台设备，验证 ONLINE 状态来自会话探测
        UserDeviceSessionEntity online = new UserDeviceSessionEntity();
        online.setUserId(1003L);
        online.setDeviceId("dev_it_online_" + System.nanoTime());
        online.setTokenHash(Digests.sha256Hex(worker));
        online.setDeviceName("集成测试设备");
        online.setOsType("iOS");
        online.setOsVersion("18.0");
        online.setIpAddress("10.0.0.8");
        online.setClientType("APP");
        online.setLastActiveAt(LocalDateTime.now());
        deviceSessionMapper.insert(online);

        JsonNode devices = ok(getJson("/api/admin/users/1003/devices", admin, Map.of(), 1L));
        JsonNode target = null;
        for (JsonNode item : devices) {
            if (online.getDeviceId().equals(item.path("deviceId").asText())) target = item;
        }
        assertNotNull(target, "应能查到刚登记的设备");
        assertEquals("ONLINE", target.path("status").asText());
        assertEquals("10.0.0.8", target.path("ipAddress").asText());

        ok(postJson("/api/admin/users/1003/devices/" + online.getDeviceId() + "/revoke", admin, null, 1L));
        JsonNode afterRevoke = ok(getJson("/api/admin/users/1003/devices", admin, Map.of(), 1L));
        for (JsonNode item : afterRevoke) {
            assertFalse(online.getDeviceId().equals(item.path("deviceId").asText()), "设备应已下线并删除登记行");
        }
        // 被踢掉的 worker 会话已失效：再访问管理端应 401（无组织管理权限会先 403，这里验证用户接口需登录态）
        JsonNode unauthorized = call(get("/api/admin/users")
                .header("Authorization", "Bearer " + worker)
                .header("X-Campus-Id", "1"), 401);
        assertEquals("AUTH_401", unauthorized.path("code").asText());

        // 全部下线：管理员不能对自己执行
        JsonNode self = call(post("/api/admin/users/1001/devices/revoke-all")
                .header("Authorization", "Bearer " + admin)
                .header("X-Campus-Id", "1"), 400);
        assertEquals("COMMON_400", self.path("code").asText());
    }

    @Test
    void roleCreateCopyAndDeleteProtection() throws Exception {
        String admin = login("admin");
        String suffix = String.valueOf(System.nanoTime() % 1000000);
        String roleCode = "IT_ROLE_" + suffix;

        JsonNode created = ok(postJson("/api/admin/roles", admin, Map.of(
                "roleCode", roleCode,
                "roleName", "集成测试角色",
                "roleType", "ADMIN",
                "status", "ENABLED",
                "permissionIds", List.of(1, 2)), 1L));
        long roleId = created.path("roleId").asLong();
        assertEquals(roleCode, created.path("roleCode").asText());
        assertEquals(2, created.path("permissionIds").size());

        // 重复编码拒绝
        JsonNode duplicate = call(post("/api/admin/roles")
                .header("Authorization", "Bearer " + admin)
                .header("X-Campus-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "roleCode", roleCode, "roleName", "重复角色", "roleType", "ADMIN"))), 400);
        assertEquals("COMMON_400", duplicate.path("code").asText());

        // 模板复制：带上权限
        JsonNode copied = ok(postJson("/api/admin/roles/" + roleId + "/copy", admin,
                Map.of("roleName", "集成测试角色副本"), 1L));
        long copiedId = copied.path("roleId").asLong();
        assertTrue(copied.path("roleCode").asText().startsWith(roleCode + "_COPY"));
        assertEquals(2, copied.path("permissionIds").size());

        // 删除复制出的角色成功；再查 404
        ok(call(delete("/api/admin/roles/" + copiedId)
                .header("Authorization", "Bearer " + admin)
                .header("X-Campus-Id", "1"), 200));
        JsonNode gone = call(get("/api/admin/roles/" + copiedId)
                .header("Authorization", "Bearer " + admin)
                .header("X-Campus-Id", "1"), 404);
        assertEquals("COMMON_404", gone.path("code").asText());

        // 内置角色拒绝删除
        JsonNode builtIn = call(delete("/api/admin/roles/1")
                .header("Authorization", "Bearer " + admin)
                .header("X-Campus-Id", "1"), 400);
        assertEquals("COMMON_400", builtIn.path("code").asText());

        // 在用角色拒绝删除
        UserRoleEntity binding = new UserRoleEntity();
        binding.setUserId(1002L);
        binding.setRoleId(roleId);
        binding.setOrganizationId(10L);
        binding.setDataScope("SELF");
        binding.setCreatedBy(0L);
        userRoleMapper.insert(binding);
        JsonNode inUse = call(delete("/api/admin/roles/" + roleId)
                .header("Authorization", "Bearer " + admin)
                .header("X-Campus-Id", "1"), 400);
        assertEquals("COMMON_400", inUse.path("code").asText());
        userRoleMapper.deleteById(binding.getId());

        // 清理角色
        ok(call(delete("/api/admin/roles/" + roleId)
                .header("Authorization", "Bearer " + admin)
                .header("X-Campus-Id", "1"), 200));
    }

    @Test
    void organizationTreeLeaderDeleteProtectionAndMove() throws Exception {
        String admin = login("admin");
        JsonNode list = ok(getJson("/api/admin/organizations", admin, Map.of(), 1L));
        JsonNode root = findOrg(list, 10L);
        assertNotNull(root);
        assertEquals(1, root.path("level").asInt());
        assertEquals(1, root.path("childCount").asInt());
        JsonNode team = findOrg(list, 12L);
        assertNotNull(team);
        assertEquals(3, team.path("level").asInt());
        assertTrue(team.path("userCount").asInt() >= 1);

        JsonNode candidates = ok(getJson("/api/admin/organizations/leader-candidates", admin, Map.of(), 1L));
        assertTrue(candidates.size() >= 3);

        String suffix = String.valueOf(System.nanoTime() % 1000000);
        JsonNode parent = ok(postJson("/api/admin/organizations", admin, Map.of(
                "parentId", 10,
                "campusId", 1,
                "organizationCode", "IT_PARENT_" + suffix,
                "organizationName", "集成测试父组织",
                "organizationType", "DEPARTMENT",
                "sortOrder", 999,
                "status", "ENABLED",
                "leaderId", 1001), 1L));
        long parentId = parent.path("organizationId").asLong();
        assertEquals("开发管理员", parent.path("leaderName").asText());
        assertEquals(2, parent.path("level").asInt());

        JsonNode child = ok(postJson("/api/admin/organizations", admin, Map.of(
                "parentId", parentId,
                "campusId", 1,
                "organizationCode", "IT_CHILD_" + suffix,
                "organizationName", "集成测试子组织",
                "organizationType", "CLASS",
                "sortOrder", 0,
                "status", "ENABLED"), 1L));
        long childId = child.path("organizationId").asLong();

        // 有子组织时拒绝删除父组织
        JsonNode blocked = call(delete("/api/admin/organizations/" + parentId)
                .header("Authorization", "Bearer " + admin)
                .header("X-Campus-Id", "1"), 400);
        assertEquals("COMMON_400", blocked.path("code").asText());
        assertTrue(blocked.path("message").asText().contains("子组织"));

        // 同级排序：新建父组织 sortOrder=0，同级有种子 ROOT(0)；UP 后应排到前面（sortOrder=10）
        ok(postJson("/api/admin/organizations/" + parentId + "/move?direction=UP", admin, null, 1L));
        JsonNode reordered = ok(getJson("/api/admin/organizations", admin, Map.of(), 1L));
        assertEquals(10, findOrg(reordered, parentId).path("sortOrder").asInt());

        // 恢复同级顺序并清理（排序会重排同级 sortOrder，这里还原种子组织 11 的原值）
        ok(postJson("/api/admin/organizations/" + parentId + "/move?direction=DOWN", admin, null, 1L));
        OrganizationEntity seedSibling = organizationMapper.selectById(11L);
        OrganizationEntity restore = new OrganizationEntity();
        restore.setId(11L);
        restore.setSortOrder(10);
        restore.setVersion(seedSibling.getVersion());
        organizationMapper.updateById(restore);

        // 无子组织/用户后可删除
        ok(call(delete("/api/admin/organizations/" + childId)
                .header("Authorization", "Bearer " + admin)
                .header("X-Campus-Id", "1"), 200));
        ok(call(delete("/api/admin/organizations/" + parentId)
                .header("Authorization", "Bearer " + admin)
                .header("X-Campus-Id", "1"), 200));
        JsonNode after = ok(getJson("/api/admin/organizations", admin, Map.of(), 1L));
        assertNull(findOrg(after, parentId));
    }

    @Test
    void csvTemplatePreviewAndSyncImportWithConflicts() throws Exception {
        String admin = login("admin");
        String suffix = String.valueOf(System.nanoTime() % 1000000);
        String userA = "imp_a_" + suffix;
        String userB = "imp_b_" + suffix;
        String csvHeader = "username,displayName,phone,email,studentNo,college,identityType,organizationCode,roleCodes\n";
        String csv = csvHeader
                + String.join(",", userA, "导入同学A", "", "a@example.com", "", "计算机学院", "STUDENT", "REPAIR_TEAM", "REQUESTER") + "\n"
                + String.join(",", userB, "导入同学B", "", "", "", "", "STUDENT", "ROOT", "") + "\n"
                + String.join(",", "no", "昵称不合法", "", "", "", "", "STUDENT", "", "REQUESTER") + "\n";

        // 模板下载
        MvcResult template = mockMvc.perform(get("/api/admin/users/import-template")
                        .header("Authorization", "Bearer " + admin)
                        .header("X-Campus-Id", "1"))
                .andReturn();
        assertEquals(200, template.getResponse().getStatus());
        String templateBody = template.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(templateBody.contains("username,displayName"));
        assertTrue(template.getResponse().getHeader("Content-Disposition").contains("users-import-template.csv"));

        // 预览：1 新建 + 1 新建 + 1 错误
        JsonNode preview = ok(upload("/api/admin/users/import/preview", "SKIP", csv.getBytes(StandardCharsets.UTF_8), admin));
        assertTrue(preview.path("dryRun").asBoolean());
        assertEquals(3, preview.path("total").asInt());
        assertEquals(2, preview.path("created").asInt());
        assertEquals(1, preview.path("failed").asInt());
        assertEquals("ERROR", preview.path("rows").get(2).path("action").asText());

        // 正式导入（SKIP）
        JsonNode created = ok(upload("/api/admin/users/import", "SKIP", csv.getBytes(StandardCharsets.UTF_8), admin));
        assertFalse(created.path("dryRun").asBoolean());
        assertEquals(2, created.path("created").asInt());
        assertEquals(1, created.path("failed").asInt());
        long userIdA = created.path("rows").get(0).path("userId").asLong();
        assertTrue(userIdA > 0);

        // 再次 SKIP：两名已存在用户被跳过
        JsonNode skipped = ok(upload("/api/admin/users/import", "SKIP", csv.getBytes(StandardCharsets.UTF_8), admin));
        assertEquals(0, skipped.path("created").asInt());
        assertEquals(2, skipped.path("skipped").asInt());

        // UPDATE 策略覆盖昵称并追加角色
        String updateCsv = csvHeader
                + String.join(",", userA, "导入同学A改名", "", "", "", "计算机学院", "STUDENT", "REPAIR_TEAM", "REQUESTER") + "\n";
        JsonNode updated = ok(upload("/api/admin/users/import", "UPDATE", updateCsv.getBytes(StandardCharsets.UTF_8), admin));
        assertEquals(1, updated.path("updated").asInt());
        JsonNode detail = ok(getJson("/api/admin/users/" + userIdA, admin, Map.of(), 1L));
        assertEquals("导入同学A改名", detail.path("displayName").asText());
        assertEquals("维修班组", detail.path("defaultOrganizationName").asText());

        // 文件内用户名重复 -> ERROR
        String duplicateCsv = "username,displayName\n" + userA + ",A\n" + userA + ",B\n";
        JsonNode duplicate = ok(upload("/api/admin/users/import/preview", "SKIP",
                duplicateCsv.getBytes(StandardCharsets.UTF_8), admin));
        assertEquals(1, duplicate.path("failed").asInt());

        // 缺少必需列 -> 400
        JsonNode badHeader = call(multipart("/api/admin/users/import/preview")
                .file(new MockMultipartFile("file", "users.csv", "text/csv",
                        "nickname\nabc\n".getBytes(StandardCharsets.UTF_8)))
                .param("strategy", "SKIP")
                .header("Authorization", "Bearer " + admin)
                .header("X-Campus-Id", "1"), 400);
        assertEquals("COMMON_400", badHeader.path("code").asText());
    }

    @Test
    void ssoStatusIsReadOnlyNotConfigured() throws Exception {
        String admin = login("admin");
        JsonNode sso = ok(getJson("/api/admin/users/sso-status", admin, Map.of(), 1L));
        assertEquals("NOT_CONFIGURED", sso.path("status").asText());
        assertFalse(sso.path("configured").asBoolean());
        assertFalse(sso.path("implemented").asBoolean());
        assertTrue(sso.path("envKeys").size() >= 2);
        assertTrue(sso.path("message").asText().contains("学校"));
    }

    // ---- helpers ----

    private JsonNode upload(String path, String strategy, byte[] content, String token) throws Exception {
        MockHttpServletRequestBuilder builder = multipart(path)
                .file(new MockMultipartFile("file", "users.csv", "text/csv", content))
                .param("strategy", strategy)
                .header("Authorization", "Bearer " + token)
                .header("X-Campus-Id", "1");
        return call(builder, 200);
    }

    private JsonNode findOrg(JsonNode list, long organizationId) {
        for (JsonNode item : list) {
            if (item.path("organizationId").asLong() == organizationId) {
                return item;
            }
        }
        return null;
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

    private JsonNode getJson(String path, String token, Map<String, String> params, Long campusId) throws Exception {
        MockHttpServletRequestBuilder builder = get(path);
        params.forEach(builder::param);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (campusId != null) builder.header("X-Campus-Id", String.valueOf(campusId));
        return call(builder, 200);
    }

    private JsonNode postJson(String path, String token, Object body) throws Exception {
        return postJson(path, token, body, null);
    }

    private JsonNode postJson(String path, String token, Object body, Long campusId) throws Exception {
        MockHttpServletRequestBuilder builder = post(path)
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) builder.content(objectMapper.writeValueAsBytes(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (campusId != null) builder.header("X-Campus-Id", String.valueOf(campusId));
        return call(builder, 200);
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(builder).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(expectedStatus, result.getResponse().getStatus(), "响应体: " + body);
        return objectMapper.readTree(body);
    }
}
