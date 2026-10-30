package com.studenthub.modules.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.studenthub.modules.community.AdminApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 组织架构单根树 + 校区范围（V85）集成测试。
 *
 * <p>覆盖：根节点为校级、校级部门、具体校区学院、跨校区学院、跨校区学院下的班级、
 * 父组织与子组织校区不一致时拒绝、根节点不可重复创建。</p>
 */
class AdminOrganizationScopeIntegrationTest extends AdminApiTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void singleRootWithCampusScope() throws Exception {
        String admin = login("admin");
        long suffix = System.nanoTime() % 1_000_000_000L;

        JsonNode list = ok(getJson("/api/admin/organizations", admin, Map.of()));
        JsonNode root = find(list, 10L);
        assertTrue(root != null, "种子根组织应存在");
        assertEquals("ROOT", root.path("organizationType").asText());
        assertEquals("SCHOOL", root.path("campusScope").asText());
        assertTrue(root.path("campusId").isNull() || root.path("campusId").isMissingNode());

        JsonNode createdCampus = ok(postJson("/api/admin/campuses", admin, Map.of(
                "campusCode", "SCOPE" + suffix,
                "campusName", "范围测试校区")));
        long otherCampusId = createdCampus.path("campusId").asLong();

        Long schoolOrgId = null;
        Long collegeOrgId = null;
        Long multiOrgId = null;
        Long majorOrgId = null;
        Long classOrgId = null;
        try {
            // 1. 校级部门：root 下、campus_id 为空
            JsonNode schoolOrg = ok(postJson("/api/admin/organizations", admin, Map.of(
                    "parentId", 10,
                    "organizationType", "DEPARTMENT",
                    "campusScope", "SCHOOL",
                    "organizationCode", "SCOPE_SCHOOL_" + suffix,
                    "organizationName", "校级测试部门",
                    "status", "ENABLED")));
            schoolOrgId = schoolOrg.path("organizationId").asLong();
            assertEquals("SCHOOL", schoolOrg.path("campusScope").asText());
            assertTrue(schoolOrg.path("campusId").isNull());

            // 2. 具体校区学院
            JsonNode college = ok(postJson("/api/admin/organizations", admin, Map.of(
                    "parentId", 10,
                    "organizationType", "COLLEGE",
                    "campusScope", "CAMPUS",
                    "campusId", 1,
                    "organizationCode", "SCOPE_COLLEGE_" + suffix,
                    "organizationName", "校区测试学院",
                    "status", "ENABLED")));
            collegeOrgId = college.path("organizationId").asLong();
            assertEquals("CAMPUS", college.path("campusScope").asText());
            assertEquals(1L, college.path("campusId").asLong());

            // 3. 跨校区学院 + 其下具体校区班级
            JsonNode multi = ok(postJson("/api/admin/organizations", admin, Map.of(
                    "parentId", 10,
                    "organizationType", "COLLEGE",
                    "campusScope", "MULTI",
                    "organizationCode", "SCOPE_MULTI_" + suffix,
                    "organizationName", "跨校区测试学院",
                    "status", "ENABLED")));
            multiOrgId = multi.path("organizationId").asLong();
            assertEquals("MULTI", multi.path("campusScope").asText());
            assertTrue(multi.path("campusId").isNull());

            JsonNode classOrg = ok(postJson("/api/admin/organizations", admin, Map.of(
                    "parentId", multiOrgId,
                    "organizationType", "CLASS",
                    "campusScope", "CAMPUS",
                    "campusId", 1,
                    "organizationCode", "SCOPE_CLASS_" + suffix,
                    "organizationName", "跨校区学院测试班",
                    "status", "ENABLED")));
            classOrgId = classOrg.path("organizationId").asLong();
            assertEquals("CAMPUS", classOrg.path("campusScope").asText());

            JsonNode majorOrg = ok(postJson("/api/admin/organizations", admin, Map.of(
                    "parentId", collegeOrgId,
                    "organizationType", "MAJOR",
                    "campusScope", "CAMPUS",
                    "campusId", 1,
                    "organizationCode", "SCOPE_MAJOR_OK_" + suffix,
                    "organizationName", "校区测试专业",
                    "status", "ENABLED")));
            majorOrgId = majorOrg.path("organizationId").asLong();

            // 4. 父组织调整到其他校区时，已存在的子组织必须仍属于同一校区
            JsonNode childMismatch = call(putRequest("/api/admin/organizations/" + collegeOrgId, admin, Map.of(
                    "parentId", 10,
                    "organizationType", "COLLEGE",
                    "campusScope", "CAMPUS",
                    "campusId", otherCampusId,
                    "organizationCode", "SCOPE_COLLEGE_" + suffix,
                    "organizationName", "校区测试学院",
                    "status", "ENABLED")), 400);
            assertEquals("COMMON_400", childMismatch.path("code").asText());
            assertTrue(childMismatch.path("message").asText().contains("不属于目标校区"));

            // 5. 具体校区父组织下挂其他校区的子组织：拒绝
            JsonNode mismatch = call(postRequest("/api/admin/organizations", admin, Map.of(
                    "parentId", collegeOrgId,
                    "organizationType", "MAJOR",
                    "campusScope", "CAMPUS",
                    "campusId", otherCampusId,
                    "organizationCode", "SCOPE_MAJOR_" + suffix,
                    "organizationName", "跨校区测试专业",
                    "status", "ENABLED")), 400);
            assertEquals("COMMON_400", mismatch.path("code").asText());
            assertTrue(mismatch.path("message").asText().contains("同一校区"));

            // 6. 根组织不可重复创建
            JsonNode secondRoot = call(postRequest("/api/admin/organizations", admin, Map.of(
                    "organizationType", "ROOT",
                    "campusScope", "SCHOOL",
                    "organizationCode", "SCOPE_ROOT_" + suffix,
                    "organizationName", "第二个根",
                    "status", "ENABLED")), 400);
            assertEquals("COMMON_400", secondRoot.path("code").asText());
            assertTrue(secondRoot.path("message").asText().contains("已存在根组织"));

            // 7. 列表能反映三种范围
            JsonNode after = ok(getJson("/api/admin/organizations", admin, Map.of()));
            assertEquals("SCHOOL", find(after, schoolOrgId).path("campusScope").asText());
            assertEquals("CAMPUS", find(after, collegeOrgId).path("campusScope").asText());
            assertEquals("MULTI", find(after, multiOrgId).path("campusScope").asText());
            assertEquals(3, find(after, classOrgId).path("level").asInt());
        } finally {
            deleteIfPresent(admin, classOrgId);
            deleteIfPresent(admin, multiOrgId);
            deleteIfPresent(admin, majorOrgId);
            deleteIfPresent(admin, collegeOrgId);
            deleteIfPresent(admin, schoolOrgId);
        }
    }


    @Test
    void campusAdminCanOnlyMaintainOwnCampusOrganizations() throws Exception {
        String admin = login("admin");
        long suffix = System.nanoTime() % 1_000_000_000L;
        long roleId = 9_100_000_000_000_000L + suffix;
        Long permissionId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_permission WHERE permission_code = ?", Long.class,
                "system:organization:manage");
        assertTrue(permissionId != null, "组织管理权限应存在");
        Long multiOrgId = null;
        Long ownCampusOrgId = null;
        try {
            // 给普通用户临时绑定“组织管理”角色，但不授予平台管理员权限。
            jdbcTemplate.update("INSERT INTO sys_role (id, role_code, role_name, role_type, status, "
                            + "created_at, updated_at, created_by, updated_by) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, "
                            + "CURRENT_TIMESTAMP, 1001, 1001)",
                    roleId, "ORG_MANAGER_" + suffix, "校区组织管理员", "ADMIN", "ENABLED");
            jdbcTemplate.update("INSERT INTO sys_role_permission (id, role_id, permission_id, created_at, created_by) "
                    + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, 1001)", roleId + 1, roleId, permissionId);
            jdbcTemplate.update("INSERT INTO sys_user_role (id, user_id, role_id, organization_id, data_scope, "
                            + "created_at, created_by) VALUES (?, 1002, ?, 10, 'ORGANIZATION_AND_CHILDREN', "
                            + "CURRENT_TIMESTAMP, 1001)",
                    roleId + 2, roleId);
            // 角色授权后重新登录，确保会话拿到最新权限集合。
            String requester = login("requester");

            // 1. 普通管理员不能建校级/跨校区节点。
            JsonNode schoolDenied = call(post("/api/admin/organizations")
                    .header("Authorization", "Bearer " + requester)
                    .header("X-Campus-Id", "1")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "parentId", 10,
                            "organizationType", "DEPARTMENT",
                            "campusScope", "SCHOOL",
                            "organizationCode", "REQ_SCHOOL_" + suffix,
                            "organizationName", "普通管理员校级组织",
                            "status", "ENABLED"))), 403);
            assertEquals("AUTH_403", schoolDenied.path("code").asText());

            // 2. 可在根下创建所属校区的学院，并能编辑/删除。
            JsonNode ownCampusOrg = ok(call(post("/api/admin/organizations")
                    .header("Authorization", "Bearer " + requester)
                    .header("X-Campus-Id", "1")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "parentId", 10,
                            "organizationType", "COLLEGE",
                            "campusScope", "CAMPUS",
                            "campusId", 1,
                            "organizationCode", "REQ_CAMPUS_" + suffix,
                            "organizationName", "普通管理员校区学院",
                            "status", "ENABLED"))), 200));
            ownCampusOrgId = ownCampusOrg.path("organizationId").asLong();
            assertEquals("CAMPUS", ownCampusOrg.path("campusScope").asText());

            // 3. 平台管理员维护的跨校区节点下，普通管理员不能挂子组织。
            JsonNode multi = ok(postJson("/api/admin/organizations", admin, Map.of(
                    "parentId", 10,
                    "organizationType", "COLLEGE",
                    "campusScope", "MULTI",
                    "organizationCode", "REQ_MULTI_" + suffix,
                    "organizationName", "平台跨校区学院",
                    "status", "ENABLED")));
            multiOrgId = multi.path("organizationId").asLong();
            JsonNode childDenied = call(post("/api/admin/organizations")
                    .header("Authorization", "Bearer " + requester)
                    .header("X-Campus-Id", "1")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "parentId", multiOrgId,
                            "organizationType", "CLASS",
                            "campusScope", "CAMPUS",
                            "campusId", 1,
                            "organizationCode", "REQ_CLASS_" + suffix,
                            "organizationName", "普通管理员跨校区班级",
                            "status", "ENABLED"))), 403);
            assertEquals("AUTH_403", childDenied.path("code").asText());
        } finally {
            // 清理普通管理员创建的组织：删除操作仍需 X-Campus-Id，回退为平台管理员执行。
            if (ownCampusOrgId != null) {
                call(delete("/api/admin/organizations/" + ownCampusOrgId)
                        .header("Authorization", "Bearer " + admin)
                        .header("X-Campus-Id", "1"), 200);
            }
            deleteIfPresent(admin, multiOrgId);
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id = ?", roleId);
            jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id = ?", roleId);
            jdbcTemplate.update("DELETE FROM sys_role WHERE id = ?", roleId);
        }
    }

    private void deleteIfPresent(String admin, Long organizationId) throws Exception {
        if (organizationId == null) {
            return;
        }
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/admin/organizations/" + organizationId)
                .header("Authorization", "Bearer " + admin), 200);
    }

    private JsonNode find(JsonNode list, Long organizationId) {
        if (list == null || organizationId == null) {
            return null;
        }
        for (JsonNode item : list) {
            if (item.path("organizationId").asLong() == organizationId) {
                return item;
            }
        }
        return null;
    }
}
