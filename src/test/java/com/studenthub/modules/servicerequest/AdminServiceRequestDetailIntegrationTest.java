package com.studenthub.modules.servicerequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.file.entity.FileStorageEntity;
import com.studenthub.modules.file.mapper.FileStorageMapper;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端审批详情回归：申请人脱敏手机号、表单图片签名材料、工单 SLA、时间线与审批动作。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminServiceRequestDetailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileStorageMapper fileStorageMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    void adminDetailRendersRequesterFormMaterialsWorkOrderAndTimeline() throws Exception {
        // 申请人手机号脱敏展示：测试库补一条手机号，断言只看到中间掩码
        UserEntity requester = userMapper.selectById(1002L);
        requester.setPhone("13812345678");
        requester.setDisplayName("开发申请人");
        userMapper.updateById(requester);

        String requesterToken = login("requester");
        String adminToken = login("admin");

        // 上传材料：直接插一条图片文件行（申请提交只做字段归一化，不校验上传人）
        FileStorageEntity file = new FileStorageEntity();
        file.setStorageKey("test/admin-detail.png");
        file.setOriginalName("admin-detail.png");
        file.setContentType("image/png");
        file.setFileSize(1024L);
        file.setUploaderId(1002L);
        fileStorageMapper.insert(file);

        MvcResult submitResult = mockMvc.perform(post("/api/app/service-requests")
                        .header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":1001,"title":"宿舍台灯不亮","formData":{"contactPhone":"13800000001","dormBuilding":"博宁社区-博梦1","address":"302","faultType":"灯","description":"台灯不亮","photos":[%d]}}
                                """.formatted(file.getId())))
                .andExpect(status().isOk())
                .andReturn();
        long requestId = objectMapper.readTree(submitResult.getResponse().getContentAsString())
                .path("data").path("requestId").asLong();

        // 审批详情：申请人 / 表单 / 图片材料（签名 URL） / 时间线 / 可执行动作
        mockMvc.perform(get("/api/admin/service-requests/" + requestId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requester.displayName").value("开发申请人"))
                .andExpect(jsonPath("$.data.requester.phoneMasked").value("138****5678"))
                .andExpect(jsonPath("$.data.handlerOrganizationName").value("维修班组"))
                .andExpect(jsonPath("$.data.formData.contactPhone").value("13800000001"))
                .andExpect(jsonPath("$.data.formData.address").value("302"))
                .andExpect(jsonPath("$.data.materials[0].fieldKey").value("photos"))
                .andExpect(jsonPath("$.data.materials[0].urls[0]", containsString("/api/common/files/view/")))
                .andExpect(jsonPath("$.data.timeline[0].action").value("SUBMIT"))
                .andExpect(jsonPath("$.data.availableActions").isEmpty())
                .andExpect(jsonPath("$.data.workOrder.slaHours").value(24))
                .andExpect(jsonPath("$.data.workOrder.dueAt").exists())
                .andExpect(jsonPath("$.data.workOrder.overdue").value(false));

        // 报修禁止用通用审批绕过派单：状态和维修队列保持一致。
        mockMvc.perform(post("/api/admin/service-requests/" + requestId + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"comment\":\"材料齐全，转维修班组\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/admin/service-requests/" + requestId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.availableActions").isEmpty())
                .andExpect(jsonPath("$.data.timeline[?(@.action == 'DISPATCH')]").isEmpty());
    }

    @Test
    void nonAdminCannotReadAdminDetailAndMissingRequestReturns404() throws Exception {
        String requesterToken = login("requester");
        String adminToken = login("admin");

        mockMvc.perform(get("/api/admin/service-requests/123456789")
                        .header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/service-requests/123456789")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUEST_404_01"));
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
