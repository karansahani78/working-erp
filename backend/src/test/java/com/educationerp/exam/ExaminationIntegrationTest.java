package com.educationerp.exam;

import com.educationerp.common.error.ErrorCode;
import com.educationerp.support.CourseOfferingFixture;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Grading configuration, mark entry, the result workflow, published-result immutability
 * and report card / transcript generation.
 */
class ExaminationIntegrationTest extends IntegrationTest {

    @Autowired
    private CourseOfferingFixture fixture;

    @Autowired
    private ResultRepository results;

    @Autowired
    private ResultCorrectionRepository corrections;

    @Autowired
    private GradingScaleRepository scales;

    private String token;
    private UUID scaleId;

    @BeforeEach
    void seed() throws Exception {
        testData.institution();
        token = adminToken();
        scaleId = createScale();
    }

    /** A conventional 4.0-scale, created through the API so validation is exercised. */
    private UUID createScale() throws Exception {
        String body = mockMvc.perform(post("/api/v1/exams/grading-scales")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Standard 4.0",
                                "code", "STD-4",
                                "boundaries", List.of(
                                        boundary("A", "4.0", "80", "100", true),
                                        boundary("B", "3.0", "60", "79.99", true),
                                        boundary("C", "2.0", "40", "59.99", true),
                                        boundary("F", "0.0", "0", "39.99", false))))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private Map<String, Object> boundary(String letter, String points, String min, String max, boolean pass) {
        return Map.of("letterGrade", letter, "gradePoint", points,
                "minPercentage", min, "maxPercentage", max, "pass", pass);
    }

