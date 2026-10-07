package com.educationerp.exam;

import com.educationerp.auth.role.Role;
import com.educationerp.auth.security.AuthenticatedUserLoader;
import com.educationerp.auth.user.RoleDefinition;
import com.educationerp.auth.user.RoleDefinitionRepository;
import com.educationerp.support.CourseOfferingFixture;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Object-scope enforcement on marks and results: an examiner may only enter and change
 * marks for the class linked to their subject, and a marks entry may only name students
 * who are actually in that class.
 */
class ExamScopeIntegrationTest extends IntegrationTest {

    @Autowired
    private CourseOfferingFixture fixture;

    @Autowired
    private ResultRepository results;

    @Autowired
    private RoleDefinitionRepository roles;

    @Autowired
    private AuthenticatedUserLoader loader;

    private String admin;
    private String teacher;
    private String controller;
    private UUID scaleId;
    private UUID myOffering;
    private UUID otherOffering;
    private UUID examA;
    private UUID examB;
    private UUID examC;
    private UUID mySubject;
    private UUID otherSubject;
    private UUID unlinkedSubject;
    private UUID myStudent;
    private UUID foreign;

    @BeforeEach
    void seed() throws Exception {
        testData.institution();
        admin = adminToken();
        testData.user("exam.controller", "Exam Controller", "exam.controller@example.edu",
                Role.EXAM_CONTROLLER, "ExamCtrl123");
        controller = loginToken("exam.controller", "ExamCtrl123");

        myOffering = fixture.createOffering(fixture.createTeacher("exam.teacher", "ExamTeacher1"));
        otherOffering = fixture.createOffering(fixture.createTeacher("exam.teachtwo", "ExamTeacher2"));
        teacher = loginToken("exam.teacher", "ExamTeacher1");
        myStudent = fixture.createStudent("exam.enrolled@example.edu");
        fixture.enroll(myStudent, myOffering);
        foreign = fixture.createStudent("exam.foreign@example.edu");

        scaleId = createScale();
        examA = createExam(scaleId, "EXM-A");
        examB = createExam(scaleId, "EXM-B");
        examC = createExam(scaleId, "EXM-C");
        mySubject = scheduleSubject(examA, myOffering);
        otherSubject = scheduleSubject(examB, otherOffering);
        unlinkedSubject = scheduleSubject(examC, null);
    }

    private UUID createScale() throws Exception {
        String body = mockMvc.perform(post("/api/v1/exams/grading-scales")
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Scope 4.0", "code", "SCP-4",
                                "boundaries", List.of(
                                        boundary("A", "4.0", "80", "100", true),
                                        boundary("F", "0.0", "0", "79.99", false))))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private Map<String, Object> boundary(String letter, String points, String min, String max, boolean pass) {
        return Map.of("letterGrade", letter, "gradePoint", points,
                "minPercentage", min, "maxPercentage", max, "pass", pass);
    }

