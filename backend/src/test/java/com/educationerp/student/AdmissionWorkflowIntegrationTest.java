package com.educationerp.student;

import com.educationerp.student.dto.StudentDtos;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The blueprint admission workflow: campaign → application → submission → review →
 * documents → eligibility → selection → approval → student creation. Also proves that a
 * retried approval does not create a second student.
 */
class AdmissionWorkflowIntegrationTest extends com.educationerp.support.IntegrationTest {

    private String openCampaign() throws Exception {
        testData.institution();
        String token = adminToken();
        String body = mockMvc.perform(post("/api/v1/admissions/campaigns")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Grade 10 Intake",
                                "code", "G10-2024",
                                "openDate", LocalDate.now().minusDays(5).toString(),
                                "closeDate", LocalDate.now().plusDays(30).toString(),
                                "status", "OPEN",
                                "documentRequirements", List.of("CITIZENSHIP", "TRANSCRIPT")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private String newApplication(String campaignId) throws Exception {
        testData.institution();
        String token = adminToken();
        String body = mockMvc.perform(post("/api/v1/admissions/applications")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "campaignId", campaignId,
                                "firstName", "Ravi",
                                "lastName", "Sharma",
                                "email", "ravi.sharma@example.edu",
                                "phone", "+977-9800000001",
                                "nationality", "Nepal",
                                "previousQualification", "+2",
                                "previousPercentage", "78.5"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    @Test
    void approvalCreatesStudentAndIsIdempotent() throws Exception {
        String campaignId = openCampaign();
        String applicationId = newApplication(campaignId);
        String token = adminToken();

        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/submit")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/review")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNDER_REVIEW"));

        // Documents are attached against the campaign's configured requirements.
        for (String type : List.of("CITIZENSHIP", "TRANSCRIPT")) {
            mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/documents")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "documentType", type,
                                    "fileName", type + ".pdf",
                                    "storageKey", "admissions/" + type + ".pdf",
                                    "contentType", "application/pdf",
                                    "sizeBytes", 20480))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/documents/verify")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(
                                Map.of("documentType", "CITIZENSHIP", "status", "ACCEPTED", "notes", "Verified"),
                                Map.of("documentType", "TRANSCRIPT", "status", "ACCEPTED", "notes", "Verified")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rejected").value(false))
                .andExpect(jsonPath("$.data.missingDocuments").isEmpty())
                .andExpect(jsonPath("$.data.application.status").value("ELIGIBILITY"));

        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/eligibility")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "OFFERED", "notes", "Merit list cleared"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SELECTED"));

        String approval = mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/approve")
                        .header("Authorization", bearer(token))
                        .param("notes", "Approved by registrar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.application.status").value("ADMITTED"))
                .andReturn().getResponse().getContentAsString();

        var json = objectMapper.readTree(approval).path("data");
        String studentNumber = json.path("student").path("studentNumber").asText();
        assertThat(studentNumber).isNotBlank();

