package com.educationerp.auth;

import com.educationerp.auth.role.Role;
import com.educationerp.auth.user.RoleDefinition;
import com.educationerp.auth.user.RoleDefinitionRepository;
import com.educationerp.support.CourseOfferingFixture;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The principal resolver caches a resolved account for a short window. Saving a role's
 * permissions through the admin API must evict that cache, so a membership change is
 * felt by a logged-in holder immediately rather than after the cache expires.
 */
class PrincipalCacheInvalidationTest extends IntegrationTest {

    @Autowired
    private RoleDefinitionRepository roles;

    @Autowired
    private CourseOfferingFixture fixture;

    private static final String SCHEDULE_BODY = """
            {"subjects":[{"subjectName":"Mathematics","subjectCode":"M101","examDate":"2024-02-05",
              "startTime":"09:00:00","endTime":"11:00:00","maxMarks":100,"passMarks":40}]}
            """;

    private void savePermissions(String admin, String roleId, Set<String> permissions) throws Exception {
        mockMvc.perform(put("/api/v1/admin/roles/{id}/permissions", roleId)
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", "Teacher", "permissions", permissions))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(permissions.size()));
    }

    @Test
    @DisplayName("granting and revoking a permission takes effect immediately for a logged-in holder")
    void permissionChangesAreFeltByLiveSessions() throws Exception {
        String admin = adminToken();
        fixture.createTeacher("gradet", "GradeT123");
        String teacher = loginToken("gradet", "GradeT123");
        String fakeExam = UUID.randomUUID().toString();

        // TEACHER has no schedule rights, so the live token is refused before any lookup.
        mockMvc.perform(put("/api/v1/exams/{id}/schedule", fakeExam)
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content(SCHEDULE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        RoleDefinition teacherRole = roles.findByCode(Role.TEACHER).orElseThrow();
        Set<String> granted = new LinkedHashSet<>(teacherRole.getPermissions());
        granted.add("EXAM_SCHEDULE_MANAGE");
        savePermissions(admin, teacherRole.getId().toString(), granted);

        // Same token, same request: now the permission gate opens (the 404 is the
        // fabricated exam id being looked up, which proves authorization passed).
        mockMvc.perform(put("/api/v1/exams/{id}/schedule", fakeExam)
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content(SCHEDULE_BODY))
                .andExpect(status().isNotFound());

        Set<String> revoked = new LinkedHashSet<>(granted);
        revoked.remove("EXAM_SCHEDULE_MANAGE");
        savePermissions(admin, teacherRole.getId().toString(), revoked);

        mockMvc.perform(put("/api/v1/exams/{id}/schedule", fakeExam)
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content(SCHEDULE_BODY))
                .andExpect(status().isForbidden());
    }
}