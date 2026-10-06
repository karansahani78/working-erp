package com.educationerp.academic;

import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourseIntegrationTest extends IntegrationTest {
    @Test
    void crud() throws Exception {
        testData.institution();
        String token = adminToken();
        mockMvc.perform(post("/api/v1/academic/courses")
                .header("Authorization", bearer(token))
                .contentType("application/json")
                .content("{\"code\":\"MATH101\",\"name\":\"Mathematics\",\"courseType\":\"SUBJECT\",\"active\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/academic/courses").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }
}
