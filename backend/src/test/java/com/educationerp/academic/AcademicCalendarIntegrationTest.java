package com.educationerp.academic;

import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AcademicCalendarIntegrationTest extends IntegrationTest {
    @Test
    void crud() throws Exception {
        testData.institution();
        String token = adminToken();
        var year = testData.academicYear();
        mockMvc.perform(post("/api/v1/academic/calendar")
                .header("Authorization", bearer(token))
                .contentType("application/json")
                .content("{\"title\":\"Start\",\"eventType\":\"ACADEMIC_START\",\"academicYearId\":\""+year.getId()+"\",\"startDate\":\"2023-04-01\",\"endDate\":\"2023-04-01\",\"workingDay\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/academic/calendar").param("yearId",year.getId().toString())
                .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }
}
