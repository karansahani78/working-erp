package com.educationerp.hr;

import com.educationerp.accounting.ChartOfAccounts;
import com.educationerp.auth.role.Role;
import com.educationerp.institution.ModuleKey;
import com.educationerp.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Booking an approved payroll run into the ledger.
 *
 * <p>This is the one place the payroll module and the accounting module meet, so these tests
 * are about the seam rather than either side of it: that the entry balances, that it lands in
 * the period the salary belongs to, that the money lands on the accounts the institution
 * nominated, and above all that a month of salary cannot be booked twice.
 *
 * <p>The chart of accounts starts empty on every institution, which is why each test begins by
 * naming the accounts payroll posts to and saying which month they are paying.
 */
class PayrollLedgerPostingIntegrationTest extends IntegrationTest {

    private static final int YEAR = 2024;

    private static final String SALARY_EXPENSE = "6000";
    private static final String NET_PAYABLE = "2100";
    private static final String TAX_PAYABLE = "2200";
    private static final String LOAN_RECEIVABLE = "1300";
    private static final String DEDUCTIONS_PAYABLE = "2300";

    private String token;
    private String salaryStructureId;
    private String salaryStructureCode;

    /** Chart-of-accounts ids, keyed by the code the institution configured. */
    private final Map<String, String> accountIds = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        testData.enableModule(ModuleKey.HR);
        testData.enableModule(ModuleKey.PAYROLL);
        token = adminToken();

        createAccount(SALARY_EXPENSE, "Salaries and wages",
                ChartOfAccounts.AccountGroup.EXPENSE, ChartOfAccounts.AccountType.ADMIN_EXPENSE);
        createAccount(NET_PAYABLE, "Salary payable",
                ChartOfAccounts.AccountGroup.LIABILITY, ChartOfAccounts.AccountType.CURRENT_LIABILITY);
        createAccount(TAX_PAYABLE, "Tax payable",
                ChartOfAccounts.AccountGroup.LIABILITY, ChartOfAccounts.AccountType.CURRENT_LIABILITY);
        createAccount(LOAN_RECEIVABLE, "Staff loans receivable",
                ChartOfAccounts.AccountGroup.ASSET, ChartOfAccounts.AccountType.RECEIVABLE);
        createAccount(DEDUCTIONS_PAYABLE, "Deductions payable",
                ChartOfAccounts.AccountGroup.LIABILITY,
                ChartOfAccounts.AccountType.CURRENT_LIABILITY);

