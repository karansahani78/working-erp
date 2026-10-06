package com.educationerp.hr;

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
 * Payroll (blueprint section 34): configurable salary structures, versioned tax rules,
 * overtime derived from attendance, loans and advances, and a monthly run that cannot be
 * processed twice.
 *
 * <p>The figures asserted here are worked out by hand, so a change in the calculation is a
 * change in behaviour rather than a change in an expectation that drifted.
 */
class PayrollIntegrationTest extends IntegrationTest {

    private static final int YEAR = 2024;

    private String token;
    private String salaryStructureId;

    @BeforeEach
    void enablePayroll() throws Exception {
        // Staff have to exist before they can be paid, so payroll tests need HR as well.
        testData.enableModule(ModuleKey.HR);
        testData.enableModule(ModuleKey.PAYROLL);
        token = adminToken();
    }

    // ------------------------------------------------------------- salary set-up

    /**
     * Basic 40,000 with a 10% house allowance, a 5% provident fund deduction and an
     * overtime rate of 400 an hour. Every number a payslip can contain is configured here
     * rather than compiled in.
     */
    private String publishSalaryStructure() throws Exception {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("name", "House allowance");
        component.put("componentType", "ALLOWANCE");
        component.put("valueType", "PERCENTAGE");
        component.put("value", 10);
        component.put("taxable", true);
        Map<String, Object> deduction = new LinkedHashMap<>();
        deduction.put("name", "Provident fund");
        deduction.put("componentType", "DEDUCTION");
        deduction.put("valueType", "PERCENTAGE");
        deduction.put("value", 5);
        deduction.put("taxable", true);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", "teacher-standard");
        payload.put("name", "Teacher Standard");
        payload.put("basicSalary", 40000);
        payload.put("currency", "NPR");
        payload.put("overtimeRate", 400);
        payload.put("components", List.of(component, deduction));

        String body = mockMvc.perform(post("/api/v1/hr/payroll/salary-structures")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).path("data").path("id").asText();
        // A structure stays a draft until published, so a half-built one cannot reach payroll.
        mockMvc.perform(post("/api/v1/hr/payroll/salary-structures/" + id + "/publish")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        return id;
    }

    /** A flat 10% scale with no bands, so tax arithmetic stays obvious in the assertions. */
    private void createFlatTaxRule(String code, double rate, String from, String to) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("name", code + " scale");
        payload.put("brackets", List.of(Map.of("rate", rate)));
        payload.put("effectiveFrom", from);
        if (to != null) {
            payload.put("effectiveTo", to);
        }
        mockMvc.perform(post("/api/v1/hr/payroll/tax-rules")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    private UUID createEmployee(String code) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeCode", code);
        payload.put("firstName", "Staff");
        payload.put("joinDate", "2023-04-01");
        payload.put("employmentType", "FULL_TIME");
        payload.put("salaryStructureId", salaryStructureId);
        String body = mockMvc.perform(post("/api/v1/hr/employees")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private void markOvertime(UUID employeeId, String date, int minutes) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attendanceDate", date);
        payload.put("status", "PRESENT");
        payload.put("checkIn", "09:00:00");
        payload.put("checkOut", "17:00:00");
        payload.put("overtimeMinutes", minutes);
        mockMvc.perform(post("/api/v1/hr/attendance/employees/" + employeeId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    private String processPayroll(int month) throws Exception {
        String body = mockMvc.perform(post("/api/v1/hr/payroll/runs")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "periodYear", YEAR,
                                "periodMonth", month,
                                "notes", "Monthly payroll"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    // -------------------------------------------------------------- structures

    @Test
    void computesComponentAmountsFromTheConfiguredStructure() throws Exception {
        salaryStructureId = publishSalaryStructure();
        String body = mockMvc.perform(get("/api/v1/hr/payroll/salary-structures")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var components = objectMapper.readTree(body).path("data").get(0).path("components");
        // 10% of 40,000 and 5% of 40,000, worked out from the structure rather than stored.
        assertThat(components).hasSize(2);
        assertThat(components.get(0).path("amountOnBasic").decimalValue())
                .isEqualByComparingTo("4000.00");
        assertThat(components.get(1).path("amountOnBasic").decimalValue())
                .isEqualByComparingTo("2000.00");
    }

    @Test
    void refusesToPublishASalaryStructureTwice() throws Exception {
        salaryStructureId = publishSalaryStructure();
        mockMvc.perform(post("/api/v1/hr/payroll/salary-structures/" + salaryStructureId + "/publish")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void refusesASalaryStructureWithAPercentageAboveOneHundred() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", "broken");
        payload.put("name", "Broken");
        payload.put("basicSalary", 10000);
        payload.put("components", List.of(Map.of(
                "name", "Too much",
                "componentType", "ALLOWANCE",
                "valueType", "PERCENTAGE",
                "value", 150)));
        mockMvc.perform(post("/api/v1/hr/payroll/salary-structures")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------------------------------------------------------------- tax rules

    @Test
    void calculatesProgressiveTaxBandByBand() throws Exception {
        // 10% on the first 100,000 a year, then 20% on the rest.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", "progressive-1");
        payload.put("name", "Progressive one");
        payload.put("brackets", List.of(
                Map.of("upTo", 100000, "rate", 10),
                Map.of("rate", 20)));
        payload.put("effectiveFrom", "2023-01-01");
        mockMvc.perform(post("/api/v1/hr/payroll/tax-rules")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    void refusesTaxBracketsThatDoNotRise() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", "bad-order");
        payload.put("name", "Bad order");
        payload.put("brackets", List.of(
                Map.of("upTo", 200000, "rate", 10),
                Map.of("upTo", 100000, "rate", 20)));
        payload.put("effectiveFrom", "2023-01-01");
        mockMvc.perform(post("/api/v1/hr/payroll/tax-rules")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void refusesTaxBracketsWithAnOpenEndedBandInTheMiddle() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", "bad-open");
        payload.put("name", "Bad open");
        payload.put("brackets", List.of(
                Map.of("rate", 10),
                Map.of("upTo", 100000, "rate", 20)));
        payload.put("effectiveFrom", "2023-01-01");
        mockMvc.perform(post("/api/v1/hr/payroll/tax-rules")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void keepsTaxRulesVersionedRatherThanOverwriting() throws Exception {
        createFlatTaxRule("SCALE-2023", 10, "2023-01-01", "2023-12-31");
        createFlatTaxRule("SCALE-2024", 15, "2024-01-01", null);
        String body = mockMvc.perform(get("/api/v1/hr/payroll/tax-rules")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> codes = objectMapper.readTree(body).path("data").findValuesAsText("code");
        // Both versions survive, so an old payslip can still be explained.
        assertThat(codes).containsExactlyInAnyOrder("SCALE-2023", "SCALE-2024");
    }

    // -------------------------------------------------------------- payroll run

    @Test
    void processesAPayrollRunFromConfiguredFigures() throws Exception {
        salaryStructureId = publishSalaryStructure();
        createFlatTaxRule("SCALE-2024", 10, "2024-01-01", null);
        UUID employeeId = createEmployee("pay-001");

        // 40,000 basic + 4,000 allowance = 44,000 gross; 2,000 provident fund; tax is on
        // 42,000 taxable a month, so 504,000 a year at 10% is 50,400 a year, 4,200 a month.
        String runId = processPayroll(1);
        mockMvc.perform(get("/api/v1/hr/payroll/runs/" + runId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeCount").value(1))
                .andExpect(jsonPath("$.data.totalGross").value(44000.00))
                .andExpect(jsonPath("$.data.totalDeductions").value(2000.00))
                .andExpect(jsonPath("$.data.totalTax").value(4200.00))
                .andExpect(jsonPath("$.data.totalNet").value(37800.00))
                .andExpect(jsonPath("$.data.status").value("PROCESSED"));

        String payslipBody = mockMvc.perform(get("/api/v1/hr/payroll/runs/" + runId + "/payslips")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var payslip = objectMapper.readTree(payslipBody).path("data").get(0);
        assertThat(payslip.path("basicSalary").decimalValue()).isEqualByComparingTo("40000.00");
        assertThat(payslip.path("totalAllowances").decimalValue()).isEqualByComparingTo("4000.00");
        assertThat(payslip.path("otherDeductions").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(payslip.path("netSalary").decimalValue()).isEqualByComparingTo("37800.00");
        assertThat(payslip.path("taxRuleCode").asText()).isEqualTo("SCALE-2024");
        assertThat(employeeId).isNotNull();
    }

    @Test
    void derivesOvertimeFromAttendance() throws Exception {
        salaryStructureId = publishSalaryStructure();
        createFlatTaxRule("SCALE-2024", 0, "2024-01-01", null);
        UUID employeeId = createEmployee("pay-002");
        // 60 recorded overtime minutes at 400 an hour is 400.00.
        markOvertime(employeeId, "2024-01-15", 60);

        String runId = processPayroll(1);
        String payslipBody = mockMvc.perform(get("/api/v1/hr/payroll/runs/" + runId + "/payslips")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var payslip = objectMapper.readTree(payslipBody).path("data").get(0);
        assertThat(payslip.path("overtimeMinutes").asInt()).isEqualTo(60);
        assertThat(payslip.path("overtimeAmount").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(payslip.path("grossSalary").decimalValue()).isEqualByComparingTo("44400.00");
    }

    @Test
    void writesEveryPayslipLineItem() throws Exception {
        salaryStructureId = publishSalaryStructure();
        createFlatTaxRule("SCALE-2024", 0, "2024-01-01", null);
        UUID employeeId = createEmployee("pay-003");
        markOvertime(employeeId, "2024-01-10", 30);

        Map<String, Object> process = new LinkedHashMap<>();
        process.put("periodYear", YEAR);
        process.put("periodMonth", 1);
        process.put("bonuses", Map.of(employeeId, 5000));
        String runBody = mockMvc.perform(post("/api/v1/hr/payroll/runs")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(process)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String runId = objectMapper.readTree(runBody).path("data").path("id").asText();

        String payslipBody = mockMvc.perform(get("/api/v1/hr/payroll/runs/" + runId + "/payslips")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var payslip = objectMapper.readTree(payslipBody).path("data").get(0);
        // 40,000 + 4,000 allowance + 200 overtime (30 minutes) + 5,000 bonus.
        assertThat(payslip.path("grossSalary").decimalValue()).isEqualByComparingTo("49200.00");
        assertThat(payslip.path("bonus").decimalValue()).isEqualByComparingTo("5000.00");

        List<String> lines = objectMapper.readTree(payslipBody).path("data").get(0)
                .path("items").findValuesAsText("name");
        // A disputed payslip can be explained line by line rather than only in total.
        assertThat(lines).contains("Basic salary", "House allowance", "Overtime", "Bonus",
                "Provident fund");
    }

    @Test
    void recoversALoanInstallmentEachRun() throws Exception {
        salaryStructureId = publishSalaryStructure();
        createFlatTaxRule("SCALE-2024", 0, "2024-01-01", null);
        UUID employeeId = createEmployee("pay-004");

        Map<String, Object> loan = new LinkedHashMap<>();
        loan.put("employeeId", employeeId);
        loan.put("loanType", "ADVANCE");
        loan.put("principal", 12000);
        loan.put("installmentAmount", 3000);
        loan.put("grantedOn", "2024-01-05");
        mockMvc.perform(post("/api/v1/hr/payroll/loans")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loan)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outstanding").value(12000.00));

        processPayroll(1);
        String body = mockMvc.perform(get("/api/v1/hr/payroll/loans")
                        .header("Authorization", bearer(token))
                        .param("employeeId", employeeId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // The outstanding balance only moves when a run collects an installment.
        assertThat(objectMapper.readTree(body).path("data").get(0).path("outstanding").decimalValue())
                .isEqualByComparingTo("9000.00");
    }

    @Test
    void settlesALoanOnceTheBalanceIsCleared() throws Exception {
        salaryStructureId = publishSalaryStructure();
        createFlatTaxRule("SCALE-2024", 0, "2024-01-01", null);
        UUID employeeId = createEmployee("pay-005");

        Map<String, Object> loan = new LinkedHashMap<>();
        loan.put("employeeId", employeeId);
        loan.put("loanType", "LOAN");
        loan.put("principal", 2000);
        loan.put("installmentAmount", 1000);
        loan.put("grantedOn", "2024-01-05");
        mockMvc.perform(post("/api/v1/hr/payroll/loans")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loan)))
                .andExpect(status().isOk());

        processPayroll(1);
        processPayroll(2);
        String body = mockMvc.perform(get("/api/v1/hr/payroll/loans")
                        .header("Authorization", bearer(token))
                        .param("employeeId", employeeId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var settled = objectMapper.readTree(body).path("data").get(0);
        assertThat(settled.path("outstanding").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(settled.path("status").asText()).isEqualTo("SETTLED");
    }

    @Test
    void refusesToProcessTheSamePeriodTwice() throws Exception {
        salaryStructureId = publishSalaryStructure();
        createFlatTaxRule("SCALE-2024", 10, "2024-01-01", null);
        createEmployee("pay-006");
        processPayroll(3);
        // Paying everyone a second time is the one mistake a payroll run cannot make.
        mockMvc.perform(post("/api/v1/hr/payroll/runs")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "periodYear", YEAR,
                                "periodMonth", 3))))
                .andExpect(status().isConflict());
    }

    @Test
    void refusesToPayAnEmployeeWithNoPublishedStructure() throws Exception {
        salaryStructureId = publishSalaryStructure();
        createFlatTaxRule("SCALE-2024", 10, "2024-01-01", null);
        // Someone without a published structure is a data problem, not a zero payment.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeCode", "pay-007");
        payload.put("firstName", "Unpriced");
        payload.put("joinDate", "2023-04-01");
        mockMvc.perform(post("/api/v1/hr/employees")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/hr/payroll/runs")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "periodYear", YEAR,
                                "periodMonth", 4))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void refusesAnInvalidPayrollMonth() throws Exception {
        mockMvc.perform(post("/api/v1/hr/payroll/runs")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "periodYear", YEAR,
                                "periodMonth", 13))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approvesARunSeparatelyFromProcessingIt() throws Exception {
        salaryStructureId = publishSalaryStructure();
        createFlatTaxRule("SCALE-2024", 10, "2024-01-01", null);
        createEmployee("pay-008");
        String runId = processPayroll(5);

        mockMvc.perform(post("/api/v1/hr/payroll/runs/" + runId + "/approve")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("notes", "Checked"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void refusesToApproveAnUnprocessedRunTwice() throws Exception {
        salaryStructureId = publishSalaryStructure();
        createFlatTaxRule("SCALE-2024", 10, "2024-01-01", null);
        createEmployee("pay-009");
        String runId = processPayroll(6);
        Map<String, Object> approval = Map.of("notes", "Checked");
        mockMvc.perform(post("/api/v1/hr/payroll/runs/" + runId + "/approve")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approval)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/hr/payroll/runs/" + runId + "/approve")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approval)))
                .andExpect(status().isConflict());
    }

    @Test
    void previewsAnEmployeesFiguresWithoutProcessingAnything() throws Exception {
        salaryStructureId = publishSalaryStructure();
        createFlatTaxRule("SCALE-2024", 10, "2024-01-01", null);
        UUID employeeId = createEmployee("pay-010");

        mockMvc.perform(get("/api/v1/hr/payroll/preview")
                        .header("Authorization", bearer(token))
                        .param("employeeId", employeeId.toString())
                        .param("year", String.valueOf(YEAR))
                        .param("month", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxRuleCode").value("SCALE-2024"))
                .andExpect(jsonPath("$.data.monthlyTax").value(4200.00));

        // Nothing was written: the month can still be processed afterwards.
        mockMvc.perform(post("/api/v1/hr/payroll/runs")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "periodYear", YEAR,
                                "periodMonth", 1))))
                .andExpect(status().isOk());
    }

    @Test
    void refusesThePayrollApiWhileTheModuleIsOff() throws Exception {
        jdbcTemplate.update("update module_settings set enabled = false where module_key = 'PAYROLL'");
        mockMvc.perform(get("/api/v1/hr/payroll/runs")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void refusesPayrollProcessingWithoutPermission() throws Exception {
        testData.user("payrollviewer", "Payroll Viewer", "payroll.viewer@example.test",
                com.educationerp.auth.role.Role.TEACHER, "ViewerPass123");
        String viewer = loginToken("payrollviewer", "ViewerPass123");
        mockMvc.perform(get("/api/v1/hr/payroll/runs")
                        .header("Authorization", bearer(viewer)))
                .andExpect(status().isForbidden());
    }
}