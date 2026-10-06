package com.educationerp.academic;

import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AcademicYearIntegrationTest extends IntegrationTest {
    @Test
    void crud() throws Exception {
        testData.institution();
        String token = adminToken();
        mockMvc.perform(post("/api/v1/academic/academic-years")
                .header("Authorization", bearer(token))
                .contentType("application/json")
                .content("{\"name\":\"2081/82\",\"code\":\"AY208182\",\"startDate\":\"2024-04-01\",\"endDate\":\"2025-03-31\",\"calendar\":\"AD\",\"status\":\"PLANNED\",\"current\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/academic/academic-years").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void anUnknownCalendarIsAValidationRatherThanAServerError() throws Exception {
        testData.institution();
        String token = adminToken();
        // Enum names in the body were read straight off the caller's text, so a value the
        // system does not know — "bs", "year", a stray space — escaped as an unhandled
        // IllegalArgumentException and came back as a 500 with a stack trace in the log.
        mockMvc.perform(post("/api/v1/academic/academic-years")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"name\":\"2080/81\",\"code\":\"AY208081\","
                                + "\"startDate\":\"2023-04-01\",\"endDate\":\"2024-03-31\","
                                + "\"calendar\":\"saka\",\"status\":\"planned\",\"current\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.calendar").isNotEmpty());

        // The same field, spelled correctly and in a case nobody would type, still works.
        mockMvc.perform(post("/api/v1/academic/academic-years")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"name\":\"2081/82\",\"code\":\"AY208182\","
                                + "\"startDate\":\"2024-04-01\",\"endDate\":\"2025-03-31\","
                                + "\"calendar\":\"ad\",\"status\":\"PLANNED\",\"current\":false}"))
                .andExpect(status().isOk());
    }
}
