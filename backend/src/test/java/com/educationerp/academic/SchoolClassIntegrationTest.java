package com.educationerp.academic;

import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SchoolClassIntegrationTest extends IntegrationTest {
    @Test
    void crud() throws Exception {
        testData.institution();
        String token = adminToken();
        var year = testData.academicYear();
        mockMvc.perform(post("/api/v1/academic/classes")
                .header("Authorization", bearer(token))
                .contentType("application/json")
                .content("{\"academicYearId\":\""+year.getId()+"\",\"name\":\"Grade 9\",\"code\":\"G9\",\"ordinal\":9,\"active\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/academic/classes").param("yearId",year.getId().toString())
                .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }
}
