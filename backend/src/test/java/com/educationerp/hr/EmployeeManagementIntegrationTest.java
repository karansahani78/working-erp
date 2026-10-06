package com.educationerp.hr;

import com.educationerp.auth.role.Role;
import com.educationerp.institution.ModuleKey;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Employee records (blueprint section 33): personal and employment details, department and
 * designation, qualifications, documents, and the module switch that governs all of it.
 */
class EmployeeManagementIntegrationTest extends IntegrationTest {

    private String token;

    @BeforeEach
    void enableHr() throws Exception {
        testData.enableModule(ModuleKey.HR);
        token = adminToken();
    }

    private String createEmployee(String code, String firstName) throws Exception {
        String body = mockMvc.perform(post("/api/v1/hr/employees")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "employeeCode", code,
                                "firstName", firstName,
                                "lastName", "Adhikari",
                                "joinDate", "2023-04-01",
                                "phone", "+977-9800000100",
                                "email", firstName.toLowerCase() + "@sunrise.edu.np",
                                "employmentType", "FULL_TIME"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    @Test
    void createsEmployeeWithPersonalAndEmploymentDetails() throws Exception {
        String id = createEmployee("emp-001", "Sabina");
        String body = mockMvc.perform(get("/api/v1/hr/employees/" + id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var data = objectMapper.readTree(body).path("data");
        // The code is the identity used on letters and payslips, so it is normalised.
        assertThat(data.path("employeeCode").asText()).isEqualTo("EMP-001");
        assertThat(data.path("fullName").asText()).isEqualTo("Sabina Adhikari");
        assertThat(data.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(data.path("employmentType").asText()).isEqualTo("FULL_TIME");
        assertThat(data.path("joinDate").asText()).isEqualTo("2023-04-01");
    }

    @Test
    void rejectsDuplicateEmployeeCode() throws Exception {
        createEmployee("emp-002", "Nisha");
        mockMvc.perform(post("/api/v1/hr/employees")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "employeeCode", "emp-002",
                                "firstName", "Copy",
                                "joinDate", "2023-04-01"))))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsEmployeeCodeDuplicatedInDifferentCase() throws Exception {
        createEmployee("emp-003", "Rajan");
        mockMvc.perform(post("/api/v1/hr/employees")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "employeeCode", "EMP-003",
                                "firstName", "Copy",
                                "joinDate", "2023-04-01"))))
                .andExpect(status().isConflict());
    }

