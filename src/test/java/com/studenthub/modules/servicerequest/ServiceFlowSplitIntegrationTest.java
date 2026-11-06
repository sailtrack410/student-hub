package com.studenthub.modules.servicerequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studenthub.modules.file.entity.FileStorageEntity;
import com.studenthub.modules.file.mapper.FileStorageMapper;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real HTTP/security/database transitions; isolated test fixtures never call school or production services. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:serviceflowsplit;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=DAY")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceFlowSplitIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkOrderMapper orders;
    @Autowired FileStorageMapper files;
    private final Map<String, String> tokens = new HashMap<>();
    private Map<String, Object> leaveCatalog;
    private static final String LEAVE = """
            {"itemId":2001,"formData":{"leaveType":"事假","startDate":"2026-11-10","endDate":"2026-11-11","reason":"流程验证"}}
            """;

    @BeforeEach void rememberCatalog() {
        leaveCatalog = jdbc.queryForMap("SELECT flow_type, item_name FROM service_item WHERE id=2001");
    }

    @AfterEach void restoreCatalog() {
        jdbc.update("UPDATE service_item SET flow_type=?, item_name=? WHERE id=2001",
                leaveCatalog.get("flow_type"), leaveCatalog.get("item_name"));
    }

    @Test void leaveApprovalAndCloseAreIndependentOfMaintenance() throws Exception {
        String key = UUID.randomUUID().toString();
        JsonNode submitted = call(post("/api/app/service-requests").header("Idempotency-Key", key), "requester", LEAVE, 200);
        long id = submitted.path("requestId").asLong();
        assertEquals("LEAVE_APPROVAL", submitted.path("flowType").asText());
        assertEquals("AFFAIR-LEAVE", submitted.path("itemCode").asText());
        assertEquals("PENDING_APPROVAL", submitted.path("businessStatus").asText());
        assertEquals("待审批", submitted.path("statusLabel").asText());
        assertEquals(2, submitted.path("leaveDays").asInt());
        assertTrue(submitted.hasNonNull("leaveStartAt"));
        assertTrue(submitted.hasNonNull("leaveEndAt"));
        assertTrue(hasAction(submitted, "CANCEL"));
        assertTrue(hasAction(submitted, "URGE"));
        assertNoOrder(id, submitted);
        assertEquals(id, call(post("/api/app/service-requests").header("Idempotency-Key", key), "requester", LEAVE, 200).path("requestId").asLong());

        call(post("/api/admin/leave-requests/" + id + "/approve"), "requester", "{}", 403);
        call(post("/api/admin/leave-requests/" + id + "/approve"), "worker", "{}", 403);
        call(post("/api/admin/leave-requests/" + id + "/approve"), "dispatcher", "{}", 404);
        call(get("/api/app/service-requests/" + id), "worker", null, 404);
        call(post("/api/app/service-requests/" + id + "/cancel"), "worker", "{\"reason\":\"越权\"}", 404);

        JsonNode urged = call(post("/api/app/service-requests/" + id + "/urge"), "requester", null, 200);
        assertTrue(urged.path("urged").asBoolean());
        assertFalse(urged.path("message").asText().contains("师傅"));
        assertFalse(call(post("/api/app/service-requests/" + id + "/urge"), "requester", null, 200).path("urged").asBoolean());
        assertTrue(count("SELECT COUNT(*) FROM notification WHERE related_id=? AND recipient_id=1001 AND title='申请催办提醒' AND related_type='SERVICE_REQUEST'", id) > 0);
        assertEquals(0, count("SELECT COUNT(*) FROM notification WHERE related_id=? AND recipient_id IN (1003,1004)", id));

        call(post("/api/admin/leave-requests/" + id + "/approve"), "admin", "{\"comment\":\"同意申请\"}", 200);
        JsonNode approved = detail(id);
        assertEquals("APPROVED", approved.path("businessStatus").asText());
        assertEquals("同意申请", approved.path("approvalComment").asText());
        assertEquals(0, approved.path("availableActions").size());
        assertEquals(1, count("SELECT COUNT(*) FROM service_request_log WHERE request_id=? AND action='APPROVE'", id));
        assertEquals(0, count("SELECT COUNT(*) FROM service_request_log WHERE request_id=? AND action='DISPATCH'", id));
        assertNoOrder(id, approved);
        JsonNode home = call(get("/api/app/home/dashboard"), "requester", null, 200).path("inProgressRequest");
        assertEquals(id, home.path("requestId").asLong());
        assertEquals("LEAVE_APPROVAL", home.path("flowType").asText());
        assertEquals("APPROVED", home.path("businessStatus").asText());
        assertEquals("已批准，待销假", home.path("statusLabel").asText());
        assertFalse(home.hasNonNull("orderNo"));
        assertTrue(hasAction(call(get("/api/admin/service-requests/" + id), "admin", null, 200), "LEAVE_CANCEL"));
        call(post("/api/admin/leave-requests/" + id + "/approve"), "admin", "{}", 409);
        call(post("/api/admin/leave-requests/" + id + "/reject"), "admin", "{\"reason\":\"重复审批\"}", 409);
        call(post("/api/app/service-requests/" + id + "/urge"), "requester", null, 409);
        call(post("/api/app/service-requests/" + id + "/cancel"), "requester", "{\"reason\":\"撤回\"}", 409);

        call(post("/api/admin/leave-requests/" + id + "/cancel"), "admin", "{\"comment\":\"已返校\"}", 200);
        JsonNode closed = detail(id);
        assertEquals("COMPLETED", closed.path("status").asText());
        assertEquals("CLOSED", closed.path("businessStatus").asText());
        assertEquals("已销假", closed.path("statusLabel").asText());
        assertTrue(closed.hasNonNull("leaveCancelAt"));
        assertTrue(closed.hasNonNull("completedAt"));
        assertEquals(0, closed.path("availableActions").size());
        assertEquals(1, count("SELECT COUNT(*) FROM service_request_log WHERE request_id=? AND action='LEAVE_CANCEL'", id));
        call(post("/api/admin/leave-requests/" + id + "/cancel"), "admin", "{}", 409);
        call(post("/api/app/service-requests/" + id + "/evaluation"), "requester", "{\"rating\":5}", 409);
        assertNoOrder(id, closed);
    }

    @Test void leaveRejectWithdrawalAndInvalidRangeDoNotCreateOrders() throws Exception {
        long rejected = submit(LEAVE).path("requestId").asLong();
        call(post("/api/admin/leave-requests/" + rejected + "/reject"), "admin", "{\"reason\":\"材料不完整\"}", 200);
        assertEquals("REJECTED", detail(rejected).path("businessStatus").asText());
        assertEquals("材料不完整", detail(rejected).path("rejectReason").asText());
        call(post("/api/admin/leave-requests/" + rejected + "/reject"), "admin", "{\"reason\":\"重复\"}", 409);
        call(post("/api/admin/leave-requests/" + rejected + "/cancel"), "admin", "{}", 409);
        assertNoOrder(rejected, detail(rejected));
        long cancelled = submit(LEAVE).path("requestId").asLong();
        call(post("/api/app/service-requests/" + cancelled + "/cancel"), "requester", "{\"reason\":\"行程取消\"}", 200);
        assertEquals("CANCELLED", detail(cancelled).path("businessStatus").asText());
        JsonNode adminCancelled = call(get("/api/admin/leave-requests?pageSize=100&status=WITHDRAWN"), "admin", null, 200);
        boolean foundWithdrawal = false;
        for (JsonNode row : adminCancelled.path("records")) {
            if (row.path("requestId").asLong() == cancelled) {
                foundWithdrawal = true;
                assertEquals("CANCELLED", row.path("businessStatus").asText());
                assertEquals("已撤回", row.path("statusLabel").asText());
                assertEquals(0, row.path("availableActions").size());
                assertEquals("LEAVE_APPROVAL", row.path("flowType").asText());
            }
        }
        assertTrue(foundWithdrawal);
        call(post("/api/app/service-requests/" + cancelled + "/cancel"), "requester", "{\"reason\":\"重复撤回\"}", 409);
        call(post("/api/admin/leave-requests/" + cancelled + "/approve"), "admin", "{}", 409);
        assertNoOrder(cancelled, detail(cancelled));
        ObjectNode invalid = (ObjectNode) json.readTree(LEAVE);
        ((ObjectNode) invalid.path("formData")).put("endDate", "2026-11-09");
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM service_request", Integer.class);
        call(post("/api/app/service-requests"), "requester", invalid.toString(), 400);
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM service_request", Integer.class));
    }

    @Test void venueAndCertificateUseManualAcceptanceAndCompletion() throws Exception {
        String[] requests = {
                "{\"itemId\":2002,\"formData\":{\"activityName\":\"班级活动\",\"venue\":\"报告厅\",\"useDate\":\"2026-11-20\",\"description\":\"人工核实场地\"}}",
                "{\"itemId\":3001,\"formData\":{\"purpose\":\"实习求职\",\"copies\":2}}"
        };
        for (String body : requests) {
            JsonNode submitted = submit(body);
            long id = submitted.path("requestId").asLong();
            assertEquals("MANUAL", submitted.path("flowType").asText());
            assertEquals("PENDING_ACCEPTANCE", submitted.path("businessStatus").asText());
            assertNoOrder(id, submitted);
            call(post("/api/admin/leave-requests/" + id + "/approve"), "admin", "{}", 404);
            call(post("/api/admin/service-requests/" + id + "/complete"), "admin", "{}", 409);
            call(post("/api/admin/service-requests/" + id + "/review"), "admin", "{\"action\":\"APPROVE\",\"comment\":\"人工受理\"}", 200);
            assertEquals("PROCESSING", detail(id).path("businessStatus").asText());
            call(post("/api/admin/service-requests/" + id + "/complete"), "requester", "{}", 403);
            call(post("/api/admin/service-requests/" + id + "/complete"), "admin", "{\"comment\":\"人工核验办结\"}", 200);
            JsonNode completed = detail(id);
            assertEquals("COMPLETED", completed.path("businessStatus").asText());
            assertEquals(0, completed.path("availableActions").size());
            assertNoOrder(id, completed);
            call(post("/api/admin/service-requests/" + id + "/complete"), "admin", "{}", 409);
        }
        call(post("/api/app/service-requests"), "requester", "{\"itemId\":4001,\"formData\":{\"questionType\":\"办事流程\",\"description\":\"指南咨询\"}}", 400);
    }

    @Test void flowSnapshotsSurviveCatalogChangesAndNamesNeverSelectRouting() throws Exception {
        JsonNode submitted = submit(LEAVE);
        long id = submitted.path("requestId").asLong();
        jdbc.update("UPDATE service_item SET flow_type='REPAIR', item_name='改名后的目录' WHERE id=2001");
        assertEquals("LEAVE_APPROVAL", detail(id).path("flowType").asText());
        call(post("/api/admin/leave-requests/" + id + "/approve"), "admin", "{}", 200);
        assertNoOrder(id, detail(id));

        String code = "REPAIR-TEST-" + UUID.randomUUID().toString().substring(0, 8);
        String create = "{\"categoryId\":100,\"itemCode\":\"" + code + "\",\"itemName\":\"请假报修名称不决定流程\",\"handlerOrganizationId\":12,\"formSchema\":[]}";
        long itemId = call(post("/api/admin/catalog/items"), "admin", create, 200).asLong();
        assertEquals("MANUAL", call(get("/api/app/services/" + itemId), "requester", null, 200).path("flowType").asText());
        JsonNode manual = submit("{\"itemId\":" + itemId + ",\"formData\":{}}");
        assertNoOrder(manual.path("requestId").asLong(), manual);
        ObjectNode invalid = (ObjectNode) json.readTree(create);
        invalid.put("flowType", "UNKNOWN");
        invalid.put("itemCode", code + "-INVALID");
        call(post("/api/admin/catalog/items"), "admin", invalid.toString(), 400);
    }

    @Test void wrongHistoricalOrderIsRetainedButInvisibleAndCannotBeDispatched() throws Exception {
        JsonNode leave = submit(LEAVE);
        long id = leave.path("requestId").asLong();
        JsonNode dashboardBefore = call(get("/api/admin/dashboard"), "admin", null, 200);
        JsonNode analyticsBefore = call(get("/api/admin/analytics/overview"), "admin", null, 200);
        WorkOrderEntity wrong = new WorkOrderEntity();
        wrong.setOrderNo("WRONG-" + UUID.randomUUID());
        wrong.setRequestId(id); wrong.setItemId(2001L); wrong.setCampusId(1L);
        wrong.setTitle("历史误工单"); wrong.setStatus("PENDING_DISPATCH");
        wrong.setAssignedOrganizationId(10L); wrong.setPriority("NORMAL");
        wrong.setFlowArchived(false); wrong.setPhotoWorkflowRequired(false);
        orders.insert(wrong);
        assertFalse(detail(id).hasNonNull("workOrder"));
        JsonNode queue = call(get("/api/admin/work-orders?pageSize=100"), "admin", null, 200);
        for (JsonNode row : queue.path("records")) assertNotEquals(id, row.path("requestId").asLong());
        call(post("/api/admin/work-orders/" + wrong.getId() + "/assign"), "admin", "{\"assigneeId\":1003}", 404);
        call(post("/api/app/work-orders/" + wrong.getId() + "/start"), "worker", null, 404);
        assertNotNull(orders.selectById(wrong.getId()));
        assertEquals("PENDING_DISPATCH", orders.selectById(wrong.getId()).getStatus());
        assertEquals("PENDING_APPROVAL", detail(id).path("businessStatus").asText());
        JsonNode dashboardAfter = call(get("/api/admin/dashboard"), "admin", null, 200);
        for (String field : new String[]{"pendingWorkOrders", "weekNewWorkOrders", "weekResolvedWorkOrders", "trend", "latestOrders"}) {
            assertEquals(dashboardBefore.path(field), dashboardAfter.path(field), field);
        }
        JsonNode analyticsAfter = call(get("/api/admin/analytics/overview"), "admin", null, 200);
        assertEquals(analyticsBefore.path("kpis").path("workOrdersCreated"), analyticsAfter.path("kpis").path("workOrdersCreated"));
        assertEquals(analyticsBefore.path("workOrderStatus"), analyticsAfter.path("workOrderStatus"));
    }

    @Test void repairPhotosReturnForReworkAcceptanceAndEvaluationStillWork() throws Exception {
        JsonNode submitted = submit("""
                {"itemId":1001,"formData":{"contactPhone":"13800000001","dormBuilding":"博宁社区-博梦1","address":"302","faultType":"灯","description":"维修回归"}}
                """);
        long id = submitted.path("requestId").asLong();
        long order = submitted.path("workOrder").path("orderId").asLong();
        assertTrue(order > 0);
        assertEquals("REPAIR", submitted.path("flowType").asText());
        assertTrue(submitted.path("workOrder").path("photoWorkflowRequired").asBoolean());
        call(post("/api/admin/service-requests/" + id + "/review"), "admin", "{\"action\":\"APPROVE\"}", 409);
        call(post("/api/admin/work-orders/" + order + "/assign"), "dispatcher", "{\"assigneeId\":1003}", 200);
        call(post("/api/app/work-orders/" + order + "/start"), "worker", null, 200);
        long arrival = workerImage("arrival");
        long completion = workerImage("completion");
        call(post("/api/app/work-orders/" + order + "/arrival-photo"), "worker", "{\"fileId\":" + arrival + "}", 200);
        call(post("/api/app/work-orders/" + order + "/completion-photo"), "worker", "{\"fileId\":" + completion + ",\"remark\":\"修复提交\"}", 200);
        assertTrue(hasAction(detail(id), "CONFIRM"));
        assertTrue(hasAction(detail(id), "REJECT_COMPLETION"));
        call(post("/api/app/work-orders/" + order + "/reject-confirmation"), "requester", "{\"reason\":\"仍有故障\"}", 200);
        assertEquals("ONSITE_PHOTO_RECORDED", detail(id).path("businessStatus").asText());
        call(post("/api/app/work-orders/" + order + "/completion-photo"), "worker", "{\"fileId\":" + completion + ",\"remark\":\"返工完成\"}", 200);
        call(post("/api/app/work-orders/" + order + "/confirm"), "worker", null, 403);
        call(post("/api/app/work-orders/" + order + "/confirm"), "requester", null, 200);
        assertEquals("COMPLETED", detail(id).path("businessStatus").asText());
        assertTrue(hasAction(detail(id), "EVALUATE"));
        call(post("/api/app/service-requests/" + id + "/evaluation"), "requester", "{\"rating\":5,\"comment\":\"修复完成\"}", 200);
        assertFalse(hasAction(detail(id), "EVALUATE"));
        call(post("/api/app/service-requests/" + id + "/evaluation"), "requester", "{\"rating\":5}", 409);
    }

    private long workerImage(String name) {
        FileStorageEntity file = new FileStorageEntity();
        file.setStorageKey("test/flow-" + name + ".png"); file.setOriginalName(name + ".png");
        file.setContentType("image/png"); file.setFileSize(512L); file.setUploaderId(1003L);
        files.insert(file);
        return file.getId();
    }

    private JsonNode detail(long id) throws Exception { return call(get("/api/app/service-requests/" + id), "requester", null, 200); }
    private JsonNode submit(String body) throws Exception { return call(post("/api/app/service-requests"), "requester", body, 200); }
    private void assertNoOrder(long id, JsonNode detail) {
        assertFalse(detail.hasNonNull("workOrder"));
        assertEquals(0, count("SELECT COUNT(*) FROM work_order WHERE request_id=?", id));
    }
    private int count(String sql, long id) { return jdbc.queryForObject(sql, Integer.class, id); }
    private boolean hasAction(JsonNode data, String action) {
        for (JsonNode value : data.path("availableActions")) if (action.equals(value.asText())) return true;
        return false;
    }
    private JsonNode call(MockHttpServletRequestBuilder request, String username, String body, int expected) throws Exception {
        if (!tokens.containsKey(username)) {
            String login = mvc.perform(post("/api/common/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + username + "\",\"password\":\"DevOnly123!\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            tokens.put(username, json.readTree(login).path("data").path("accessToken").asText());
        }
        request.header("Authorization", "Bearer " + tokens.get(username));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return json.readTree(mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString()).path("data");
    }
}
