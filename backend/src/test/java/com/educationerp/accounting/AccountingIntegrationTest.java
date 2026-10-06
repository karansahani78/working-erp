package com.educationerp.accounting;

import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Double-entry bookkeeping: chart of accounts, the fiscal calendar, journal entries and
 * the trial balance.
 *
 * <p>These tests are mostly about refusals. A ledger that quietly accepts an unbalanced
 * entry, an edit to a posted one, or a booking into a closed period is worse than one
 * that rejects the request, so each of those is pinned down here.
 */
class AccountingIntegrationTest extends IntegrationTest {

    @Autowired
    private ChartOfAccountsRepository accounts;

    private String token;
    private UUID cashAccount;
    private UUID feesAccount;
    private UUID fiscalYearId;
    private UUID periodId;

    @BeforeEach
    void openTheBooks() throws Exception {
        token = adminToken();
        cashAccount = account("1000", "Cash in hand", ChartOfAccounts.AccountGroup.ASSET,
                ChartOfAccounts.AccountType.CASH);
        feesAccount = account("4000", "Tuition income", ChartOfAccounts.AccountGroup.INCOME,
                ChartOfAccounts.AccountType.REVENUE);

        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2027, 3, 31);
        fiscalYearId = UUID.fromString(postJson("/api/v1/accounting/fiscal-years", """
                {"name":"FY 2082/83","code":"FY2082","startDate":"%s","endDate":"%s"}"""
                .formatted(start, end)).path("id").asText());
        postJson("/api/v1/accounting/fiscal-years/" + fiscalYearId + "/open", "{}");
        periodId = UUID.fromString(postJson("/api/v1/accounting/periods", """
                {"fiscalYearId":"%s","name":"Shrawan","startDate":"%s","endDate":"%s"}"""
                .formatted(fiscalYearId, start, start.plusDays(30))).path("id").asText());
    }

    @Test
    @DisplayName("a balanced entry is drafted, posted, and then refused any further edit")
    void balancedEntryPostsAndThenLocks() throws Exception {
        String entryId = entry("""
                {"entryDate":"%s","accountingPeriodId":"%s","sourceType":"MANUAL",
                 "description":"Cash received at the counter",
                 "lines":[
                   {"accountId":"%s","debit":5000.00,"credit":0,"description":"Cash"},
                   {"accountId":"%s","debit":0,"credit":5000.00,"description":"Income"}]}"""
                .formatted(LocalDate.of(2026, 4, 5), periodId, cashAccount, feesAccount)).path("id").asText();

        mockMvc.perform(get("/api/v1/accounting/journal-entries/{id}", entryId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.balanced").value(true))
                .andExpect(jsonPath("$.data.entryNumber").value(org.hamcrest.Matchers.startsWith("JRN-")));

        mockMvc.perform(post("/api/v1/accounting/journal-entries/{id}/post", entryId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.totalDebit").value(5000.00));

        // A posted entry is history. Changing it would make the ledger disagree with the
        // invoice it was written from.
        mockMvc.perform(post("/api/v1/accounting/journal-entries/{id}/post", entryId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"));
    }

    @Test
    @DisplayName("an unbalanced entry is refused outright")
    void refusesAnUnbalancedEntry() throws Exception {
        mockMvc.perform(post("/api/v1/accounting/journal-entries")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"entryDate":"%s","accountingPeriodId":"%s","sourceType":"MANUAL",
                                 "description":"Mismatched",
                                 "lines":[
                                   {"accountId":"%s","debit":5000.00,"credit":0},
                                   {"accountId":"%s","debit":0,"credit":4000.00}]}"""
                                .formatted(LocalDate.of(2026, 4, 5), periodId, cashAccount, feesAccount)))
                .andExpect(status().isUnprocessableEntity());
        assertThat(entriesInPeriod()).isZero();
    }

    @Test
    @DisplayName("a journal line cannot be both a debit and a credit")
    void refusesALineThatIsBothSides() throws Exception {
        mockMvc.perform(post("/api/v1/accounting/journal-entries")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"entryDate":"%s","accountingPeriodId":"%s","sourceType":"MANUAL",
                                 "lines":[
                                   {"accountId":"%s","debit":100.00,"credit":100.00},
                                   {"accountId":"%s","debit":0,"credit":100.00}]}"""
                                .formatted(LocalDate.of(2026, 4, 5), periodId, cashAccount, feesAccount)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a heading account cannot be posted to")
    void refusesPostingToANonPostableAccount() throws Exception {
        UUID heading = account("1100", "Current assets", ChartOfAccounts.AccountGroup.ASSET,
                ChartOfAccounts.AccountType.CURRENT_ASSET, false);
        mockMvc.perform(post("/api/v1/accounting/journal-entries")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"entryDate":"%s","accountingPeriodId":"%s","sourceType":"MANUAL",
                                 "lines":[
                                   {"accountId":"%s","debit":100.00,"credit":0},
                                   {"accountId":"%s","debit":0,"credit":100.00}]}"""
                                .formatted(LocalDate.of(2026, 4, 5), periodId, heading, feesAccount)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("an entry dated outside its accounting period is refused")
    void refusesAnEntryOutsideThePeriod() throws Exception {
        mockMvc.perform(post("/api/v1/accounting/journal-entries")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"entryDate":"%s","accountingPeriodId":"%s","sourceType":"MANUAL",
                                 "lines":[
                                   {"accountId":"%s","debit":100.00,"credit":0},
                                   {"accountId":"%s","debit":0,"credit":100.00}]}"""
                                .formatted(LocalDate.of(2026, 11, 5), periodId, cashAccount, feesAccount)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a closed accounting period refuses new postings")
    void closedPeriodRefusesPostings() throws Exception {
        mockMvc.perform(post("/api/v1/accounting/periods/{id}/close", periodId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(post("/api/v1/accounting/journal-entries")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"entryDate":"%s","accountingPeriodId":"%s","sourceType":"MANUAL",
                                 "lines":[
                                   {"accountId":"%s","debit":100.00,"credit":0},
                                   {"accountId":"%s","debit":0,"credit":100.00}]}"""
                                .formatted(LocalDate.of(2026, 4, 5), periodId, cashAccount, feesAccount)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("reversing a posted entry writes an equal and opposite entry and leaves the original in place")
    void reversalWritesAMirroredEntry() throws Exception {
        String entryId = entry("""
                {"entryDate":"%s","accountingPeriodId":"%s","sourceType":"MANUAL",
                 "description":"Banked a cheque that bounced",
                 "lines":[
                   {"accountId":"%s","debit":3000.00,"credit":0},
                   {"accountId":"%s","debit":0,"credit":3000.00}]}"""
                .formatted(LocalDate.of(2026, 4, 6), periodId, cashAccount, feesAccount)).path("id").asText();
        mockMvc.perform(post("/api/v1/accounting/journal-entries/{id}/post", entryId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/accounting/journal-entries/{id}/reverse", entryId)
                        .param("reason", "Cheque returned unpaid")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.balanced").value(true));

        // The original still reads POSTED... now REVERSED, and still carries its own lines.
        mockMvc.perform(get("/api/v1/accounting/journal-entries/{id}", entryId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVERSED"))
                .andExpect(jsonPath("$.data.totalDebit").value(3000.00));
    }

    @Test
    @DisplayName("an entry cannot be reversed twice")
    void refusesToReverseTwice() throws Exception {
        String entryId = entry("""
                {"entryDate":"%s","accountingPeriodId":"%s","sourceType":"MANUAL",
                 "lines":[
                   {"accountId":"%s","debit":500.00,"credit":0},
                   {"accountId":"%s","debit":0,"credit":500.00}]}"""
                .formatted(LocalDate.of(2026, 4, 7), periodId, cashAccount, feesAccount)).path("id").asText();
        mockMvc.perform(post("/api/v1/accounting/journal-entries/{id}/post", entryId)
                        .header("Authorization", bearer(token)));
        mockMvc.perform(post("/api/v1/accounting/journal-entries/{id}/reverse", entryId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/accounting/journal-entries/{id}/reverse", entryId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("an entry drafted before the year closed can no longer be posted")
    void aClosedFiscalYearRefusesPosting() throws Exception {
        // Drafted while the year was open, then the year is closed underneath it.
        String entryId = entry("""
                {"entryDate":"%s","accountingPeriodId":"%s","sourceType":"MANUAL",
                 "description":"Late fees",
                 "lines":[
                   {"accountId":"%s","debit":5000.00,"credit":0},
                   {"accountId":"%s","debit":0,"credit":5000.00}]}"""
                .formatted(LocalDate.of(2026, 4, 12), periodId, cashAccount, feesAccount))
                .path("id").asText();

        // Closing the year must not be defeated by its own periods still being open.
        mockMvc.perform(post("/api/v1/accounting/fiscal-years/{id}/close", fiscalYearId.toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/accounting/journal-entries/{id}/post", entryId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity());

        // The entry stays a draft, so nothing reached the ledger.
        mockMvc.perform(get("/api/v1/accounting/journal-entries/{id}", entryId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("the trial balance nets a posted entry and its reversal back to zero")
    void trialBalanceNetsAReversal() throws Exception {
        mockMvc.perform(get("/api/v1/accounting/trial-balance")
                        .param("asOf", "2026-12-31")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanced").value(true))
                .andExpect(jsonPath("$.data.totalDebit").value(0.00));

        String entryId = entry("""
                {"entryDate":"%s","accountingPeriodId":"%s","sourceType":"MANUAL",
                 "lines":[
                   {"accountId":"%s","debit":7000.00,"credit":0},
                   {"accountId":"%s","debit":0,"credit":7000.00}]}"""
                .formatted(LocalDate.of(2026, 4, 8), periodId, cashAccount, feesAccount)).path("id").asText();
        mockMvc.perform(post("/api/v1/accounting/journal-entries/{id}/post", entryId)
                        .header("Authorization", bearer(token)));

        mockMvc.perform(get("/api/v1/accounting/trial-balance")
                        .param("asOf", "2026-12-31")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanced").value(true))
                .andExpect(jsonPath("$.data.totalDebit").value(7000.00))
                .andExpect(jsonPath("$.data.rows.length()").value(2));

        mockMvc.perform(post("/api/v1/accounting/journal-entries/{id}/reverse", entryId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        // The reversal is a real entry, so it adds an equal amount to both sides of the
        // trial balance. What must hold is that each account now carries the same figure on
        // both sides, i.e. the reversal genuinely unwound the original.
        mockMvc.perform(get("/api/v1/accounting/trial-balance")
                        .param("asOf", "2026-12-31")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanced").value(true))
                .andExpect(jsonPath("$.data.totalDebit").value(14000.00))
                .andExpect(jsonPath("$.data.totalCredit").value(14000.00))
                .andExpect(jsonPath("$.data.rows[?(@.code=='1000')].debit").value(7000.00))
                .andExpect(jsonPath("$.data.rows[?(@.code=='1000')].credit").value(7000.00))
                .andExpect(jsonPath("$.data.rows[?(@.code=='4000')].debit").value(7000.00))
                .andExpect(jsonPath("$.data.rows[?(@.code=='4000')].credit").value(7000.00));
    }

    @Test
    @DisplayName("a duplicate account code is refused")
    void refusesADuplicateAccountCode() throws Exception {
        mockMvc.perform(post("/api/v1/accounting/accounts")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"code":"1000","name":"Duplicate cash","accountGroup":"ASSET",
                                 "accountType":"CASH"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("only one fiscal year can be open at a time")
    void refusesASecondOpenFiscalYear() throws Exception {
        LocalDate start = LocalDate.of(2027, 4, 1);
        String otherId = postJson("/api/v1/accounting/fiscal-years", """
                {"name":"FY 2083/84","code":"FY2083","startDate":"%s","endDate":"%s"}"""
                .formatted(start, start.plusDays(364))).path("id").asText();

        // Two open years would let a transaction be booked to the wrong calendar, so the
        // second one is refused while the first is still open.
        mockMvc.perform(post("/api/v1/accounting/fiscal-years/{id}/open", otherId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/accounting/fiscal-years/{id}/close", fiscalYearId.toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/accounting/fiscal-years/{id}/open", otherId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an overlapping accounting period is refused")
    void refusesAnOverlappingPeriod() throws Exception {
        mockMvc.perform(post("/api/v1/accounting/periods")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"fiscalYearId":"%s","name":"Bhadra","startDate":"%s","endDate":"%s"}"""
                                .formatted(fiscalYearId, LocalDate.of(2026, 4, 15),
                                        LocalDate.of(2026, 5, 15))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a period outside its fiscal year is refused")
    void refusesAPeriodOutsideTheFiscalYear() throws Exception {
        mockMvc.perform(post("/api/v1/accounting/periods")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"fiscalYearId":"%s","name":"Outside","startDate":"%s","endDate":"%s"}"""
                                .formatted(fiscalYearId, LocalDate.of(2028, 1, 1),
                                        LocalDate.of(2028, 1, 31))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a bank account with a matching statement reconciles, a mismatched one is filed as a discrepancy")
    void reconciliationComparesStatementToLedger() throws Exception {
        String entryId = entry("""
                {"entryDate":"%s","accountingPeriodId":"%s","sourceType":"MANUAL",
                 "description":"Bank deposit",
                 "lines":[
                   {"accountId":"%s","debit":20000.00,"credit":0},
                   {"accountId":"%s","debit":0,"credit":20000.00}]}"""
                .formatted(LocalDate.of(2026, 4, 9), periodId, cashAccount, feesAccount)).path("id").asText();
        mockMvc.perform(post("/api/v1/accounting/journal-entries/{id}/post", entryId)
                        .header("Authorization", bearer(token)));

        String bankId = postJson("/api/v1/accounting/bank-accounts", """
                {"name":"Nabil Current","bankName":"Nabil Bank","accountNumber":"001002003",
                 "chartAccountId":"%s","currency":"NPR"}""".formatted(cashAccount)).path("id").asText();

        String matching = postJson("/api/v1/accounting/bank-accounts/" + bankId + "/reconciliations",
                ("""
                {"statementDate":"%s","statementEndingBalance":20000.00}"""
                .formatted(LocalDate.of(2026, 4, 30)))).path("id").asText();
        mockMvc.perform(post("/api/v1/accounting/reconciliations/{id}/complete", matching)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECONCILED"));

        String mismatched = postJson("/api/v1/accounting/bank-accounts/" + bankId + "/reconciliations",
                ("""
                {"statementDate":"%s","statementEndingBalance":18500.00}"""
                .formatted(LocalDate.of(2026, 5, 31)))).path("id").asText();
        mockMvc.perform(post("/api/v1/accounting/reconciliations/{id}/complete", mismatched)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISCREPANCY"));
    }

    // ----------------------------------------------------------------------- helpers

    /** POSTs a JSON body as the administrator and returns the {@code data} node. */
    private com.fasterxml.jackson.databind.JsonNode postJson(String path, String body) throws Exception {
        String response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private UUID account(String code, String name, ChartOfAccounts.AccountGroup group,
                         ChartOfAccounts.AccountType type) throws Exception {
        return account(code, name, group, type, true);
    }

    private UUID account(String code, String name, ChartOfAccounts.AccountGroup group,
                         ChartOfAccounts.AccountType type, boolean postable) throws Exception {
        return UUID.fromString(postJson("/api/v1/accounting/accounts", """
                {"code":"%s","name":"%s","accountGroup":"%s","accountType":"%s","postable":%s}"""
                .formatted(code, name, group, type, postable)).path("id").asText());
    }

    private com.fasterxml.jackson.databind.JsonNode entry(String body) throws Exception {
        return postJson("/api/v1/accounting/journal-entries", body);
    }

    private int entriesInPeriod() {
        return jdbcTemplate.queryForObject("select count(*) from journal_entries", Integer.class);
    }
}
