package com.educationerp.attendance;

import com.educationerp.common.error.ErrorCode;
import com.educationerp.support.CourseOfferingFixture;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.LinkedHashMap;
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
 * Attendance recording, approval, correction and the percentage report.
 *
 * <p>The blueprint's hard rule here is that a correction is audited rather than applied
 * inline, so most of these tests assert on the two-step correction path.
 */
class AttendanceIntegrationTest extends IntegrationTest {

    @Autowired
    private CourseOfferingFixture offerings;

    @Autowired
    private AttendanceRecordRepository records;

    @Autowired
    private AttendanceCorrectionRepository corrections;

    private LocalDate date;
    private String token;

    @BeforeEach
    void seed() throws Exception {
        testData.institution();
        token = adminToken();
        date = LocalDate.of(2024, 2, 5);
    }

    private String recordRegister(UUID offeringId, UUID studentId, String status) throws Exception {
        return recordRegister(offeringId, studentId, status, null, date);
    }

    private String recordRegister(UUID offeringId, UUID studentId, String status,
                                  Integer minutesLate, LocalDate on) throws Exception {
        Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("studentId", studentId);
        entry.put("status", status);
        if (minutesLate != null) {
            entry.put("minutesLate", minutesLate);
        }
        return mockMvc.perform(post("/api/v1/attendance/registers")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseOfferingId", offeringId,
                                "attendanceDate", on.toString(),
                                "entries", List.of(entry)))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void anUnknownEnumInTheRegisterIsAValidationRatherThanAServerError() throws Exception {
        UUID offering = offerings.createOffering();
        UUID student = offerings.createStudent("a.enum@example.edu");
        offerings.enroll(student, offering);

        // periodType went straight to Enum.valueOf on whatever the client sent, so "hourly"
        // reached the catch-all handler as an unhandled exception: a 500 for a typo.
        mockMvc.perform(post("/api/v1/attendance/registers")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>(Map.of(
                                "courseOfferingId", offering,
                                "attendanceDate", date.toString(),
                                "periodType", "hourly",
                                "entries", List.of(Map.of("studentId", student, "status", "PRESENT")))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.name()))
                .andExpect(jsonPath("$.fieldErrors.periodType").isNotEmpty());

        // A missing status is the same class of problem: value.trim() on null is a
        // NullPointerException, which is also a 500.
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("studentId", student);
        entry.put("status", null);
        mockMvc.perform(post("/api/v1/attendance/registers")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>(Map.of(
                                "courseOfferingId", offering,
                                "attendanceDate", date.toString(),
                                "entries", List.of(entry))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.name()));

        // And the wording still names the values that would have worked.
        mockMvc.perform(post("/api/v1/attendance/registers")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LinkedHashMap<>(Map.of(
                                "courseOfferingId", offering,
                                "attendanceDate", date.toString(),
                                "periodType", "DAILY",
                                "entries", List.of(Map.of("studentId", student, "status", "present")))))))
                .andExpect(status().isOk());
    }

    @Test
    void bulkRegisterRecordsEveryStudentInThePeriod() throws Exception {
        UUID offering = offerings.createOffering();
        UUID a = offerings.createStudent("a.att@example.edu");
        offerings.enroll(a, offering);
        UUID b = offerings.createStudent("b.att@example.edu");
        offerings.enroll(b, offering);

        recordRegister(offering, a, "PRESENT");
        String body = recordRegister(offering, b, "ABSENT");

        assertThat(records.findByCourseOfferingIdAndAttendanceDateOrderByStudentIdAsc(offering, date))
                .hasSize(2);
        // The register view is the whole day's sheet, so the second call returns both students.
        assertThat(objectMapper.readTree(body).path("data").path("entries")).hasSize(2);
    }

    @Test
    void reSubmittingTheSamePeriodUpdatesInsteadOfDuplicating() throws Exception {
        UUID offering = offerings.createOffering();
        UUID student = offerings.createStudent("retry.att@example.edu");
        offerings.enroll(student, offering);

        recordRegister(offering, student, "PRESENT");
        recordRegister(offering, student, "ABSENT");

        assertThat(records.findByCourseOfferingIdAndAttendanceDateOrderByStudentIdAsc(offering, date))
                .hasSize(1);
        assertThat(records.findByCourseOfferingIdAndAttendanceDateOrderByStudentIdAsc(offering, date).get(0)
                .getStatus()).isEqualTo(AttendanceRecord.Status.ABSENT);
    }

    @Test
    void lateRequiresMinutesLate() throws Exception {
        UUID offering = offerings.createOffering();
        UUID student = offerings.createStudent("late.att@example.edu");
        offerings.enroll(student, offering);

        mockMvc.perform(post("/api/v1/attendance/registers")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseOfferingId", offering,
                                "attendanceDate", date.toString(),
                                "entries", List.of(Map.of("studentId", student, "status", "LATE"))))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.name()));
    }

    @Test
    void periodAttendanceWithoutTimeSlotIsRejected() throws Exception {
        UUID offering = offerings.createOffering();
        UUID student = offerings.createStudent("period.att@example.edu");
        offerings.enroll(student, offering);

        mockMvc.perform(post("/api/v1/attendance/registers")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseOfferingId", offering,
                                "attendanceDate", date.toString(),
                                "periodType", "PERIOD",
                                "entries", List.of(Map.of("studentId", student, "status", "PRESENT"))))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void registerIsApprovedOnlyAfterApproval() throws Exception {
        UUID offering = offerings.createOffering();
        UUID student = offerings.createStudent("approve.att@example.edu");
        offerings.enroll(student, offering);
        recordRegister(offering, student, "PRESENT");

        assertThat(record(offering, student).getWorkflowStatus())
                .isEqualTo(AttendanceRecord.WorkflowStatus.DRAFT);

        mockMvc.perform(post("/api/v1/attendance/registers/{offering}/{date}/approve", offering, date)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        assertThat(record(offering, student).getWorkflowStatus())
                .isEqualTo(AttendanceRecord.WorkflowStatus.APPROVED);
    }

    @Test
    void approvedRegisterCannotBeOverwrittenDirectly() throws Exception {
        UUID offering = offerings.createOffering();
        UUID student = offerings.createStudent("locked.att@example.edu");
        offerings.enroll(student, offering);
        recordRegister(offering, student, "PRESENT");
        approveRegister(offering);

        mockMvc.perform(post("/api/v1/attendance/registers")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseOfferingId", offering,
                                "attendanceDate", date.toString(),
                                "entries", List.of(Map.of("studentId", student, "status", "ABSENT"))))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(ErrorCode.BUSINESS_RULE_VIOLATION.name()));

        assertThat(record(offering, student).getStatus()).isEqualTo(AttendanceRecord.Status.PRESENT);
    }

    @Test
    void approvedRegisterCorrectionRequiresApprovalBeforeItApplies() throws Exception {
        UUID offering = offerings.createOffering();
        UUID student = offerings.createStudent("correct.att@example.edu");
        offerings.enroll(student, offering);
        recordRegister(offering, student, "ABSENT");
        approveRegister(offering);
        UUID recordId = record(offering, student).getId();

        String body = mockMvc.perform(post("/api/v1/attendance/records/{id}/corrections", recordId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newStatus", "PRESENT",
                                "reason", "Medical certificate submitted"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String correctionId = objectMapper.readTree(body).path("data").path("id").asText();
        assertThat(objectMapper.readTree(body).path("data").path("oldStatus").asText()).isEqualTo("ABSENT");

        // Pending: the register still shows the original status.
        assertThat(record(offering, student).getStatus()).isEqualTo(AttendanceRecord.Status.ABSENT);
        assertThat(corrections.findById(UUID.fromString(correctionId)))
                .get().extracting(AttendanceCorrection::getStatus)
                .isEqualTo(AttendanceCorrection.Status.PENDING);

        mockMvc.perform(put("/api/v1/attendance/corrections/{id}/decision", correctionId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("approved", true, "notes", "Verified"))))
                .andExpect(status().isOk());

        assertThat(record(offering, student).getStatus()).isEqualTo(AttendanceRecord.Status.PRESENT);
        assertThat(corrections.findById(UUID.fromString(correctionId)))
                .get().extracting(AttendanceCorrection::getStatus)
                .isEqualTo(AttendanceCorrection.Status.APPLIED);
    }

    @Test
    void rejectedCorrectionLeavesTheRecordUnchanged() throws Exception {
        UUID offering = offerings.createOffering();
        UUID student = offerings.createStudent("reject.att@example.edu");
        offerings.enroll(student, offering);
        recordRegister(offering, student, "ABSENT");
        approveRegister(offering);
        UUID recordId = record(offering, student).getId();

        String body = mockMvc.perform(post("/api/v1/attendance/records/{id}/corrections", recordId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newStatus", "PRESENT", "reason", "Claimed illness"))))
                .andReturn().getResponse().getContentAsString();
        String correctionId = objectMapper.readTree(body).path("data").path("id").asText();

        mockMvc.perform(put("/api/v1/attendance/corrections/{id}/decision", correctionId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("approved", false, "notes", "No evidence"))))
                .andExpect(status().isOk());

        assertThat(record(offering, student).getStatus()).isEqualTo(AttendanceRecord.Status.ABSENT);
    }

    @Test
    void correctionRequiresAReason() throws Exception {
        UUID offering = offerings.createOffering();
        UUID student = offerings.createStudent("noreason.att@example.edu");
        offerings.enroll(student, offering);
        recordRegister(offering, student, "ABSENT");
        approveRegister(offering);
        UUID recordId = record(offering, student).getId();

        mockMvc.perform(post("/api/v1/attendance/records/{id}/corrections", recordId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("newStatus", "PRESENT"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void attendancePercentageCountsExcusedAsPresent() throws Exception {
        UUID offering = offerings.createOffering();
        UUID student = offerings.createStudent("summary.att@example.edu");
        offerings.enroll(student, offering);

        // One record per day: daily attendance is keyed on the date, not on repeated calls.
        recordRegister(offering, student, "PRESENT", null, date);
        recordRegister(offering, student, "LATE", 10, date.plusDays(1));
        recordRegister(offering, student, "EXCUSED", null, date.plusDays(2));
        recordRegister(offering, student, "ABSENT", null, date.plusDays(3));

        String body = mockMvc.perform(get("/api/v1/attendance/students/{id}/summary", student)
                        .header("Authorization", bearer(token))
                        .param("from", date.toString())
                        .param("to", date.plusDays(3).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPeriods").value(4))
                .andExpect(jsonPath("$.data.attendancePercentage").value("75.00"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("75.00");
    }

    @Test
    void correctingADraftIsRejectedInFavourOfADirectEdit() throws Exception {
        UUID offering = offerings.createOffering();
        UUID student = offerings.createStudent("draft.att@example.edu");
        offerings.enroll(student, offering);
        recordRegister(offering, student, "ABSENT");
        UUID recordId = record(offering, student).getId();

        mockMvc.perform(post("/api/v1/attendance/records/{id}/corrections", recordId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newStatus", "PRESENT", "reason", "Mistake"))))
                .andExpect(status().is4xxClientError());
    }

    private AttendanceRecord record(UUID offering, UUID student) {
        return records.findByCourseOfferingIdAndAttendanceDateOrderByStudentIdAsc(offering, date).stream()
                .filter(r -> r.getStudentId().equals(student))
                .findFirst()
                .orElseThrow();
    }

    private void approveRegister(UUID offering) throws Exception {
        mockMvc.perform(post("/api/v1/attendance/registers/{offering}/{date}/approve", offering, date)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());
    }
}