    @Test
    void assignsDepartmentAndDesignation() throws Exception {
        UUID departmentId = createDepartment();
        String designationBody = mockMvc.perform(post("/api/v1/hr/designations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "teacher-senior",
                                "name", "Senior Teacher",
                                "level", "SENIOR"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String designationId = objectMapper.readTree(designationBody).path("data").path("id").asText();

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("employeeCode", "emp-004");
        payload.put("firstName", "Bikash");
        payload.put("joinDate", "2023-04-01");
        payload.put("departmentId", departmentId);
        payload.put("designationId", designationId);
        String body = mockMvc.perform(post("/api/v1/hr/employees")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var data = objectMapper.readTree(body).path("data");
        assertThat(data.path("departmentId").asText()).isEqualTo(departmentId.toString());
        assertThat(data.path("designationName").asText()).isEqualTo("Senior Teacher");
    }

    @Test
    void recordsAnInitialEmploymentSpell() throws Exception {
        String id = createEmployee("emp-005", "Maya");
        String body = mockMvc.perform(get("/api/v1/hr/employees/" + id + "/profile")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var employments = objectMapper.readTree(body).path("data").path("employments");
        // Creating an employee opens an employment, so payroll always has terms to read.
        assertThat(employments).hasSize(1);
        assertThat(employments.get(0).path("current").asBoolean()).isTrue();
        assertThat(employments.get(0).path("startDate").asText()).isEqualTo("2023-04-01");
    }

    @Test
    void refusesASecondOpenEmploymentSpell() throws Exception {
        String id = createEmployee("emp-006", "Prakash");
        mockMvc.perform(post("/api/v1/hr/employees/" + id + "/employments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "employmentType", "FULL_TIME",
                                "startDate", "2024-01-01"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void closesEmploymentOnTermination() throws Exception {
        String id = createEmployee("emp-007", "Anita");
        mockMvc.perform(post("/api/v1/hr/employees/" + id + "/terminate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "exitDate", "2024-06-30",
                                "status", "RESIGNED",
                                "reason", "Relocated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESIGNED"))
                .andExpect(jsonPath("$.data.exitDate").value("2024-06-30"));
    }

    @Test
    void refusesExitDateBeforeJoiningDate() throws Exception {
        String id = createEmployee("emp-008", "Kiran");
        mockMvc.perform(post("/api/v1/hr/employees/" + id + "/terminate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "exitDate", "2022-01-01",
                                "status", "RESIGNED"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void refusesASecondTermination() throws Exception {
        String id = createEmployee("emp-009", "Deepak");
        Map<String, String> termination = Map.of("exitDate", "2024-06-30", "status", "RESIGNED");
        mockMvc.perform(post("/api/v1/hr/employees/" + id + "/terminate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(termination)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/hr/employees/" + id + "/terminate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(termination)))
                .andExpect(status().isConflict());
    }

    @Test
    void awardsQualificationToAnEmployee() throws Exception {
        String employeeId = createEmployee("emp-010", "Sunita");
        String qualificationId = createQualification("Master of Education");
        mockMvc.perform(post("/api/v1/hr/employees/" + employeeId + "/qualifications")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
                            put("qualificationId", qualificationId);
                            put("institution", "Tribhuvan University");
                            put("awardedYear", 2015);
                            put("grade", "Distinction");
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Master of Education"))
                .andExpect(jsonPath("$.data.institution").value("Tribhuvan University"));
    }

    @Test
    void refusesTheSameQualificationTwice() throws Exception {
        String employeeId = createEmployee("emp-011", "Gopal");
        String qualificationId = createQualification("Bachelor of Education");
        Map<String, Object> award = Map.of("qualificationId", qualificationId);
        mockMvc.perform(post("/api/v1/hr/employees/" + employeeId + "/qualifications")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(award)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/hr/employees/" + employeeId + "/qualifications")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(award)))
                .andExpect(status().isConflict());
    }

    @Test
    void registersAndReviewsAnEmployeeDocument() throws Exception {
        String employeeId = createEmployee("emp-012", "Rita");
        String body = mockMvc.perform(post("/api/v1/hr/employees/" + employeeId + "/documents")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "documentType", "citizenship",
                                "fileName", "citizenship.pdf",
                                "storageKey", "hr/emp-012/citizenship.pdf",
                                "contentType", "application/pdf",
                                "sizeBytes", 20480))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        // The type is normalised so a later search does not depend on the caller's casing.
        assertThat(objectMapper.readTree(body).path("data").path("documentType").asText())
                .isEqualTo("CITIZENSHIP");
        String documentId = objectMapper.readTree(body).path("data").path("id").asText();

        mockMvc.perform(put("/api/v1/hr/employees/" + employeeId + "/documents/" + documentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    }

    @Test
    void refusesToReviewADocumentTwice() throws Exception {
        String employeeId = createEmployee("emp-013", "Hari");
        String documentId = documentId(employeeId, "identity-card");
        mockMvc.perform(put("/api/v1/hr/employees/" + employeeId + "/documents/" + documentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "REJECTED"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/hr/employees/" + employeeId + "/documents/" + documentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isConflict());
    }

    @Test
    void listsEmployeesFilteredByStatus() throws Exception {
        String active = createEmployee("emp-014", "Aarti");
        String departing = createEmployee("emp-015", "Bikash");
        mockMvc.perform(post("/api/v1/hr/employees/" + departing + "/terminate")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "exitDate", "2024-06-30", "status", "TERMINATED"))))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/v1/hr/employees")
                        .header("Authorization", bearer(token))
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> codes = objectMapper.readTree(body).path("data").findValuesAsText("employeeCode");
        assertThat(codes).contains("EMP-014").doesNotContain("EMP-015");
        assertThat(active).isNotBlank();
    }

    @Test
    void refusesTheHrApiWhileTheModuleIsOff() throws Exception {
        jdbcTemplate.update("update module_settings set enabled = false where module_key = 'HR'");
        mockMvc.perform(get("/api/v1/hr/employees")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void refusesEmployeeCreationWithoutPermission() throws Exception {
        String studentToken = loginAsStudent();
        mockMvc.perform(get("/api/v1/hr/employees")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
    }

    private UUID createDepartment() throws Exception {
        String facultyBody = mockMvc.perform(post("/api/v1/academic/faculties")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "science",
                                "name", "Science and Mathematics",
                                "active", true))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String facultyId = objectMapper.readTree(facultyBody).path("data").path("id").asText();

        String body = mockMvc.perform(post("/api/v1/academic/departments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
                            put("code", "physics");
                            put("name", "Physics");
                            put("facultyId", facultyId);
                            put("active", true);
                        }})))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    // ------------------------------------------------------------------ helpers

    private String createQualification(String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/hr/qualifications")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "level", "MASTERS"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private String documentId(String employeeId, String type) throws Exception {
        String body = mockMvc.perform(post("/api/v1/hr/employees/" + employeeId + "/documents")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "documentType", type,
                                "fileName", type + ".pdf",
                                "storageKey", "hr/" + employeeId + "/" + type + ".pdf"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private String loginAsStudent() throws Exception {
        testData.user("onestudent", "One Student", "one.student@example.test",
                Role.STUDENT, "StudentPass123");
        return loginToken("onestudent", "StudentPass123");
    }
}