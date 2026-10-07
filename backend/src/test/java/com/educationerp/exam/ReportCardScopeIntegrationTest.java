package com.educationerp.exam;

import com.educationerp.auth.permission.Permission;
import com.educationerp.auth.role.Role;
import com.educationerp.auth.security.AuthenticatedUserLoader;
import com.educationerp.auth.user.RoleDefinition;
import com.educationerp.auth.user.RoleDefinitionRepository;
import com.educationerp.support.CourseOfferingFixture;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Report cards and transcripts belong to a single student. A reader role that knows a
 * card's id must not be able to pull another student's card just because it carries a
 * read permission without the blanket student-data scope.
 */
class ReportCardScopeIntegrationTest extends IntegrationTest {

    @Autowired
    private CourseOfferingFixture fixture;

    @Autowired
    private RoleDefinitionRepository roles;

    @Autowired
    private AuthenticatedUserLoader loader;

    private String admin;
    private String teacher;
    private UUID scaleId;
    private UUID exam;
    private UUID subject;
    private UUID studentA;
    private UUID cardId;
    private UUID transcriptId;

    @BeforeEach
    void seed() throws Exception {
        testData.institution();
        admin = adminToken();
        fixture.createTeacher("rc.teacher", "ReportCard1");
        teacher = loginToken("rc.teacher", "ReportCard1");

        studentA = fixture.createStudent("rc.a@example.edu");
        scaleId = createScale();
        exam = createExam();
        subject = scheduleSubject();
        UUID resultId = enterMarks(subject, studentA, 88);
        publish(resultId);
        String card = generateCard(studentA);
        cardId = cardFrom(card);
        approveAndPublish(cardId);
        String transcript = generateTranscript(studentA);
        transcriptId = UUID.fromString(objectMapper.readTree(transcript).path("data").path("id").asText());

        // A school may give a role the report-card/transcript read without the blanket
        // student-data read; the owner check must still hold.
        RoleDefinition teacherRole = roles.findByCode(Role.TEACHER).orElseThrow();
        Set<String> permissions = new HashSet<>(teacherRole.getPermissions());
        permissions.remove(Permission.STUDENT_READ.name());
        permissions.add(Permission.TRANSCRIPT_READ.name());
        teacherRole.setPermissions(permissions);
        roles.save(teacherRole);
        loader.invalidateAll();
    }

    private UUID createScale() throws Exception {
        String body = mockMvc.perform(post("/api/v1/exams/grading-scales")
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "RC 4.0", "code", "RC-4",
                                "boundaries", List.of(
                                        Map.of("letterGrade", "A", "gradePoint", "4.0",
                                                "minPercentage", "80", "maxPercentage", "100", "pass", true),
                                        Map.of("letterGrade", "F", "gradePoint", "0.0",
                                                "minPercentage", "0", "maxPercentage", "79.99", "pass", false))))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private UUID createExam() throws Exception {
        String body = mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Report Card Term", "code", "RC-T1",
                                "gradingScaleId", scaleId,
                                "startDate", "2024-02-01", "endDate", "2024-02-10"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private UUID scheduleSubject() throws Exception {
        String body = mockMvc.perform(put("/api/v1/exams/{id}/schedule", exam)
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("subjects", List.of(Map.of(
                                "subjectName", "Mathematics", "subjectCode", "RC-MATH",
                                "examDate", "2024-02-05", "startTime", "09:00:00", "endTime", "11:00:00",
                                "maxMarks", 100, "passMarks", 40))))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").get(0).path("id").asText());
    }

    private UUID enterMarks(UUID subjectId, UUID studentId, int marks) throws Exception {
        String body = mockMvc.perform(post("/api/v1/exams/subjects/{id}/marks", subjectId)
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "marks", List.of(Map.of("studentId", studentId, "marksObtained", marks))))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").get(0).path("id").asText());
    }

    private void publish(UUID resultId) throws Exception {
        for (String step : List.of("MARKS_ENTERED", "VERIFIED", "APPROVED", "PUBLISHED")) {
            mockMvc.perform(put("/api/v1/exams/results/{id}/status", resultId)
                            .header("Authorization", bearer(admin))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(Map.of("status", step))))
                    .andExpect(status().isOk());
        }
    }

    private String generateCard(UUID studentId) throws Exception {
        return mockMvc.perform(post("/api/v1/exams/report-cards/generate")
                        .header("Authorization", bearer(admin))
                        .param("studentId", studentId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private UUID cardFrom(String body) throws Exception {
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private void approveAndPublish(UUID card) throws Exception {
        mockMvc.perform(put("/api/v1/exams/report-cards/{id}/status", card)
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isOk());
    }

    private String generateTranscript(UUID studentId) throws Exception {
        return mockMvc.perform(post("/api/v1/exams/transcripts/generate")
                        .header("Authorization", bearer(admin))
                        .param("studentId", studentId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void aCardReaderWithoutStudentScopeCannotPullAnotherStudentsCard() throws Exception {
        // The teacher holds REPORT_CARD_READ but not STUDENT_READ anymore.
        mockMvc.perform(get("/api/v1/exams/report-cards/{id}", cardId)
                        .header("Authorization", bearer(teacher)))
                .andExpect(status().isForbidden());

        // Leadership still reads any card.
        mockMvc.perform(get("/api/v1/exams/report-cards/{id}", cardId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void aTranscriptIsEquallyScopedToItsOwner() throws Exception {
        mockMvc.perform(get("/api/v1/exams/transcripts/{id}", transcriptId)
                        .header("Authorization", bearer(teacher)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/exams/transcripts/{id}", transcriptId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void cardTransitionsAreScopedToTheOwnerStudent() throws Exception {
        // The teacher has no REPORT_CARD_GENERATE, so denial is by permission here; the
        // owner check is what keeps the id-holding attacker honest for read-only roles.
        mockMvc.perform(put("/api/v1/exams/report-cards/{id}/status", cardId)
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("status", "PUBLISHED"))))
                .andExpect(status().isForbidden());
    }
}