    private UUID createExam(UUID gradingScaleId, String code) throws Exception {
        String body = mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Term " + code, "code", code,
                                "gradingScaleId", gradingScaleId,
                                "startDate", "2024-02-01", "endDate", "2024-02-10"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private UUID scheduleSubject(UUID examId, UUID offeringId) throws Exception {
        Map<String, Object> subject = new java.util.LinkedHashMap<>();
        subject.put("subjectName", "Mathematics");
        subject.put("subjectCode", "MATH-" + (offeringId == null ? "X" : offeringId.toString().substring(0, 4)));
        subject.put("examDate", "2024-02-05");
        subject.put("startTime", "09:00:00");
        subject.put("endTime", "11:00:00");
        subject.put("maxMarks", 100);
        subject.put("passMarks", 40);
        if (offeringId != null) {
            subject.put("courseOfferingId", offeringId.toString());
        }
        String body = mockMvc.perform(put("/api/v1/exams/{id}/schedule", examId)
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("subjects", List.of(subject)))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").get(0).path("id").asText());
    }

    private UUID enter(String token, UUID subjectId, UUID studentId, int marks) throws Exception {
        String body = mockMvc.perform(post("/api/v1/exams/subjects/{id}/marks", subjectId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "marks", List.of(Map.of("studentId", studentId, "marksObtained", marks))))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").get(0).path("id").asText());
    }

    @Test
    void anExaminerCanEnterMarksOnlyForTheirOwnClass() throws Exception {
        enter(teacher, mySubject, myStudent, 85);

        mockMvc.perform(post("/api/v1/exams/subjects/{id}/marks", otherSubject)
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "marks", List.of(Map.of("studentId", myStudent, "marksObtained", 85))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(results.findByExaminationIdOrderByStudentIdAsc(examB)).isEmpty();
    }

    @Test
    void aTeacherCannotEnterMarksForASubjectThatIsNotTiedToAClass() throws Exception {
        mockMvc.perform(post("/api/v1/exams/subjects/{id}/marks", unlinkedSubject)
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "marks", List.of(Map.of("studentId", myStudent, "marksObtained", 85))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // Exam leadership can still key an unlinked subject's marks.
        enter(admin, unlinkedSubject, myStudent, 85);
        assertThat(results.findByExaminationIdOrderByStudentIdAsc(examC)).hasSize(1);
    }

    @Test
    void aTeacherCannotEnterMarksForAStudentOutsideTheirClass() throws Exception {
        mockMvc.perform(post("/api/v1/exams/subjects/{id}/marks", mySubject)
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "marks", List.of(Map.of("studentId", foreign, "marksObtained", 85))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(results.findByExaminationIdOrderByStudentIdAsc(examA)).isEmpty();
    }

    @Test
    void aMixedEntryIsRejectedAndNothingIsPersisted() throws Exception {
        mockMvc.perform(post("/api/v1/exams/subjects/{id}/marks", mySubject)
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "marks", List.of(
                                        Map.of("studentId", myStudent, "marksObtained", 85),
                                        Map.of("studentId", foreign, "marksObtained", 10))))))
                .andExpect(status().isForbidden());

        assertThat(results.findByExaminationIdOrderByStudentIdAsc(examA)).isEmpty();
    }

    @Test
    void anExamControllerCanVerifyApproveAndPublishAnyClass() throws Exception {
        // Admin keys the marks (controllers hold VERIFY/APPROVE/PUBLISH, not MARKS_ENTER).
        UUID resultId = enter(admin, otherSubject, myStudent, 90);

        for (String step : List.of("VERIFIED", "APPROVED", "PUBLISHED")) {
            mockMvc.perform(put("/api/v1/exams/results/{id}/status", resultId)
                            .header("Authorization", bearer(controller))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(Map.of("status", step))))
                    .andExpect(status().isOk());
        }

        assertThat(results.findById(resultId)).get()
                .extracting(Result::getStatus).isEqualTo(Result.ResultStatus.PUBLISHED);
    }

    @Test
    void aCorrectionRequestIsScopedToTheTeachersOwnClass() throws Exception {
        // A school may grant its teachers the right to request mark corrections; even
        // then they are limited to the class they teach.
        RoleDefinition teacherRole = roles.findByCode(Role.TEACHER).orElseThrow();
        java.util.Set<String> permissions = new java.util.HashSet<>(teacherRole.getPermissions());
        String corrected = com.educationerp.auth.permission.Permission.RESULT_CORRECT.name();
        if (permissions.add(corrected)) {
            teacherRole.setPermissions(permissions);
            roles.save(teacherRole);
        }
        // The teacher's principal is cached; the token is long-lived, so force a reload.
        loader.invalidateAll();

        // Publish a result in the other teacher's class, and one in the teacher's own.
        UUID foreignResult = enter(admin, otherSubject, myStudent, 60);
        publish(admin, foreignResult);
        UUID ownResult = enter(admin, mySubject, myStudent, 60);
        publish(admin, ownResult);

        // The teacher may correct a result for their own subject.
        mockMvc.perform(post("/api/v1/exams/results/{id}/corrections", ownResult)
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newMarks", 92, "reason", "Re-graded after moderation"))))
                .andExpect(status().isOk());

        // But not a result belonging to another teacher's subject.
        mockMvc.perform(post("/api/v1/exams/results/{id}/corrections", foreignResult)
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newMarks", 92, "reason", "Reaching across classes"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private void publish(String token, UUID resultId) throws Exception {
        for (String step : List.of("MARKS_ENTERED", "VERIFIED", "APPROVED", "PUBLISHED")) {
            mockMvc.perform(put("/api/v1/exams/results/{id}/status", resultId)
                            .header("Authorization", bearer(token))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(Map.of("status", step))))
                    .andExpect(status().isOk());
        }
    }
}