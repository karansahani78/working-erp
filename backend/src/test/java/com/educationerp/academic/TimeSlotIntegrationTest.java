package com.educationerp.academic;

import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TimeSlotIntegrationTest extends IntegrationTest {
    @Test
    void crud() throws Exception {
        testData.institution();
        String token = adminToken();
        mockMvc.perform(post("/api/v1/academic/time-slots")
                .header("Authorization", bearer(token))
                .contentType("application/json")
                .content("{\"name\":\"Period 1\",\"startTime\":\"09:00\",\"endTime\":\"09:45\",\"slotType\":\"LECTURE\",\"ordinal\":1,\"active\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/academic/time-slots").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }
}
