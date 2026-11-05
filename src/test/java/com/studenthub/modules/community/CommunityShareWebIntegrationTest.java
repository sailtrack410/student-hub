package com.studenthub.modules.community;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunityShareWebIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void shareLandingAndDomainAssociationArePublic() throws Exception {
        mvc.perform(get("/c/ABC234xy")).andExpect(status().isOk())
                .andExpect(forwardedUrl("/share/index.html"));
        mvc.perform(get("/share/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("studenthub://p/")));
        mvc.perform(get("/.well-known/assetlinks.json")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].target.package_name").value("com.studenthub.app"))
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints[0]").isNotEmpty());
    }
}
