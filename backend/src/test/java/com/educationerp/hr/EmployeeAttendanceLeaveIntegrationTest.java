package com.educationerp.hr;

import com.educationerp.auth.role.Role;
import com.educationerp.institution.ModuleKey;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Staff attendance and leave (blueprint section 33): marking a day, deriving overtime from
 * it, and the balance check that stops an approval spending days the employee does not have.
 */
class EmployeeAttendanceLeaveIntegrationTest extends IntegrationTest {

    private String token;
    private String leaveTypeId;

    @BeforeEach
    void enableHr() throws Exception {
        testData.enableModule(ModuleKey.HR);
        token = adminToken();
        leaveTypeId = createLeaveType("annual", "Annual leave", 14);
    }

    private String createLeaveType(String code, String name, int daysPerYear) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("name", name);
        payload.put("daysPerYear", daysPerYear);
        payload.put("paid", true);
        String body = mockMvc.perform(post("/api/v1/hr/leave/types")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private UUID createEmployee(String code) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeCode", code);
        payload.put("firstName", "Staff");
        payload.put("joinDate", "2023-04-01");
        String body = mockMvc.perform(post("/api/v1/hr/employees")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private String mark(UUID employeeId, String date, String status, String in, String out)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attendanceDate", date);
        payload.put("status", status);
        if (in != null) {
            payload.put("checkIn", in);
        }
        if (out != null) {
            payload.put("checkOut", out);
        }
        String body = mockMvc.perform(post("/api/v1/hr/attendance/employees/" + employeeId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body;
    }

    // -------------------------------------------------------------- attendance

    @Test
    void marksAttendanceForAnEmployee() throws Exception {
        UUID employeeId = createEmployee("att-001");
        String body = mark(employeeId, "2024-01-08", "PRESENT", "09:00:00", "18:00:00");
        var data = objectMapper.readTree(body).path("data");
        assertThat(data.path("status").asText()).isEqualTo("PRESENT");
        assertThat(data.path("attendanceDate").asText()).isEqualTo("2024-01-08");
        // A nine-hour day is an hour of overtime, worked out from the clock rather than typed in.
        assertThat(data.path("overtimeMinutes").asInt()).isEqualTo(60);
    }

    @Test
    void derivesNoOvertimeFromAStandardDay() throws Exception {
        UUID employeeId = createEmployee("att-002");
        String standard = mark(employeeId, "2024-01-09", "PRESENT", "09:00:00", "17:00:00");
        assertThat(objectMapper.readTree(standard).path("data").path("overtimeMinutes").asInt())
                .isZero();
        String short_ = mark(employeeId, "2024-01-10", "PRESENT", "09:00:00", "16:00:00");
        assertThat(objectMapper.readTree(short_).path("data").path("overtimeMinutes").asInt())
                .isZero();
    }

    @Test
    void prefersAnExplicitOvertimeFigureOverTheClockTimes() throws Exception {
        UUID employeeId = createEmployee("att-016");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attendanceDate", "2024-01-16");
        payload.put("status", "PRESENT");
        payload.put("checkIn", "09:00:00");
        payload.put("checkOut", "17:00:00");
        payload.put("overtimeMinutes", 45);
        String body = mockMvc.perform(post("/api/v1/hr/attendance/employees/" + employeeId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // A supervisor who recorded the overtime explicitly is believed over the clock.
        assertThat(objectMapper.readTree(body).path("data").path("overtimeMinutes").asInt())
                .isEqualTo(45);
    }

    @Test
    void reMarkingADayCorrectsItRatherThanDuplicating() throws Exception {
        UUID employeeId = createEmployee("att-003");
        mark(employeeId, "2024-01-11", "PRESENT", "09:00:00", "17:00:00");
        mark(employeeId, "2024-01-11", "ABSENT", null, null);
        // Payroll totals overtime over this table, so a duplicate row would pay twice.
        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from employee_attendance where employee_id = ?", Integer.class, employeeId);
        assertThat(rows).isEqualTo(1);
        String body = mockMvc.perform(get("/api/v1/hr/attendance/employees/{id}",
                        employeeId).header("Authorization", bearer(token))
                        .param("from", "2024-01-01").param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).path("data").get(0).path("status").asText())
                .isEqualTo("ABSENT");
    }

    @Test
    void refusesToMarkAttendanceForAFutureDate() throws Exception {
        UUID employeeId = createEmployee("att-004");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attendanceDate", java.time.LocalDate.now().plusDays(1).toString());
        payload.put("status", "PRESENT");
        mockMvc.perform(post("/api/v1/hr/attendance/employees/" + employeeId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void refusesOvertimeOnANonWorkingDay() throws Exception {
        UUID employeeId = createEmployee("att-005");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attendanceDate", "2024-01-07");
        payload.put("status", "WEEK_OFF");
        payload.put("overtimeMinutes", 120);
        mockMvc.perform(post("/api/v1/hr/attendance/employees/" + employeeId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void marksAWholeDepartmentInOneCall() throws Exception {
        UUID first = createEmployee("att-006");
        UUID second = createEmployee("att-007");
        Map<String, Object> bulk = new LinkedHashMap<>();
        bulk.put("attendanceDate", "2024-01-12");
        bulk.put("entries", List.of(
                Map.of("employeeId", first, "status", "PRESENT", "checkIn", "09:00:00", "checkOut", "17:00:00"),
                Map.of("employeeId", second, "status", "LATE", "checkIn", "09:30:00", "checkOut", "17:00:00")));
        mockMvc.perform(post("/api/v1/hr/attendance/bulk")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulk)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void refusesABulkMarkWithAnImpossibleClockOut() throws Exception {
        UUID first = createEmployee("att-008");
        Map<String, Object> bulk = new LinkedHashMap<>();
        bulk.put("attendanceDate", "2024-01-13");
        bulk.put("entries", List.of(Map.of("employeeId", first, "status", "PRESENT",
                "checkIn", "17:00:00", "checkOut", "09:00:00")));
        // Validated before anything is written, so a bad row cannot leave a half-marked day.
        mockMvc.perform(post("/api/v1/hr/attendance/bulk")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulk)))
                .andExpect(status().isUnprocessableEntity());
        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from employee_attendance", Integer.class);
        assertThat(rows).isZero();
    }

    // -------------------------------------------------------------------- leave

    @Test
    void opensAnEntitlementForTheYear() throws Exception {
        UUID employeeId = createEmployee("lv-001");
        String body = mockMvc.perform(post("/api/v1/hr/leave/balances/open")
                        .header("Authorization", bearer(token))
                        .param("employeeId", employeeId.toString())
                        .param("year", "2024"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var balance = objectMapper.readTree(body).path("data").get(0);
        assertThat(balance.path("entitled").decimalValue()).isEqualByComparingTo("14.0");
        assertThat(balance.path("used").decimalValue()).isEqualByComparingTo("0.0");
        assertThat(balance.path("remaining").decimalValue()).isEqualByComparingTo("14.0");
    }

    @Test
    void appliesForLeaveWithinTheBalance() throws Exception {
        UUID employeeId = createEmployee("lv-002");
        openBalances(employeeId);
        mockMvc.perform(post("/api/v1/hr/leave/requests")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leave(employeeId, "2024-02-01", "2024-02-05"))))
                .andExpect(status().isOk())
                // Five days inclusive, counted the way staff read a request.
                .andExpect(jsonPath("$.data.days").value(5.0))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void approvingLeaveDrawsDownTheBalance() throws Exception {
        UUID employeeId = createEmployee("lv-003");
        openBalances(employeeId);
        String requestId = apply(employeeId, "2024-03-01", "2024-03-04");

        mockMvc.perform(put("/api/v1/hr/leave/requests/" + requestId + "/decision")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        String body = mockMvc.perform(get("/api/v1/hr/leave/balances")
                        .header("Authorization", bearer(token))
                        .param("employeeId", employeeId.toString())
                        .param("year", "2024"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var balance = objectMapper.readTree(body).path("data").get(0);
        assertThat(balance.path("used").decimalValue()).isEqualByComparingTo("4.0");
        assertThat(balance.path("remaining").decimalValue()).isEqualByComparingTo("10.0");
    }

    @Test
    void refusesToApproveMoreLeaveThanTheBalanceAllows() throws Exception {
        UUID employeeId = createEmployee("lv-004");
        openBalances(employeeId);
        String requestId = apply(employeeId, "2024-04-01", "2024-04-30");
        // 30 days against a 14 day entitlement: the check happens before the approval.
        mockMvc.perform(put("/api/v1/hr/leave/requests/" + requestId + "/decision")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void refusingALeaveRequestLeavesTheBalanceAlone() throws Exception {
        UUID employeeId = createEmployee("lv-005");
        openBalances(employeeId);
        String requestId = apply(employeeId, "2024-04-01", "2024-04-05");
        mockMvc.perform(put("/api/v1/hr/leave/requests/" + requestId + "/decision")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "REJECTED"))))
                .andExpect(status().isOk());
        String body = mockMvc.perform(get("/api/v1/hr/leave/balances")
                        .header("Authorization", bearer(token))
                        .param("employeeId", employeeId.toString())
                        .param("year", "2024"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).path("data").get(0).path("used").decimalValue())
                .isEqualByComparingTo("0.0");
    }

    @Test
    void refusesToDecideTheSameRequestTwice() throws Exception {
        UUID employeeId = createEmployee("lv-006");
        openBalances(employeeId);
        String requestId = apply(employeeId, "2024-05-01", "2024-05-02");
        Map<String, String> approval = Map.of("status", "APPROVED");
        mockMvc.perform(put("/api/v1/hr/leave/requests/" + requestId + "/decision")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approval)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/hr/leave/requests/" + requestId + "/decision")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approval)))
                .andExpect(status().isConflict());
    }

    @Test
    void cancellingApprovedLeaveReturnsTheDays() throws Exception {
        UUID employeeId = createEmployee("lv-007");
        openBalances(employeeId);
        String requestId = apply(employeeId, "2024-06-01", "2024-06-03");
        mockMvc.perform(put("/api/v1/hr/leave/requests/" + requestId + "/decision")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/hr/leave/requests/" + requestId + "/cancel")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        String body = mockMvc.perform(get("/api/v1/hr/leave/balances")
                        .header("Authorization", bearer(token))
                        .param("employeeId", employeeId.toString())
                        .param("year", "2024"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).path("data").get(0).path("remaining").decimalValue())
                .isEqualByComparingTo("14.0");
    }

    @Test
    void refusesOverlappingLeaveForTheSameEmployee() throws Exception {
        UUID employeeId = createEmployee("lv-008");
        openBalances(employeeId);
        apply(employeeId, "2024-07-01", "2024-07-05");
        mockMvc.perform(post("/api/v1/hr/leave/requests")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leave(employeeId, "2024-07-03", "2024-07-08"))))
                .andExpect(status().isConflict());
    }

    @Test
    void refusesLeaveEndingBeforeItStarts() throws Exception {
        UUID employeeId = createEmployee("lv-009");
        mockMvc.perform(post("/api/v1/hr/leave/requests")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leave(employeeId, "2024-08-10", "2024-08-01"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void refusesToApproveLeaveWhenNoBalanceWasOpened() throws Exception {
        UUID employeeId = createEmployee("lv-010");
        String requestId = apply(employeeId, "2024-09-01", "2024-09-02");
        mockMvc.perform(put("/api/v1/hr/leave/requests/" + requestId + "/decision")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void refusesLeaveApprovalWithoutTheApprovalPermission() throws Exception {
        UUID employeeId = createEmployee("lv-011");
        openBalances(employeeId);
        String requestId = apply(employeeId, "2024-10-01", "2024-10-02");
        testData.user("teacherapprover", "Teacher Approver", "teacher.approver@example.test",
                Role.TEACHER, "TeacherPass123");
        String teacher = loginToken("teacherapprover", "TeacherPass123");
        mockMvc.perform(put("/api/v1/hr/leave/requests/" + requestId + "/decision")
                        .header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> leave(UUID employeeId, String from, String to) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeId", employeeId);
        payload.put("leaveTypeId", leaveTypeId);
        payload.put("startDate", from);
        payload.put("endDate", to);
        payload.put("reason", "Family");
        return payload;
    }

    private String apply(UUID employeeId, String from, String to) throws Exception {
        String body = mockMvc.perform(post("/api/v1/hr/leave/requests")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leave(employeeId, from, to))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private void openBalances(UUID employeeId) throws Exception {
        mockMvc.perform(post("/api/v1/hr/leave/balances/open")
                        .header("Authorization", bearer(token))
                        .param("employeeId", employeeId.toString())
                        .param("year", "2024"))
                .andExpect(status().isOk());
    }
}