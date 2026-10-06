package com.educationerp.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Student CRUD, server-generated numbers, status changes, guardians and enrolment
 * integrity rules.
 */
class StudentManagementIntegrationTest extends com.educationerp.support.IntegrationTest {

    @BeforeEach
    void seed() {
        testData.institution();
    }

    private String createStudent(String email) throws Exception {
        String token = adminToken();
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Sita",
                                "lastName", "Gurung",
                                "email", email,
                                "phone", "+977-9800000010",
                                "nationality", "Nepal",
                                "status", "ACTIVE"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asText();
    }

    @Test
    void studentNumberIsGeneratedServerSideAndSequential() throws Exception {
        String token = adminToken();
        String first = createStudent("a@example.edu");
        String second = createStudent("b@example.edu");

        String one = number(first);
        String two = number(second);
        assertThat(one).startsWith("SUNRISE-");
        assertThat(two).isNotEqualTo(one);
        assertThat(numberAfter(one)).isLessThan(numberAfter(two));
    }

    @Test
    void clientCannotSupplyStudentNumber() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Forged",
                                "studentNumber", "HACKED-0001"))))
                .andExpect(status().isCreated());
        // The supplied value is ignored; the server assigned its own.
        String body = mockMvc.perform(get("/api/v1/students")
                        .header("Authorization", bearer(token))
                        .param("term", "Forged"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String generated = objectMapper.readTree(body).path("data").path("data").get(0).path("studentNumber").asText();
        assertThat(generated).isNotEqualTo("HACKED-0001").startsWith("SUNRISE-");
    }

    @Test
    void duplicateStudentEmailIsRejected() throws Exception {
        createStudent("dup@example.edu");
        String token = adminToken();
        mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Copy", "email", "dup@example.edu"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void statusChangeUsesItsOwnEndpoint() throws Exception {
        String id = createStudent("status@example.edu");
        String token = adminToken();
        mockMvc.perform(patch("/api/v1/students/" + id + "/status")
                        .header("Authorization", bearer(token))
                        .param("status", "SUSPENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        mockMvc.perform(patch("/api/v1/students/" + id + "/status")
                        .header("Authorization", bearer(token))
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void deletingActiveStudentWithdrawsInsteadOfRemoving() throws Exception {
        String id = createStudent("withdraw@example.edu");
        String token = adminToken();
        mockMvc.perform(delete("/api/v1/students/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/students/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
    }

    @Test
    void student360AggregatesProfileGuardiansAndEnrolment() throws Exception {
        String studentId = createStudent("view@example.edu");
        String token = adminToken();

        // A guardian with two children proves parent-child links are explicit.
        String guardianId = objectMapper.readTree(mockMvc.perform(post("/api/v1/guardians")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Bikash", "lastName", "Gurung",
                                "phone", "+977-9800000020", "email", "guardian@example.edu"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asText();

        String siblingId = createStudent("sibling@example.edu");
        for (String child : new String[]{studentId, siblingId}) {
            mockMvc.perform(post("/api/v1/guardians/student/" + child)
                            .header("Authorization", bearer(token))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "guardianId", guardianId, "relationship", "FATHER",
                                    "primaryContact", true, "canPickup", true))))
                    .andExpect(status().isCreated());
        }

        // One guardian, two children.
        mockMvc.perform(get("/api/v1/guardians/" + guardianId + "/students")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.length()").value(2));

        var year = testData.academicYear();
        String classId = objectMapper.readTree(mockMvc.perform(post("/api/v1/academic/classes")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "academicYearId", year.getId().toString(),
                                "name", "Grade 9", "code", "G9", "ordinal", 9, "active", true))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/enrollments")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", studentId,
                                "academicYearId", year.getId().toString(),
                                "schoolClassId", classId,
                                "rollNumber", "9"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/students/" + studentId + "/360")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Sita Gurung"))
                .andExpect(jsonPath("$.data.guardians.length()").value(1))
                .andExpect(jsonPath("$.data.guardians[0].relationship").value("FATHER"))
                .andExpect(jsonPath("$.data.enrollments.length()").value(1));
    }

    @Test
    void student360CarriesTheFinanceTabAndOmitsItWhenThereIsNothingToShow() throws Exception {
        String token = adminToken();
        String assessed = createStudent("billed@example.edu");
        var year = testData.academicYear();

        // Nothing billed yet, so the tab is absent rather than an empty box.
        mockMvc.perform(get("/api/v1/students/" + assessed + "/360")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finance").doesNotExist());

        String structureId = objectMapper.readTree(mockMvc.perform(post("/api/v1/finance/fee-structures")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "FS-360", "name", "Grade 9 fees", "academicYearId", year.getId().toString(),
                                "totalAmount", 40000, "currency", "NPR"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/finance/fee-structures/{id}/publish", structureId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        objectMapper.readTree(mockMvc.perform(post("/api/v1/finance/students/" + assessed + "/fee-assessments")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "feeStructureId", structureId, "dueDate", "2026-12-31"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/students/" + assessed + "/360")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finance.totalNet").value(40000.00))
                .andExpect(jsonPath("$.data.finance.totalPaid").value(0.00))
                .andExpect(jsonPath("$.data.finance.totalOutstanding").value(40000.00))
                .andExpect(jsonPath("$.data.finance.assessments.length()").value(1))
                .andExpect(jsonPath("$.data.finance.payments.length()").value(0));
    }

    @Test
    void duplicateEnrolmentIsRejected() throws Exception {
        String studentId = createStudent("enrol@example.edu");
        String token = adminToken();
        var year = testData.academicYear();
        String classId = objectMapper.readTree(mockMvc.perform(post("/api/v1/academic/classes")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "academicYearId", year.getId().toString(),
                                "name", "Grade 8", "code", "G8", "ordinal", 8, "active", true))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asText();

        Map<String, Object> payload = Map.of(
                "studentId", studentId,
                "academicYearId", year.getId().toString(),
                "schoolClassId", classId);

        mockMvc.perform(post("/api/v1/enrollments")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/enrollments")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void classFromAnotherAcademicYearCannotBeUsed() throws Exception {
        String studentId = createStudent("year@example.edu");
        String token = adminToken();

        var otherYear = testData.academicYear("2081/82", "AY208182",
                java.time.LocalDate.of(2024, 4, 1), java.time.LocalDate.of(2025, 3, 31));
        String classId = objectMapper.readTree(mockMvc.perform(post("/api/v1/academic/classes")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "academicYearId", otherYear.getId().toString(),
                                "name", "Grade 7", "code", "G7", "ordinal", 7, "active", true))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/enrollments")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", studentId,
                                "academicYearId", testData.academicYear().getId().toString(),
                                "schoolClassId", classId))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void withdrawnStudentCannotBeEnrolled() throws Exception {
        String studentId = createStudent("blocked@example.edu");
        String token = adminToken();
        var year = testData.academicYear();
        String classId = objectMapper.readTree(mockMvc.perform(post("/api/v1/academic/classes")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "academicYearId", year.getId().toString(),
                                "name", "Grade 6", "code", "G6", "ordinal", 6, "active", true))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(patch("/api/v1/students/" + studentId + "/status")
                        .header("Authorization", bearer(token))
                        .param("status", "WITHDRAWN"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/enrollments")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", studentId,
                                "academicYearId", year.getId().toString(),
                                "schoolClassId", classId))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void numberFormatIsConfigurable() throws Exception {
        String token = adminToken();
        mockMvc.perform(put("/api/v1/students/number-format")
                        .header("Authorization", bearer(token))
                        .param("format", "{PREFIX}{YEAR}{SEQ:3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.format").value("{PREFIX}{YEAR}{SEQ:3}"));

        mockMvc.perform(get("/api/v1/students/number-format").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.format").value("{PREFIX}{YEAR}{SEQ:3}"));

        String id = createStudent("format@example.edu");
        assertThat(number(id)).matches("SUNRISE\\d{4}\\d{3}");
    }

    private String number(String studentId) throws Exception {
        String token = adminToken();
        var response = mockMvc.perform(get("/api/v1/students/" + studentId)
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse();
        assertThat(response.getStatus())
                .withFailMessage("GET /api/v1/students/%s -> %s %s", studentId,
                        response.getStatus(), response.getContentAsString())
                .isEqualTo(200);
        return objectMapper.readTree(response.getContentAsString()).path("data").path("studentNumber").asText();
    }

    private long numberAfter(String studentNumber) {
        int index = studentNumber.length() - 5;
        return Long.parseLong(studentNumber.substring(index));
    }
}