package com.educationerp.portal;

import com.educationerp.support.CourseOfferingFixture;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sweep checks across modules for the two access shapes every screen depends on: a
 * student portal call resolves to the signed-in student only, and student record reads
 * stay behind the staff read permission.
 */
class PortalScopeSweepTest extends IntegrationTest {

    @Autowired
    private CourseOfferingFixture fixture;

    private String createStudentUser(String username, UUID studentId) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/users")
                        .header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"StudPass123","displayName":"Student",
                                 "role":"STUDENT","studentId":"%s"}
                                """.formatted(username, studentId)))
                .andExpect(status().isCreated());
        return loginToken(username, "StudPass123");
    }

    @Test
    @DisplayName("a student sees only their own portal data and never another student's record")
    void studentIsConfinedToTheirOwnProfile() throws Exception {
        UUID s1 = fixture.createStudent("sw.a@example.edu");
        UUID s2 = fixture.createStudent("sw.b@example.edu");
        String token1 = createStudentUser("swa", s1);
        createStudentUser("swb", s2);

        mockMvc.perform(get("/api/v1/portal/student/profile").header("Authorization", bearer(token1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(s1.toString()));

        // A student may read their own record, but never another student's.
        mockMvc.perform(get("/api/v1/students/{id}", s1).header("Authorization", bearer(token1)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/students/{id}", s2).header("Authorization", bearer(token1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("staff with the student read permission may read any student record")
    void staffReadsAnyStudentRecord() throws Exception {
        UUID s1 = fixture.createStudent("sw.c@example.edu");
        UUID s2 = fixture.createStudent("sw.d@example.edu");

        mockMvc.perform(get("/api/v1/students/{id}", s2).header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(s2.toString()));

        mockMvc.perform(get("/api/v1/students/{id}", s1).header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk());
    }
}