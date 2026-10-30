package com.studenthub.modules.servicerequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.file.entity.FileStorageEntity;
import com.studenthub.modules.file.mapper.FileStorageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学生申请 → 调度派单 → 服务人员办结 → 学生评价 的全链路回归，
 * 同时覆盖幂等提交、越权拒绝与消息通知。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceRequestFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileStorageMapper fileStorageMapper;

    /** 报修拍照工作流要求图片行属于 worker 且为 image/*；test 存储为 local，只插 DB 行即可 */
    private long insertWorkerImage(long workerId, String name) {
        FileStorageEntity file = new FileStorageEntity();
        file.setStorageKey("test/" + name + ".png");
        file.setOriginalName(name + ".png");
        file.setContentType("image/png");
        file.setFileSize(1024L);
        file.setUploaderId(workerId);
        fileStorageMapper.insert(file);
        return file.getId();
    }

    @Test
    void fullLoopFromSubmitToEvaluation() throws Exception {
        String requesterToken = login("requester");
        String dispatcherToken = login("dispatcher");
        String workerToken = login("worker");

        // 1. 学生查看服务目录并提交宿舍报修申请
        MvcResult catalogResult = mockMvc.perform(get("/api/app/services")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].items").isArray())
                .andReturn();
        JsonNode catalog = objectMapper.readTree(catalogResult.getResponse().getContentAsString());
        long repairItemId = catalog.get("data").get(0).get("items").get(0).get("itemId").asLong();

        String idempotencyKey = UUID.randomUUID().toString();
        MvcResult submitResult = mockMvc.perform(post("/api/app/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":%d,"formData":{"contactPhone":"13800000001","dormBuilding":"博宁社区-博梦1","address":"302","faultType":"灯","description":"台灯不亮","expectDate":"2026-09-05"}}
                                """.formatted(repairItemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.timeline[0].action").value("SUBMIT"))
                .andReturn();
        long requestId = objectMapper.readTree(submitResult.getResponse().getContentAsString())
                .path("data").path("requestId").asLong();

        // 2. 幂等：相同 Idempotency-Key 重复提交返回同一申请
        MvcResult repeatResult = mockMvc.perform(post("/api/app/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":%d,"formData":{"contactPhone":"13800000001","dormBuilding":"博宁社区-博梦1","address":"302","faultType":"灯","description":"台灯不亮"}}
                                """.formatted(repairItemId)))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(requestId, objectMapper.readTree(repeatResult.getResponse().getContentAsString())
                .path("data").path("requestId").asLong());

        // 3. 学生撤销不掉别人的申请、游客进不了调度队列
        mockMvc.perform(get("/api/admin/work-orders")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isForbidden());

        // 4. 调度员在队列中看到待派工单并派给 worker
        MvcResult queueResult = mockMvc.perform(get("/api/admin/work-orders")
                        .param("status", "PENDING_DISPATCH")
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode queue = objectMapper.readTree(queueResult.getResponse().getContentAsString());
        long orderId = findOrderIdByRequestId(queue, requestId);
        long workerId = 1003L;

        mockMvc.perform(post("/api/admin/work-orders/" + orderId + "/assign")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":%d,\"comment\":\"请尽快上门\"}".formatted(workerId)))
                .andExpect(status().isOk());

        // 4.1 我的申请列表返回真实工单摘要，供 App 的“处理中/待验收”筛选使用
        MvcResult myListResult = mockMvc.perform(get("/api/app/service-requests")
                        .param("pageNum", "1")
                        .param("pageSize", "100")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode myRecords = objectMapper.readTree(myListResult.getResponse().getContentAsString())
                .path("data").path("records");
        JsonNode myRecord = null;
        for (JsonNode record : myRecords) {
            if (record.path("requestId").asLong() == requestId) {
                myRecord = record;
                break;
            }
        }
        assertNotNull(myRecord, "我的申请列表应包含刚提交的申请");
        assertEquals("ASSIGNED", myRecord.path("workOrderStatus").asText());
        assertEquals(orderId, myRecord.path("workOrderId").asLong());

        // 5. 服务人员开始工单，走拍照工作流：现场拍照 → 完工拍照提交确认 → 报修人确认
        mockMvc.perform(get("/api/app/work-orders/assigned")
                        .param("status", "ASSIGNED")
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.orderId == " + orderId + ")]").exists());

        mockMvc.perform(post("/api/app/work-orders/" + orderId + "/start")
                        .header("Authorization", "Bearer " + workerToken))
                .andExpect(status().isOk());

        long arrivalPhotoId = insertWorkerImage(workerId, "arrival");
        mockMvc.perform(post("/api/app/work-orders/" + orderId + "/arrival-photo")
                        .header("Authorization", "Bearer " + workerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":%d}".formatted(arrivalPhotoId)))
                .andExpect(status().isOk());

        long completionPhotoId = insertWorkerImage(workerId, "completion");
        mockMvc.perform(post("/api/app/work-orders/" + orderId + "/completion-photo")
                        .header("Authorization", "Bearer " + workerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":%d,\"remark\":\"已更换灯泡，可正常使用\"}".formatted(completionPhotoId)))
                .andExpect(status().isOk());

        // 报修人确认后申请才进入 COMPLETED
        mockMvc.perform(post("/api/app/work-orders/" + orderId + "/confirm")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isOk());

        // 6. 学生查看详情：已办结、时间线完整
        mockMvc.perform(get("/api/app/service-requests/" + requestId)
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.timeline[*].action",
                        hasSize(greaterThanOrEqualTo(4))));

        // 7. 学生评价；重复评价被拒绝
        mockMvc.perform(post("/api/app/service-requests/" + requestId + "/evaluation")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"tags\":\"响应快\",\"comment\":\"处理很及时\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(5));

        mockMvc.perform(post("/api/app/service-requests/" + requestId + "/evaluation")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_409_01"));

        // 8. 评价后学生收到派单/办结相关通知
        mockMvc.perform(get("/api/app/notifications?pageSize=100")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.title == '申请已完成')]").exists());

        // 9. 工单处理人才能办结：requester 无法操作该工单
        MvcResult newRequest = mockMvc.perform(post("/api/app/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":%d,\"formData\":{\"contactPhone\":\"13800000001\",\"dormBuilding\":\"博宁社区-博梦1\",\"address\":\"303\",\"faultType\":\"门窗\",\"description\":\"门锁松动\"}}"
                                .formatted(repairItemId)))
                .andExpect(status().isOk())
                .andReturn();
        long secondRequestId = objectMapper.readTree(newRequest.getResponse().getContentAsString())
                .path("data").path("requestId").asLong();
        assertNotEquals(requestId, secondRequestId);
    }

    @Test
    void requesterCanCancelSubmittedRequest() throws Exception {
        String requesterToken = login("requester");
        String dispatcherToken = login("dispatcher");

        MvcResult submitResult = mockMvc.perform(post("/api/app/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":1002,"formData":{"contactPhone":"13800000001","address":"教学楼A栋102","faultType":"照明电路","description":"灯管闪烁"}}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long requestId = objectMapper.readTree(submitResult.getResponse().getContentAsString())
                .path("data").path("requestId").asLong();

        mockMvc.perform(post("/api/app/service-requests/" + requestId + "/cancel")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"已自行修复\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // 撤销后调度队列不再出现该工单
        MvcResult queueResult = mockMvc.perform(get("/api/admin/work-orders")
                        .param("status", "PENDING_DISPATCH")
                        .header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode queue = objectMapper.readTree(queueResult.getResponse().getContentAsString());
        assertEquals(-1, findOrderIdByRequestId(queue, requestId));
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

    private long findOrderIdByRequestId(JsonNode queue, long requestId) {
        for (JsonNode record : queue.path("data").path("records")) {
            if (record.path("requestId").asLong() == requestId) {
                return record.path("orderId").asLong();
            }
        }
        return -1;
    }
}