        Map<String, Object> brackets = new LinkedHashMap<>();
        brackets.put("rate", 10);
        mockMvc.perform(post("/api/v1/hr/payroll/tax-rules")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "SCALE-2024",
                                "name", "Flat scale 2024",
                                "brackets", List.of(brackets),
                                "effectiveFrom", "2024-01-01"))))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------- the entry

    @Test
    @DisplayName("an approved run is booked as one debit for the month's gross salary")
    void booksAnApprovedRunIntoTheLedger() throws Exception {
        openYear();
        String runId = approvedRun(1, createEmployee("ledger-001"));

        String body = postToLedger(runId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyPosted").value(false))
                .andReturn().getResponse().getContentAsString();
        JsonNode posted = objectMapper.readTree(body).path("data");

        assertThat(posted.path("grossSalary").decimalValue()).isEqualByComparingTo("44000.00");
        assertThat(posted.path("netSalary").decimalValue()).isEqualByComparingTo("37800.00");
        assertThat(posted.path("tax").decimalValue()).isEqualByComparingTo("4200.00");
        assertThat(posted.path("entryNumber").asText()).isNotBlank();

        // The run remembers its entry, so the second call below can find it again.
        String entryId = posted.path("journalEntryId").asText();
        String run = fetch("/api/v1/hr/payroll/runs/" + runId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(run).path("data").path("journalEntryId").asText())
                .isEqualTo(entryId);

        String entry = fetch("/api/v1/accounting/journal-entries/" + entryId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.sourceType").value("PAYROLL"))
                .andReturn().getResponse().getContentAsString();
        // The entry names the run it came from, so a payroll number can be traced both ways.
        assertThat(objectMapper.readTree(entry).path("data").path("sourceId").asText())
                .isEqualTo(runId);

        // Debit salary expense 44,000; credit net pay 37,800, tax 4,200 and provident fund 2,000.
        // Each leg is found by account rather than by position, since nothing promises an order.
        List<JsonNode> lines = entryLines(entry);
        assertThat(lines).hasSize(4);
        assertThat(lineOn(lines, SALARY_EXPENSE).path("debit").decimalValue())
                .isEqualByComparingTo("44000.00");
        assertThat(lineOn(lines, NET_PAYABLE).path("credit").decimalValue())
                .isEqualByComparingTo("37800.00");
        assertThat(lineOn(lines, TAX_PAYABLE).path("credit").decimalValue())
                .isEqualByComparingTo("4200.00");
        assertThat(lineOn(lines, DEDUCTIONS_PAYABLE).path("credit").decimalValue())
                .isEqualByComparingTo("2000.00");
        // The salary leg carries no credit, and the credit legs carry no debit.
        assertThat(lineOn(lines, SALARY_EXPENSE).path("credit").decimalValue()).isZero();
        assertThat(lineOn(lines, NET_PAYABLE).path("debit").decimalValue()).isZero();
    }

    @Test
    @DisplayName("a month of salary cannot be booked into the ledger twice")
    void refusesToBookTheSameRunTwice() throws Exception {
        openYear();
        String runId = approvedRun(1, createEmployee("ledger-002"));
        String first = postToLedger(runId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String second = postToLedger(runId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyPosted").value(true))
                .andReturn().getResponse().getContentAsString();

        // The second call reports the first entry instead of writing a second one.
        assertThat(objectMapper.readTree(second).path("data").path("journalEntryId").asText())
                .isEqualTo(objectMapper.readTree(first).path("data").path("journalEntryId").asText());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from journal_entries where source_type = 'PAYROLL'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("a run that has not been approved cannot be booked")
    void refusesToBookARunThatIsNotApproved() throws Exception {
        createEmployee("ledger-003");
        String runId = processPayroll(1);
        configureLedgerAccounts();

        postToLedger(runId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("salary is booked into the period for the month it was paid in")
    void booksIntoThePeriodForThePayrollMonth() throws Exception {
        String runId = approvedRun(3, createEmployee("ledger-004"));

        // March payroll with only a January period open: there is nowhere to post it.
        String refused = postToLedger(runId).andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();
        assertThat(refused).contains("2024-03");

        openYear();
        postToLedger(runId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entryDate").value("2024-03-31"));
    }

    @Test
    @DisplayName("a closed period is refused rather than quietly reopened")
    void refusesToBookIntoAClosedPeriod() throws Exception {
        String runId = approvedRun(1, createEmployee("ledger-005"));
        String periodId = openPeriod("2024-01-01", "2024-01-31");
        mockMvc.perform(post("/api/v1/accounting/periods/" + periodId + "/close")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        postToLedger(runId).andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("loan recovery and other deductions go to their own accounts")
    void booksLoanRecoveryAndDeductionsSeparately() throws Exception {
        openYear();
        UUID employeeId = createEmployee("ledger-006");
        grantLoan(employeeId, "5000.00");
        String runId = approvedRun(1, employeeId);

        String body = postToLedger(runId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<JsonNode> lines = journalLines(body);

        // A 5,000 advance over five monthly instalments recovers 1,000 this month, and the
        // 5% provident fund withholds 2,000. Each goes to its own account.
        assertThat(lineOn(lines, DEDUCTIONS_PAYABLE).path("credit").decimalValue())
                .isEqualByComparingTo("2000.00");
        assertThat(lineOn(lines, LOAN_RECEIVABLE).path("credit").decimalValue())
                .isEqualByComparingTo("1000.00");
        assertThat(lineOn(lines, SALARY_EXPENSE).path("debit").decimalValue())
                .isEqualByComparingTo("44000.00");
    }

    @Test
    @DisplayName("posting refuses to guess when the ledger accounts are not configured")
    void refusesToBookWithoutConfiguredLedgerAccounts() throws Exception {
        openYear();
        String runId = approvedRun(1, createEmployee("ledger-007"));
        jdbcTemplate.update("update module_settings set settings = null where module_key = 'PAYROLL'");

        String refused = postToLedger(runId).andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();
        assertThat(refused).contains("salaryExpense");
    }

    @Test
    @DisplayName("a ledger account that is not postable is refused at configuration time")
    void refusesToConfigureAHeadingAsALedgerAccount() throws Exception {
        String heading = createAccount("6100", "Operating expenses",
                ChartOfAccounts.AccountGroup.EXPENSE, ChartOfAccounts.AccountType.ADMIN_EXPENSE);
        jdbcTemplate.update("update chart_of_accounts set is_postable = false where code = ?", heading);

        Map<String, String> accounts = new LinkedHashMap<>();
        accounts.put("salaryExpense", heading);
        accounts.put("netPayable", NET_PAYABLE);
        accounts.put("taxPayable", TAX_PAYABLE);
        String refused = configure(accounts).andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();
        assertThat(refused).contains("heading");
    }

    @Test
    @DisplayName("a ledger account that does not exist is refused at configuration time")
    void refusesToConfigureAnAccountOutsideTheChart() throws Exception {
        Map<String, String> accounts = new LinkedHashMap<>();
        accounts.put("salaryExpense", "9999");
        accounts.put("netPayable", NET_PAYABLE);
        accounts.put("taxPayable", TAX_PAYABLE);
        String refused = configure(accounts).andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();
        assertThat(refused).contains("9999");
    }

    @Test
    @DisplayName("only somebody who can post entries may book payroll")
    void refusesToBookWithoutTheAccountingPermission() throws Exception {
        String runId = approvedRun(1, createEmployee("ledger-008"));
        testData.user("payrollclerk", "Payroll Clerk", "payroll.clerk@example.test",
                Role.HR, "ClerkPass123");
        String clerk = loginToken("payrollclerk", "ClerkPass123");

        // The HR role can approve payroll, but posting to the ledger is not its to do.
        mockMvc.perform(post("/api/v1/hr/payroll/runs/" + runId + "/post-to-ledger")
                        .header("Authorization", bearer(clerk)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the configured accounts can be read back with their names")
    void readsBackTheConfiguredLedgerAccounts() throws Exception {
        configureLedgerAccounts().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salaryExpense").value(SALARY_EXPENSE))
                .andExpect(jsonPath("$.data.salaryExpenseName").value("Salaries and wages"))
                .andExpect(jsonPath("$.data.loanReceivable").value(LOAN_RECEIVABLE))
                .andExpect(jsonPath("$.data.loanReceivableName").value("Staff loans receivable"));

        fetch("/api/v1/hr/payroll/ledger-accounts").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deductionsPayable").value(DEDUCTIONS_PAYABLE));
    }

    // --------------------------------------------------------------- helpers

    private ResultActions fetch(String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", bearer(token)));
    }

    private ResultActions postToLedger(String runId) throws Exception {
        return mockMvc.perform(post("/api/v1/hr/payroll/runs/" + runId + "/post-to-ledger")
                .header("Authorization", bearer(token)));
    }

    private ResultActions configure(Map<String, String> accounts) throws Exception {
        return mockMvc.perform(put("/api/v1/hr/payroll/ledger-accounts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(accounts)));
    }

    private ResultActions configureLedgerAccounts() throws Exception {
        Map<String, String> accounts = new LinkedHashMap<>();
        accounts.put("salaryExpense", SALARY_EXPENSE);
        accounts.put("netPayable", NET_PAYABLE);
        accounts.put("taxPayable", TAX_PAYABLE);
        accounts.put("loanReceivable", LOAN_RECEIVABLE);
        accounts.put("deductionsPayable", DEDUCTIONS_PAYABLE);
        return configure(accounts);
    }

    private List<JsonNode> journalLines(String postingBody) throws Exception {
        String entryId = objectMapper.readTree(postingBody).path("data").path("journalEntryId").asText();
        String entry = fetch("/api/v1/accounting/journal-entries/" + entryId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return entryLines(entry);
    }

    private List<JsonNode> entryLines(String entry) throws Exception {
        List<JsonNode> lines = new ArrayList<>();
        objectMapper.readTree(entry).path("data").path("lines").forEach(lines::add);
        return lines;
    }

    private JsonNode lineOn(List<JsonNode> lines, String accountCode) {
        String accountId = accountIds.get(accountCode);
        for (JsonNode line : lines) {
            if (line.path("accountId").asText().equals(accountId)) {
                return line;
            }
        }
        throw new AssertionError("No journal line for account " + accountCode + " in " + lines);
    }

    /** A processed, approved run with its ledger accounts configured. */
    private String approvedRun(int month, UUID employeeId) throws Exception {
        String runId = processPayroll(month);
        mockMvc.perform(post("/api/v1/hr/payroll/runs/" + runId + "/approve")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        configureLedgerAccounts().andExpect(status().isOk());
        return runId;
    }

    private String createAccount(String code, String name, ChartOfAccounts.AccountGroup group,
            ChartOfAccounts.AccountType type) throws Exception {
        String body = mockMvc.perform(post("/api/v1/accounting/accounts")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "name", name,
                                "accountGroup", group.name(),
                                "accountType", type.name(),
                                "postable", true))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).path("data").path("id").asText();
        accountIds.put(code, id);
        return code;
    }

    /** A fiscal year and an open period covering every month payroll might be booked into. */
    private void openYear() throws Exception {
        openPeriod("2024-01-01", "2024-12-31");
    }

    private String openPeriod(String start, String end) throws Exception {
        // The fiscal year has to enclose the payroll months, so it is drawn around the periods.
        String code = "FY" + start.replace("-", "");
        String fiscalYear = mockMvc.perform(post("/api/v1/accounting/fiscal-years")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "name", "Fiscal year " + code,
                                "startDate", start,
                                "endDate", end))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fiscalYearId = objectMapper.readTree(fiscalYear).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/accounting/fiscal-years/" + fiscalYearId + "/open")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        String period = mockMvc.perform(post("/api/v1/accounting/periods")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fiscalYearId", fiscalYearId,
                                "name", "Period " + start,
                                "startDate", start,
                                "endDate", end))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(period).path("data").path("id").asText();
    }

    /** Basic 40,000, a 10% house allowance, a 5% provident fund and 400 an hour for overtime. */
    private String publishSalaryStructure() throws Exception {
        Map<String, Object> allowance = new LinkedHashMap<>();
        allowance.put("name", "House allowance");
        allowance.put("componentType", "ALLOWANCE");
        allowance.put("valueType", "PERCENTAGE");
        allowance.put("value", 10);
        allowance.put("taxable", true);
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
        payload.put("components", List.of(allowance, deduction));

        String body = mockMvc.perform(post("/api/v1/hr/payroll/salary-structures")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/hr/payroll/salary-structures/" + id + "/publish")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        return id;
    }

    private UUID createEmployee(String code) throws Exception {
        if (salaryStructureId == null) {
            salaryStructureId = publishSalaryStructure();
            salaryStructureCode = salaryStructureId;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeCode", code);
        payload.put("firstName", "Staff");
        payload.put("joinDate", "2023-04-01");
        payload.put("employmentType", "FULL_TIME");
        payload.put("salaryStructureId", salaryStructureCode);
        String body = mockMvc.perform(post("/api/v1/hr/employees")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private void grantLoan(UUID employeeId, String amount) throws Exception {
        Map<String, Object> loan = new LinkedHashMap<>();
        loan.put("employeeId", employeeId);
        loan.put("loanType", "ADVANCE");
        loan.put("principal", new BigDecimal(amount));
        loan.put("installmentAmount", new BigDecimal(amount).divide(BigDecimal.valueOf(5)));
        loan.put("grantedOn", "2024-01-05");
        mockMvc.perform(post("/api/v1/hr/payroll/loans")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loan)))
                .andExpect(status().isOk());
    }

    private String processPayroll(int month) throws Exception {
        String body = mockMvc.perform(post("/api/v1/hr/payroll/runs")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "periodYear", YEAR,
                                "periodMonth", month))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }
}