package com.educationerp.academic;

import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CampusIntegrationTest extends IntegrationTest {
    @Test
    void crud() throws Exception {
        testData.institution();
        String token = adminToken();
        String body = mockMvc.perform(post("/api/v1/academic/campuses")
                .header("Authorization", bearer(token))
                .contentType("application/json")
                .content("{\"code\":\"MAIN\",\"name\":\"Main Campus\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("MAIN"))
                .andReturn().getResponse().getContentAsString();
        var id = objectMapper.readTree(body).path("data").path("id").asText();
        mockMvc.perform(put("/api/v1/academic/campuses/"+id)
                .header("Authorization", bearer(token))
                .contentType("application/json")
                .content("{\"code\":\"MAIN\",\"name\":\"Main Campus Updated\",\"active\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/academic/campuses").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/academic/campuses/"+id).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }
}
