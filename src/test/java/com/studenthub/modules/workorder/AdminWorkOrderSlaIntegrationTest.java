package com.studenthub.modules.workorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.workorder.entity.WorkOrderEntity;
import com.studenthub.modules.workorder.mapper.WorkOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端工单 SLA 回归：创建时按服务项 SLA 计算截止时间、超时/临期过滤、统计与负载、改派、批量派发。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminWorkOrderSlaIntegrationTest {

    private static final long WORKER_ONE_ID = 1003L;
    private static final long WORKER_TWO_ID = 1103L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Test
    void submittedRequestCreatesSlaAwareWorkOrderWithStatsAndWorkload() throws Exception {
        String requesterToken = login("requester");
        String dispatcherToken = login("dispatcher");

        long requestId = submitRepairRequest(requesterToken, "SLA 字段验证");
        long orderId = findPendingOrderId(dispatcherToken, requestId);

        // 队列行携带 SLA：承诺 24h、截止时间已写入、未超时、剩余时长 > 0
        MvcResult queueResult = mockMvc.perform(get("/api/admin/work-orders")
                        .param("status", "PENDING_DISPATCH")
                        .param("pageSize", "50")
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode record = findRecord(objectMapper.readTree(queueResult.getResponse().getContentAsString())
                .path("data").path("records"), orderId);
        assertNotNull(record, "调度队列应包含新工单");
        assertEquals(24, record.path("slaHours").asInt());
        assertFalse(record.path("dueAt").isNull());
        assertFalse(record.path("overdue").asBoolean());
        assertTrue(record.path("remainingHours").asDouble() > 0);
        assertFalse(record.path("dueSoon").asBoolean());

        // 注意：JacksonConfig 将 Long/long 序列化为字符串，统计值需按 asLong 断言
        MvcResult statsResult = mockMvc.perform(get("/api/admin/work-orders/stats")
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode stats = objectMapper.readTree(statsResult.getResponse().getContentAsString()).path("data");
        assertTrue(stats.path("pendingDispatch").asLong() >= 1, "应有待派发工单");
        assertTrue(stats.path("inProgressTotal").asLong() >= 1, "应有进行中工单");

        // 负载列表：包含在岗服务人员且给出进行中/超时计数
        MvcResult workloadResult = mockMvc.perform(get("/api/admin/work-orders/workload")
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode workload = objectMapper.readTree(workloadResult.getResponse().getContentAsString()).path("data");
        assertTrue(workload.isArray());
        assertTrue(workload.size() >= 1, "应返回在岗服务人员负载");
        JsonNode worker = findWorkload(workload, WORKER_ONE_ID);
        assertNotNull(worker, "负载列表应包含开发维修员");
        assertTrue(worker.path("activeCount").asLong() >= 0, "负载应给出进行中计数");

        mockMvc.perform(get("/api/admin/work-orders/" + orderId)
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slaHours").value(24))
                .andExpect(jsonPath("$.data.dueAt").exists())
                .andExpect(jsonPath("$.data.timeline").isArray());
    }

    @Test
    void dispatcherCanReassignAndBatchAssignWorkOrders() throws Exception {
        ensureSecondWorker();
        String requesterToken = login("requester");
        String dispatcherToken = login("dispatcher");

        long firstRequestId = submitRepairRequest(requesterToken, "改派验证");
        long firstOrderId = findPendingOrderId(dispatcherToken, firstRequestId);

        mockMvc.perform(post("/api/admin/work-orders/" + firstOrderId + "/assign")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":%d,\"comment\":\"首次派单\"}".formatted(WORKER_ONE_ID)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/work-orders/" + firstOrderId)
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeName").value("开发维修员"));

        mockMvc.perform(post("/api/admin/work-orders/" + firstOrderId + "/reassign")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":%d,\"comment\":\"维修员临时调班\"}".formatted(WORKER_TWO_ID)))
                .andExpect(status().isOk());

        MvcResult detailResult = mockMvc.perform(get("/api/admin/work-orders/" + firstOrderId)
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeName").value("维修员二号"))
                .andReturn();
        JsonNode timeline = objectMapper.readTree(detailResult.getResponse().getContentAsString())
                .path("data").path("timeline");
        boolean reassignLogged = false;
        for (JsonNode log : timeline) {
            if ("DISPATCH".equals(log.path("action").asText())
                    && log.path("comment").asText().contains("改派：")
                    && log.path("comment").asText().contains("维修员二号")) {
                reassignLogged = true;
            }
        }
        assertTrue(reassignLogged, "改派应在申请时间线留下 DISPATCH 记录");

        // 批量派发：两张新申请一次性派给维修员二号
        long secondRequestId = submitRepairRequest(requesterToken, "批量派发 A");
        long thirdRequestId = submitRepairRequest(requesterToken, "批量派发 B");
        long secondOrderId = findPendingOrderId(dispatcherToken, secondRequestId);
        long thirdOrderId = findPendingOrderId(dispatcherToken, thirdRequestId);

        mockMvc.perform(post("/api/admin/work-orders/batch-assign")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderIds\":[%d,%d],\"assigneeId\":%d,\"comment\":\"批量派单\"}"
                                .formatted(secondOrderId, thirdOrderId, WORKER_TWO_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requested").value(2))
                .andExpect(jsonPath("$.data.success").value(2))
                .andExpect(jsonPath("$.data.failures").isEmpty());

        mockMvc.perform(get("/api/admin/work-orders/" + secondOrderId)
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.assigneeName").value("维修员二号"));

        // 批量派发中已派发的工单只记失败，不影响其他工单
        mockMvc.perform(post("/api/admin/work-orders/batch-assign")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderIds\":[%d],\"assigneeId\":%d}".formatted(secondOrderId, WORKER_ONE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(0))
                .andExpect(jsonPath("$.data.failures[0].orderId").value(secondOrderId));
    }

    @Test
    void overdueAndDueSoonFiltersReturnMatchingWorkOrders() throws Exception {
        String requesterToken = login("requester");
        String dispatcherToken = login("dispatcher");

        long overdueRequestId = submitRepairRequest(requesterToken, "超时过滤验证");
        long overdueOrderId = findPendingOrderId(dispatcherToken, overdueRequestId);
        WorkOrderEntity overdueOrder = workOrderMapper.selectById(overdueOrderId);
        LocalDateTime pastDue = LocalDateTime.now().minusHours(2);
        overdueOrder.setDueAt(pastDue);
        overdueOrder.setSlaHours(1);
        workOrderMapper.updateById(overdueOrder);

        MvcResult overdueResult = mockMvc.perform(get("/api/admin/work-orders")
                        .param("overdue", "true")
                        .param("pageSize", "50")
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode overdueRecord = findRecord(objectMapper.readTree(overdueResult.getResponse()
                .getContentAsString()).path("data").path("records"), overdueOrderId);
        assertNotNull(overdueRecord, "超时过滤应返回已超时工单");
        assertTrue(overdueRecord.path("overdue").asBoolean());
        assertTrue(overdueRecord.path("overdueHours").asDouble() > 0);

        MvcResult overdueStatsResult = mockMvc.perform(get("/api/admin/work-orders/stats")
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isOk())
                .andReturn();
        long overdueCount = objectMapper.readTree(overdueStatsResult.getResponse().getContentAsString())
                .path("data").path("overdue").asLong();
        assertTrue(overdueCount >= 1, "统计应包含已超时工单");

        // 距截止 2 小时 → 落在 4 小时临期窗口
        long dueSoonRequestId = submitRepairRequest(requesterToken, "临期过滤验证");
        long dueSoonOrderId = findPendingOrderId(dispatcherToken, dueSoonRequestId);
        WorkOrderEntity dueSoonOrder = workOrderMapper.selectById(dueSoonOrderId);
        dueSoonOrder.setDueAt(LocalDateTime.now().plusHours(2));
        workOrderMapper.updateById(dueSoonOrder);

        MvcResult dueSoonResult = mockMvc.perform(get("/api/admin/work-orders")
                        .param("dueSoon", "true")
                        .param("pageSize", "50")
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode dueSoonRecord = findRecord(objectMapper.readTree(dueSoonResult.getResponse()
                .getContentAsString()).path("data").path("records"), dueSoonOrderId);
        assertNotNull(dueSoonRecord, "临期过滤应返回 4 小时内到期工单");
        assertTrue(dueSoonRecord.path("dueSoon").asBoolean());
    }

    // ---------------------------------------------------------------- helpers

    private long submitRepairRequest(String requesterToken, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/app/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":1001,"title":"%s","formData":{"contactPhone":"13800000001","dormBuilding":"博宁社区-博梦1","address":"302","faultType":"灯","description":"台灯不亮"}}
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("requestId").asLong();
    }

    private long findPendingOrderId(String dispatcherToken, long requestId) throws Exception {
        for (int page = 1; page <= 5; page++) {
            MvcResult result = mockMvc.perform(get("/api/admin/work-orders")
                            .param("status", "PENDING_DISPATCH")
                            .param("pageNum", String.valueOf(page))
                            .param("pageSize", "50")
                            .header("Authorization", "Bearer " + dispatcherToken))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode records = objectMapper.readTree(result.getResponse().getContentAsString())
                    .path("data").path("records");
            for (JsonNode record : records) {
                if (record.path("requestId").asLong() == requestId) {
                    return record.path("orderId").asLong();
                }
            }
        }
        throw new IllegalStateException("未在调度队列找到申请 " + requestId + " 对应的工单");
    }

    private JsonNode findRecord(JsonNode records, long orderId) {
        for (JsonNode record : records) {
            if (record.path("orderId").asLong() == orderId) {
                return record;
            }
        }
        return null;
    }

    private JsonNode findWorkload(JsonNode workload, long userId) {
        for (JsonNode item : workload) {
            if (item.path("userId").asLong() == userId) {
                return item;
            }
        }
        return null;
    }

    /** 测试库仅内置一名服务人员，改派用例补一名可派单的维修员。 */
    private void ensureSecondWorker() {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ?", Integer.class, WORKER_TWO_ID);
        if (existing != null && existing > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, campus_id, default_organization_id, username, password_hash,
                                      display_name, status, created_at, updated_at, created_by, updated_by,
                                      deleted, version)
                VALUES (?, 1, 12, 'worker2', 'x', '维修员二号', 'ENABLED',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0)
                """, WORKER_TWO_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (id, user_id, role_id, organization_id, data_scope, created_at, created_by)
                VALUES (20099, ?, 3, 12, 'ASSIGNED', CURRENT_TIMESTAMP, 0)
                """, WORKER_TWO_ID);
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
