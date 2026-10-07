package com.educationerp.auth;

import com.educationerp.hr.Employee;
import com.educationerp.hr.EmployeeRepository;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The strongest accounts and the shape of a sign-in are both guarded in the admin API:
 * someone below Super Admin must not be able to alter a Super Admin account, and a role
 * must never be combined with a profile link it cannot make sense of.
 */
class AdminSecurityTest extends IntegrationTest {

    @Autowired
    private EmployeeRepository employees;

    private static int employeeSeq = 6000;

    private UUID bareEmployeeId() {
        Employee employee = new Employee();
        employee.setEmployeeCode("EMP-SEC-" + (employeeSeq++));
        employee.setFirstName("Sign-in");
        employee.setLastName("Only");
        employee.setJoinDate(testData.YEAR_START);
        return employees.save(employee).getId();
    }

    private String adminId() throws Exception {
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/auth/me").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private String createOpsAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content("""
                                {"username":"opsadmin","password":"OpsPass1234","displayName":"Ops Admin",
                                 "email":"ops@sunrise.edu.np","role":"INSTITUTION_ADMIN"}
                                """))
                .andExpect(status().isCreated());
        return loginToken("opsadmin", "OpsPass1234");
    }

    @Test
    @DisplayName("an institution admin can manage ordinary users but never a Super Admin")
    void superAdminProtectedFromLowerAdmins() throws Exception {
        String superAdminId = adminId();
        String ops = createOpsAdmin();

        mockMvc.perform(put("/api/v1/admin/users/" + superAdminId)
                        .header("Authorization", bearer(ops))
                        .contentType("application/json")
                        .content("""
                                {"displayName":"Administrator","email":"admin@sunrise.edu.np",
                                 "role":"PRINCIPAL","status":"ACTIVE"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Only a Super Admin can manage another Super Admin account."));

        mockMvc.perform(put("/api/v1/admin/users/" + superAdminId)
                        .header("Authorization", bearer(ops))
                        .contentType("application/json")
                        .content("""
                                {"displayName":"Administrator","email":"admin@sunrise.edu.np",
                                 "role":"SUPER_ADMIN","status":"LOCKED"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Only a Super Admin can manage another Super Admin account."));

        mockMvc.perform(post("/api/v1/admin/users/" + superAdminId + "/password")
                        .header("Authorization", bearer(ops))
                        .contentType("application/json")
                        .content("""
                                {"password":"UsurpedPass123"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Only a Super Admin can manage another Super Admin account."));

        // A lower administrator still manages real users below the top tier.
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(ops))
                        .contentType("application/json")
                        .content("""
                                {"username":"bursar","password":"BursarPass123","displayName":"Bursar",
                                 "email":"bursar@sunrise.edu.np","role":"ACCOUNTANT"}
                                """))
                .andExpect(status().isCreated());

        String created = mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(ops))
                        .contentType("application/json")
                        .content("""
                                {"username":"registrar","password":"RegPass1234","displayName":"Registrar",
                                 "role":"RECEPTIONIST"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String registrarId = objectMapper.readTree(created).path("data").path("id").asText();

        mockMvc.perform(put("/api/v1/admin/users/" + registrarId)
                        .header("Authorization", bearer(ops))
                        .contentType("application/json")
                        .content("""
                                {"displayName":"Chief Registrar","role":"RECEPTIONIST","status":"ACTIVE"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a super admin demoting one super admin is still stopped when it is the last one")
    void lastSuperAdminStillProtected() throws Exception {
        String superAdminId = adminId();

        mockMvc.perform(put("/api/v1/admin/users/" + superAdminId)
                        .header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content("""
                                {"displayName":"Administrator","email":"admin@sunrise.edu.np",
                                 "role":"PRINCIPAL","status":"ACTIVE"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        "This is the only active administrator; demote would leave nobody able to administer the institution."));
    }

    @Test
    @DisplayName("a student sign-in cannot be linked to an employee profile, and never the reverse")
    void impossibleIdentityCombosRefused() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"username":"shesradey","password":"StudPass123","displayName":"Shesra",
                                 "role":"STUDENT","studentId":"%s","employeeId":"%s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("A student sign-in cannot be linked to an employee profile."));

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"username":"sir","password":"TeachPass123","displayName":"Mr Teacher",
                                 "role":"TEACHER","studentId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("A student profile link is only valid for a student sign-in."));

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"username":"guardian","password":"ParentPass123","displayName":"Guardian",
                                 "role":"PARENT","studentId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("A parent sign-in cannot be linked to a student or employee profile."));
    }

    @Test
    @DisplayName("a role change cannot smuggle a profile link it no longer supports")
    void roleChangeCannotCreateAContradiction() throws Exception {
        String token = adminToken();

        String created = mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"username":"casey","password":"CasePass123","displayName":"Casey",
                                 "role":"STUDENT","studentId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).path("data").path("id").asText();

        mockMvc.perform(put("/api/v1/admin/users/" + id)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"displayName":"Casey","role":"TEACHER","status":"ACTIVE"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("A student profile link is only valid for a student sign-in."));
    }

    @Test
    @DisplayName("matching role and profile links are still accepted")
    void matchingIdentityStillAccepted() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"username":"stella","password":"StellaPass12","displayName":"Stella",
                                 "role":"STUDENT","studentId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"username":"msmith","password":"MatrixPass12","displayName":"M Smith",
                                 "role":"TEACHER","employeeId":"%s"}
                                """.formatted(bareEmployeeId())))
                .andExpect(status().isCreated());
    }
}