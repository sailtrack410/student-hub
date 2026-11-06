package com.studenthub.modules.servicerequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Same observable input on pristine and corrected copies; regression assertions live in FlowSplitIntegrationTest. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceFlowSplitProbeTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void observeLeaveRoute() throws Exception {
        JsonNode login = json.readTree(mvc.perform(post("/api/common/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"requester\",\"password\":\"DevOnly123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String token = login.path("data").path("accessToken").asText();
        JsonNode request = json.readTree(mvc.perform(post("/api/app/service-requests")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":2001,\"formData\":{\"leaveType\":\"事假\",\"startDate\":\"2026-11-10\",\"endDate\":\"2026-11-11\",\"reason\":\"流程验证\"}}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        System.out.println("FLOW_PROBE flowType=" + request.path("flowType").asText("MISSING")
                + "; businessStatus=" + request.path("businessStatus").asText("MISSING")
                + "; maintenanceOrder=" + (request.hasNonNull("workOrder") ? "PRESENT" : "NONE"));
    }
}