        // Retrying approval must not mint a second student.
        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/approve")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.student.studentNumber").value(studentNumber));

        assertThat(countStudents()).isEqualTo(1);
    }

    @Test
    void missingRequiredDocumentHoldsApplicationAtVerification() throws Exception {
        String campaignId = openCampaign();
        String applicationId = newApplication(campaignId);
        String token = adminToken();

        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/submit")
                .header("Authorization", bearer(token))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/review")
                .header("Authorization", bearer(token))).andExpect(status().isOk());

        // Only one of the two required documents is supplied.
        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/documents")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "documentType", "CITIZENSHIP",
                                "fileName", "citizenship.pdf",
                                "storageKey", "admissions/citizenship.pdf"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/documents/verify")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(
                                Map.of("documentType", "CITIZENSHIP", "status", "ACCEPTED")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missingDocuments").isArray())
                .andExpect(jsonPath("$.data.missingDocuments[0]").value("TRANSCRIPT"));

        // Still at verification: it must not advance while a document is outstanding.
        mockMvc.perform(get("/api/v1/admissions/applications/" + applicationId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DOCUMENT_VERIFICATION"));
    }

    @Test
    void rejectedDocumentRejectsTheApplication() throws Exception {
        String campaignId = openCampaign();
        String applicationId = newApplication(campaignId);
        String token = adminToken();

        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/submit")
                .header("Authorization", bearer(token))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/review")
                .header("Authorization", bearer(token))).andExpect(status().isOk());

        for (String type : List.of("CITIZENSHIP", "TRANSCRIPT")) {
            mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/documents")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "documentType", type,
                                    "fileName", type + ".pdf",
                                    "storageKey", "admissions/" + type + ".pdf"))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/documents/verify")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(
                                Map.of("documentType", "CITIZENSHIP", "status", "ACCEPTED"),
                                Map.of("documentType", "TRANSCRIPT", "status", "REJECTED",
                                        "notes", "Illegible")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rejected").value(true))
                .andExpect(jsonPath("$.data.application.status").value("REJECTED"));

        assertThat(countStudents()).isZero();
    }

    @Test
    void illegalTransitionIsRejected() throws Exception {
        String campaignId = openCampaign();
        String applicationId = newApplication(campaignId);
        String token = adminToken();

        // Eligibility cannot be decided before the application is submitted.
        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/eligibility")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "SELECTED"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void closedCampaignRejectsSubmissions() throws Exception {
        testData.institution();
        String token = adminToken();
        String body = mockMvc.perform(post("/api/v1/admissions/campaigns")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Closed Intake",
                                "code", "CLOSED-1",
                                "openDate", LocalDate.now().minusDays(30).toString(),
                                "closeDate", LocalDate.now().minusDays(10).toString(),
                                "status", "CLOSED"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String campaignId = objectMapper.readTree(body).path("data").path("id").asText();
        String applicationId = newApplication(campaignId);

        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/submit")
                        .header("Authorization", bearer(token)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void duplicateCampaignCodeIsRejected() throws Exception {
        openCampaign();
        String token = adminToken();
        mockMvc.perform(post("/api/v1/admissions/campaigns")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Duplicate", "code", "G10-2024"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void campaignDatesMustBeOrdered() throws Exception {
        testData.institution();
        String token = adminToken();
        mockMvc.perform(post("/api/v1/admissions/campaigns")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Backwards",
                                "code", "BACK-1",
                                "openDate", LocalDate.now().toString(),
                                "closeDate", LocalDate.now().minusDays(5).toString()))))
                .andExpect(status().is4xxClientError());
    }

    // ------------------------------------------- approval, fees and enrolment

    /**
     * A campaign tied to the seeded academic year and one of the seeded classes, so the
     * fee and enrolment steps have something real to attach to.
     */
    private String classCampaign(String code, String classCode) throws Exception {
        testData.institution();
        String token = adminToken();
        UUID classId = jdbcTemplate.queryForObject(
                "SELECT id FROM school_classes WHERE code = ?", UUID.class, new Object[]{classCode});
        String body = mockMvc.perform(post("/api/v1/admissions/campaigns")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Grade intake " + classCode,
                                "code", code,
                                "academicYearId", testData.academicYear().getId().toString(),
                                "openDate", LocalDate.now().minusDays(5).toString(),
                                "closeDate", LocalDate.now().plusDays(30).toString(),
                                "status", "OPEN",
                                "documentRequirements", List.of("CITIZENSHIP")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private String applicationFor(String campaignId, String classCode) throws Exception {
        UUID classId = jdbcTemplate.queryForObject(
                "SELECT id FROM school_classes WHERE code = ?", UUID.class, new Object[]{classCode});
        String token = adminToken();
        String body = mockMvc.perform(post("/api/v1/admissions/applications")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "campaignId", campaignId,
                                "applyingClassId", classId.toString(),
                                "firstName", "Sita",
                                "lastName", "Gurung",
                                "email", "sita.gurung@example.edu",
                                "nationality", "Nepal",
                                "previousQualification", "+2",
                                "previousPercentage", "82.0"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private String publishedStructure(String code, String classCode, String amount) throws Exception {
        String token = adminToken();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("name", "Grade fees " + classCode);
        body.put("code", code);
        body.put("schoolClassId", jdbcTemplate.queryForObject(
                "SELECT id FROM school_classes WHERE code = ?", UUID.class, new Object[]{classCode}).toString());
        body.put("academicYearId", testData.academicYear().getId().toString());
        body.put("totalAmount", new java.math.BigDecimal(amount));
        String created = mockMvc.perform(post("/api/v1/finance/fee-structures")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/finance/fee-structures/" + id + "/publish")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        return id;
    }

    /** Walks an application to SELECTED, the state approval expects. */
    private String driveToSelected(String applicationId) throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/submit")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/review")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/documents")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "documentType", "CITIZENSHIP",
                                "fileName", "citizenship.pdf",
                                "storageKey", "admissions/citizenship.pdf"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/documents/verify")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(
                                Map.of("documentType", "CITIZENSHIP", "status", "ACCEPTED")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application.status").value("ELIGIBILITY"));
        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/eligibility")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "OFFERED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SELECTED"));
        return token;
    }

    @Test
    void approvalAssessesThePublishedStructureWithoutBeingAsked() throws Exception {
        String campaignId = classCampaign("FEES-1", "G10");
        String applicationId = applicationFor(campaignId, "G10");
        publishedStructure("FS-G10", "G10", "45000.00");
        String token = driveToSelected(applicationId);

        String approval = mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/approve")
                        .header("Authorization", bearer(token))
                        .param("notes", "Registrar approved"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application.status").value("ADMITTED"))
                .andReturn().getResponse().getContentAsString();
        String studentId = objectMapper.readTree(approval).path("data").path("student").path("id").asText();

        // The blueprint puts fee assessment straight after approval, so nobody had to
        // type the structure onto the student by hand.
        mockMvc.perform(get("/api/v1/finance/students/" + studentId + "/fee-assessments")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].grossAmount").value(45000.00))
                .andExpect(jsonPath("$.data[0].netAmount").value(45000.00))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void aRetriedApprovalDoesNotAssessTheStudentTwice() throws Exception {
        String campaignId = classCampaign("FEES-2", "G10");
        String applicationId = applicationFor(campaignId, "G10");
        publishedStructure("FS-G10B", "G10", "30000.00");
        String token = driveToSelected(applicationId);

        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/approve")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/approve")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(false));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM student_fee_assessments", Long.class)).isEqualTo(1);
    }

    @Test
    void aClassStructureWinsOverTheYearWideOne() throws Exception {
        String campaignId = classCampaign("FEES-3", "G10");
        String applicationId = applicationFor(campaignId, "G10");
        String token = driveToSelected(applicationId);
        // A year-wide structure is published first, so the class one has to win on merit.
        publishedYearWideStructure("FS-YEAR", "25000.00");
        publishedStructure("FS-G10C", "G10", "41000.00");

        String approval = mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/approve")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String studentId = objectMapper.readTree(approval).path("data").path("student").path("id").asText();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT gross_amount FROM student_fee_assessments WHERE student_id = ?",
                java.math.BigDecimal.class, new Object[]{UUID.fromString(studentId)}))
                .isEqualByComparingTo("41000.00");
    }

    private void publishedYearWideStructure(String code, String amount) throws Exception {
        String token = adminToken();
        String created = mockMvc.perform(post("/api/v1/finance/fee-structures")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Whole school fees",
                                "code", code,
                                "academicYearId", testData.academicYear().getId().toString(),
                                "totalAmount", new java.math.BigDecimal(amount)))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/finance/fee-structures/" + id + "/publish")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void approvalSucceedsWhenNoStructureHasBeenPublishedYet() throws Exception {
        String campaignId = classCampaign("FEES-4", "G10");
        String applicationId = applicationFor(campaignId, "G10");
        String token = driveToSelected(applicationId);

        // Fees can be set up after the fact, so an unpublished year must not block admission.
        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/approve")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application.status").value("ADMITTED"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM student_fee_assessments", Long.class)).isZero();
    }

    @Test
    void enrolmentPutsTheStudentInTheApplyingClassAndClosesTheApplication() throws Exception {
        String campaignId = classCampaign("ENR-1", "G10");
        String applicationId = applicationFor(campaignId, "G10");
        String token = driveToSelected(applicationId);
        UUID sectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM sections WHERE code = 'A' ORDER BY created_at LIMIT 1", UUID.class);

        String approval = mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/approve")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String studentId = objectMapper.readTree(approval).path("data").path("student").path("id").asText();

        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/enrol")
                        .header("Authorization", bearer(token))
                        .param("sectionId", sectionId.toString())
                        .param("rollNumber", "10-A-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.application.status").value("ENROLLED"))
                .andExpect(jsonPath("$.data.enrollment.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.enrollment.rollNumber").value("10-A-1"));

        // The whole chain is on the record: applicant, admission, fee, class.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM enrollments WHERE student_id = ? AND status = 'ACTIVE'",
                Long.class, new Object[]{UUID.fromString(studentId)})).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT school_class_id FROM enrollments WHERE student_id = ?", UUID.class,
                new Object[]{UUID.fromString(studentId)}))
                .isEqualTo(jdbcTemplate.queryForObject(
                        "SELECT id FROM school_classes WHERE code = 'G10'", UUID.class));

        // Running the step again must not enrol the student a second time.
        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/enrol")
                        .header("Authorization", bearer(token))
                        .param("sectionId", sectionId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(false));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM enrollments WHERE student_id = ?", Long.class,
                new Object[]{UUID.fromString(studentId)}))
                .isEqualTo(1);
    }

    @Test
    void enrolmentBeforeApprovalIsRefused() throws Exception {
        String campaignId = classCampaign("ENR-2", "G10");
        String applicationId = applicationFor(campaignId, "G10");
        String token = adminToken();

        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/enrol")
                        .header("Authorization", bearer(token)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void aFullClassRefusesAnotherEnrolment() throws Exception {
        String campaignId = classCampaign("ENR-3", "G12");
        String applicationId = applicationFor(campaignId, "G12");
        String token = driveToSelected(applicationId);
        // The setup seeded Grade 12 without any sections, so one is created for it here.
        String sectionId = createSection("G12", "A");
        // A section with room for exactly one student.
        jdbcTemplate.update("UPDATE sections SET capacity = 1 WHERE id = ?",
                UUID.fromString(sectionId));

        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/approve")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admissions/applications/" + applicationId + "/enrol")
                        .header("Authorization", bearer(token))
                        .param("sectionId", sectionId))
                .andExpect(status().isOk());

        // A second applicant to the same tiny section has nowhere to sit.
        String second = applicationFor(campaignId, "G12");
        String secondToken = driveToSelected(second);
        mockMvc.perform(post("/api/v1/admissions/applications/" + second + "/approve")
                        .header("Authorization", bearer(secondToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admissions/applications/" + second + "/enrol")
                        .header("Authorization", bearer(secondToken))
                        .param("sectionId", sectionId))
                .andExpect(status().is4xxClientError());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM enrollments WHERE section_id = ? AND status = 'ACTIVE'",
                Long.class, new Object[]{UUID.fromString(sectionId)})).isEqualTo(1);
    }

    /** Creates a section in a seeded class and returns its id. */
    private String createSection(String classCode, String code) throws Exception {
        String token = adminToken();
        UUID classId = jdbcTemplate.queryForObject(
                "SELECT id FROM school_classes WHERE code = ?", UUID.class, new Object[]{classCode});
        String body = mockMvc.perform(post("/api/v1/academic/sections")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "schoolClassId", classId.toString(),
                                "name", "Section " + code,
                                "code", code,
                                "capacity", 40))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private long countStudents() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM students", Long.class);
    }
}