    private UUID createExam() throws Exception {
        String body = mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Term 1",
                                "code", "T1-2024",
                                "gradingScaleId", scaleId,
                                "startDate", "2024-02-01",
                                "endDate", "2024-02-10"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private UUID scheduleSubject(UUID examId) throws Exception {
        String body = mockMvc.perform(put("/api/v1/exams/{id}/schedule", examId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("subjects", List.of(Map.of(
                                "subjectName", "Mathematics",
                                "subjectCode", "MATH",
                                "examDate", "2024-02-05",
                                "startTime", "09:00:00",
                                "endTime", "11:00:00",
                                "maxMarks", 100,
                                "passMarks", 40))))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").get(0).path("id").asText());
    }

    private UUID enterMark(UUID subjectId, UUID studentId, int marks) throws Exception {
        String body = mockMvc.perform(post("/api/v1/exams/subjects/{id}/marks", subjectId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "marks", List.of(Map.of("studentId", studentId, "marksObtained", marks))))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").get(0).path("id").asText());
    }

    private void moveResult(UUID resultId, String to) throws Exception {
        mockMvc.perform(put("/api/v1/exams/results/{id}/status", resultId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("status", to))))
                .andExpect(status().isOk());
    }

    private Result result(UUID id) {
        return results.findById(id).orElseThrow();
    }

    // ---------- Grading ----------

    @Test
    void gradingScaleRejectsGapsInTheBoundaries() throws Exception {
        mockMvc.perform(post("/api/v1/exams/grading-scales")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Broken",
                                "code", "BAD",
                                "boundaries", List.of(
                                        boundary("A", "4.0", "80", "100", true),
                                        boundary("C", "2.0", "40", "59.99", true))))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.name()));
    }

    @Test
    void gradingScaleCodeIsUnique() throws Exception {
        mockMvc.perform(post("/api/v1/exams/grading-scales")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Duplicate",
                                "code", "std-4",
                                "boundaries", List.of(boundary("P", "1.0", "0", "100", true))))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_RESOURCE.name()));
    }

    @Test
    void gradesComeFromTheConfiguredScaleNotHardcodedValues() throws Exception {
        UUID exam = createExam();
        UUID subject = scheduleSubject(exam);
        UUID student = fixture.createStudent("gradeA@example.edu");

        UUID resultId = enterMark(subject, student, 85);
        assertThat(result(resultId).getPercentage()).isEqualByComparingTo("85.00");
        assertThat(result(resultId).getLetterGrade()).isEqualTo("A");
        assertThat(result(resultId).getGradePoint()).isEqualByComparingTo("4.00");
        assertThat(result(resultId).getPass()).isTrue();

        UUID failing = enterMark(subject, fixture.createStudent("gradeF@example.edu"), 20);
        assertThat(result(failing).getLetterGrade()).isEqualTo("F");
        assertThat(result(failing).getPass()).isFalse();
    }

    @Test
    void marksAboveTheSubjectMaximumAreRejected() throws Exception {
        UUID exam = createExam();
        UUID subject = scheduleSubject(exam);
        UUID student = fixture.createStudent("over@example.edu");

        mockMvc.perform(post("/api/v1/exams/subjects/{id}/marks", subject)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "marks", List.of(Map.of("studentId", student, "marksObtained", 140))))))
                .andExpect(status().is4xxClientError());
    }

    // ---------- Result workflow ----------

    @Test
    void resultFollowsTheWorkflowAndCannotSkipStates() throws Exception {
        UUID exam = createExam();
        UUID subject = scheduleSubject(exam);
        UUID student = fixture.createStudent("flow@example.edu");
        UUID resultId = enterMark(subject, student, 75);

        // Straight to PUBLISHED is not allowed.
        mockMvc.perform(put("/api/v1/exams/results/{id}/status", resultId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("status", "PUBLISHED"))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_STATE_TRANSITION.name()));

        moveResult(resultId, "VERIFIED");
        moveResult(resultId, "APPROVED");
        moveResult(resultId, "PUBLISHED");

        assertThat(result(resultId).getStatus()).isEqualTo(Result.ResultStatus.PUBLISHED);
        assertThat(result(resultId).getPublishedAt()).isNotNull();
    }

    @Test
    void publishedResultsCannotBeModifiedSilently() throws Exception {
        UUID exam = createExam();
        UUID subject = scheduleSubject(exam);
        UUID student = fixture.createStudent("lockedresult@example.edu");
        UUID resultId = enterMark(subject, student, 55);
        moveResult(resultId, "VERIFIED");
        moveResult(resultId, "APPROVED");
        moveResult(resultId, "PUBLISHED");

        // Re-entering marks on a published result is refused.
        mockMvc.perform(post("/api/v1/exams/subjects/{id}/marks", subject)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "marks", List.of(Map.of("studentId", student, "marksObtained", 95))))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESULT_LOCKED.name()));

        // And the published value is untouched.
        assertThat(result(resultId).getMarksObtained()).isEqualByComparingTo("55.00");
    }

    @Test
    void publishedResultCorrectionRecordsOldAndNewValuesAndRecalculatesTheGrade() throws Exception {
        UUID exam = createExam();
        UUID subject = scheduleSubject(exam);
        UUID student = fixture.createStudent("correctresult@example.edu");
        UUID resultId = enterMark(subject, student, 55);
        moveResult(resultId, "VERIFIED");
        moveResult(resultId, "APPROVED");
        moveResult(resultId, "PUBLISHED");

        String body = mockMvc.perform(post("/api/v1/exams/results/{id}/corrections", resultId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newMarks", 92, "reason", "Re-graded after moderation"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String correctionId = objectMapper.readTree(body).path("data").path("id").asText();
        assertThat(objectMapper.readTree(body).path("data").path("oldMarks").decimalValue())
                .isEqualByComparingTo("55.00");
        assertThat(objectMapper.readTree(body).path("data").path("oldGrade").asText()).isEqualTo("C");

        // Still published with the old mark until the correction is approved.
        assertThat(result(resultId).getMarksObtained()).isEqualByComparingTo("55.00");

        mockMvc.perform(put("/api/v1/exams/corrections/{id}/decision", correctionId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("approved", true))))
                .andExpect(status().isOk());

        assertThat(result(resultId).getMarksObtained()).isEqualByComparingTo("92.00");
        assertThat(result(resultId).getLetterGrade()).isEqualTo("A");
        assertThat(result(resultId).getStatus()).isEqualTo(Result.ResultStatus.PUBLISHED);
        assertThat(corrections.findById(UUID.fromString(correctionId))).get()
                .extracting(ResultCorrection::getStatus)
                .isEqualTo(ResultCorrection.Status.APPLIED);
    }

    @Test
    void correctionRequiresAReason() throws Exception {
        UUID exam = createExam();
        UUID subject = scheduleSubject(exam);
        UUID student = fixture.createStudent("reason@example.edu");
        UUID resultId = enterMark(subject, student, 50);
        moveResult(resultId, "VERIFIED");
        moveResult(resultId, "APPROVED");
        moveResult(resultId, "PUBLISHED");

        mockMvc.perform(post("/api/v1/exams/results/{id}/corrections", resultId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("newMarks", 70))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void correctionIsRejectedForAResultThatIsNotYetPublished() throws Exception {
        UUID exam = createExam();
        UUID subject = scheduleSubject(exam);
        UUID student = fixture.createStudent("early@example.edu");
        UUID resultId = enterMark(subject, student, 50);

        mockMvc.perform(post("/api/v1/exams/results/{id}/corrections", resultId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newMarks", 70, "reason", "Typo"))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(ErrorCode.BUSINESS_RULE_VIOLATION.name()));
    }

    // ---------- Examination lifecycle ----------

    @Test
    void examinationStatusFollowsItsWorkflow() throws Exception {
        UUID exam = createExam();
        scheduleSubject(exam);

        // Cannot jump from PLANNED to PUBLISHED.
        mockMvc.perform(put("/api/v1/exams/{id}/status", exam)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("status", "PUBLISHED"))))
                .andExpect(status().is4xxClientError());

        for (String step : List.of("SCHEDULED", "IN_PROGRESS", "MARKS_ENTERED")) {
            mockMvc.perform(put("/api/v1/exams/{id}/status", exam)
                            .header("Authorization", bearer(token))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(Map.of("status", step))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void overlappingExamTimesAreRejected() throws Exception {
        UUID exam = createExam();
        mockMvc.perform(put("/api/v1/exams/{id}/schedule", exam)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("subjects", List.of(
                                Map.of("subjectName", "Maths", "examDate", "2024-02-05",
                                        "startTime", "09:00:00", "endTime", "11:00:00", "maxMarks", 100),
                                Map.of("subjectName", "Physics", "examDate", "2024-02-05",
                                        "startTime", "10:30:00", "endTime", "12:30:00", "maxMarks", 100))))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void subjectScheduledOutsideTheExamWindowIsRejected() throws Exception {
        UUID exam = createExam();
        mockMvc.perform(put("/api/v1/exams/{id}/schedule", exam)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("subjects", List.of(
                                Map.of("subjectName", "Maths", "examDate", "2024-05-05",
                                        "startTime", "09:00:00", "endTime", "11:00:00", "maxMarks", 100))))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void duplicateExaminationCodeIsRejected() throws Exception {
        createExam();
        mockMvc.perform(post("/api/v1/exams")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Term 1 copy", "code", "T1-2024"))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_RESOURCE.name()));
    }

    // ---------- Report cards ----------

    @Test
    void reportCardIsGeneratedFromApprovedResultsAndCannotBeRegeneratedAfterPublication() throws Exception {
        UUID exam = createExam();
        UUID subject = scheduleSubject(exam);
        UUID student = fixture.createStudent("card@example.edu");
        UUID resultId = enterMark(subject, student, 88);
        moveResult(resultId, "VERIFIED");
        moveResult(resultId, "APPROVED");

        String body = mockMvc.perform(post("/api/v1/exams/report-cards/generate")
                        .header("Authorization", bearer(token))
                        .param("studentId", student.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallResult").value("PASS"))
                .andExpect(jsonPath("$.data.items[0].subjectName").value("Mathematics"))
                .andReturn().getResponse().getContentAsString();
        String cardId = objectMapper.readTree(body).path("data").path("id").asText();

        mockMvc.perform(put("/api/v1/exams/report-cards/{id}/status", cardId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("status", "PUBLISHED"))))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(put("/api/v1/exams/report-cards/{id}/status", cardId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/exams/report-cards/{id}/status", cardId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("status", "PUBLISHED"))))
                .andExpect(status().isOk());

        // Regenerating a published card is refused.
        mockMvc.perform(post("/api/v1/exams/report-cards/generate")
                        .header("Authorization", bearer(token))
                        .param("studentId", student.toString()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void reportCardRequiresApprovedResults() throws Exception {
        UUID exam = createExam();
        UUID subject = scheduleSubject(exam);
        UUID student = fixture.createStudent("nocards@example.edu");
        enterMark(subject, student, 60);

        mockMvc.perform(post("/api/v1/exams/report-cards/generate")
                        .header("Authorization", bearer(token))
                        .param("studentId", student.toString()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void transcriptAggregatesCumulativeGpaAcrossReportCards() throws Exception {
        UUID exam = createExam();
        UUID subject = scheduleSubject(exam);
        UUID student = fixture.createStudent("transcript@example.edu");
        UUID resultId = enterMark(subject, student, 88);
        moveResult(resultId, "VERIFIED");
        moveResult(resultId, "APPROVED");
        mockMvc.perform(post("/api/v1/exams/report-cards/generate")
                        .header("Authorization", bearer(token))
                        .param("studentId", student.toString()))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/api/v1/exams/transcripts/generate")
                        .header("Authorization", bearer(token))
                        .param("studentId", student.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).path("data").path("cumulativeGpa").decimalValue())
                .isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(objectMapper.readTree(body).path("data").path("reportCards")).hasSize(1);
    }

    @Test
    void transcriptForAStudentWithNoCardsIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/exams/transcripts/generate")
                        .header("Authorization", bearer(token))
                        .param("studentId", fixture.createStudent("empty@example.edu").toString()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void resultsAreVisiblePerStudent() throws Exception {
        UUID exam = createExam();
        UUID subject = scheduleSubject(exam);
        UUID student = fixture.createStudent("lookup@example.edu");
        enterMark(subject, student, 72);

        mockMvc.perform(get("/api/v1/exams/results/students/{id}", student)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].letterGrade").value("B"));
    }

    @Test
    void unknownResultStatusIsRejected() throws Exception {
        UUID exam = createExam();
        UUID subject = scheduleSubject(exam);
        UUID student = fixture.createStudent("badstatus@example.edu");
        UUID resultId = enterMark(subject, student, 72);

        mockMvc.perform(put("/api/v1/exams/results/{id}/status", resultId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("status", "BANANA"))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.name()));
    }

    @Test
    void student360ExposesTheResultAndReportCardTabs() throws Exception {
        UUID exam = createExam();
        UUID subject = scheduleSubject(exam);
        UUID student = fixture.createStudent("aggregate@example.edu");
        UUID resultId = enterMark(subject, student, 88);
        moveResult(resultId, "VERIFIED");
        moveResult(resultId, "APPROVED");
        mockMvc.perform(post("/api/v1/exams/report-cards/generate")
                        .header("Authorization", bearer(token))
                        .param("studentId", student.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/students/{id}/360", student)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results.length()").value(1))
                .andExpect(jsonPath("$.data.results[0].subjectName").value("Mathematics"))
                .andExpect(jsonPath("$.data.results[0].examinationName").value("Term 1"))
                .andExpect(jsonPath("$.data.results[0].letterGrade").value("A"))
                .andExpect(jsonPath("$.data.reportCards.length()").value(1))
                // No attendance recorded, so the tab is empty rather than a misleading zero.
                .andExpect(jsonPath("$.data.attendance.length()").value(0));
    }
}