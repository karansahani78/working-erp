package com.educationerp.finance;

import com.educationerp.auth.role.Role;
import com.educationerp.support.CourseOfferingFixture;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fee assessment, invoicing, payments and refunds.
 *
 * <p>The emphasis is on the rules that cost money when they are wrong: a published price
 * must not move, a payment must not be taken twice, a confirmed payment must not be
 * deleted, and a refunded payment must leave a truthful balance behind.
 */
class FinanceIntegrationTest extends IntegrationTest {

    @Autowired
    private CourseOfferingFixture fixture;

    private static final LocalDate DUE = LocalDate.now().plusDays(30);

    // ------------------------------------------------------------ fee structures

    @Test
    @DisplayName("a draft fee structure can be priced, componentised and published")
    void publishesAFeeStructure() throws Exception {
        String token = adminToken();
        UUID academicYearId = jdbcTemplate.queryForObject(
                "select id from academic_years order by created_at limit 1", UUID.class);

        Map<String, Object> structure = createFeeStructure(token, "FS-BCA-2081", 50000.00, academicYearId);
        String id = structure.get("id").toString();
        assertThat(structure.get("status")).isEqualTo("DRAFT");

        mockMvc.perform(put("/api/v1/finance/fee-structures/{id}/components", id)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"components":[
                                  {"componentType":"TUITION","name":"Tuition","amount":40000.00,"mandatory":true},
                                  {"componentType":"ADMISSION","name":"Admission","amount":10000.00,"mandatory":true}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.componentsMatchTotal").value(true));

        mockMvc.perform(post("/api/v1/finance/fee-structures/{id}/publish", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("a fee structure whose components disagree with its total cannot be published")
    void refusesToPublishAnInconsistentFeeStructure() throws Exception {
        String token = adminToken();
        UUID academicYearId = jdbcTemplate.queryForObject(
                "select id from academic_years order by created_at limit 1", UUID.class);
        String id = createFeeStructure(token, "FS-MISMATCH", 50000.00, academicYearId).get("id").toString();

        mockMvc.perform(put("/api/v1/finance/fee-structures/{id}/components", id)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"components":[
                                  {"componentType":"TUITION","name":"Tuition","amount":45000.00,"mandatory":true}]}"""))
                .andExpect(status().isOk());

        // Two different answers to "what does this cost?" is exactly what we refuse to publish.
        mockMvc.perform(post("/api/v1/finance/fee-structures/{id}/publish", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a published fee structure is frozen")
    void refusesToEditAPublishedFeeStructure() throws Exception {
        String token = adminToken();
        UUID academicYearId = jdbcTemplate.queryForObject(
                "select id from academic_years order by created_at limit 1", UUID.class);
        String id = createFeeStructure(token, "FS-FROZEN", 50000.00, academicYearId).get("id").toString();
        mockMvc.perform(post("/api/v1/finance/fee-structures/{id}/publish", id)
                .header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        // A price that moves after students have been billed is how an institution ends up
        // unable to explain an invoice.
        mockMvc.perform(put("/api/v1/finance/fee-structures/{id}", id)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"name":"Rais","totalAmount":99000.00,"currency":"NPR"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a duplicate fee structure code is rejected")
    void rejectsDuplicateFeeStructureCode() throws Exception {
        String token = adminToken();
        UUID academicYearId = jdbcTemplate.queryForObject(
                "select id from academic_years order by created_at limit 1", UUID.class);
        createFeeStructure(token, "FS-DUPE", 1000.00, academicYearId);
        mockMvc.perform(post("/api/v1/finance/fee-structures")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"name":"Again","code":"fs-dupe","academicYearId":"%s","totalAmount":2000.00}"""
                                .formatted(academicYearId)))
                .andExpect(status().isConflict());
    }

    // --------------------------------------------------------------- concessions

    @Test
    @DisplayName("granting a discount reduces the net fee and records the concession")
    void grantConcessionReducesTheNetFee() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("concession@example.test");
        String structureId = publishedStructure(token, academicYearId, 100000.00, "FS-CONCESSION");
        String assessmentId = assess(token, studentId, structureId);
        long gross = amount(token, assessmentId, "grossAmount");
        assertThat(gross).isEqualTo(100000L);

        String discountId = createDiscount(token, "MERIT-50");
        mockMvc.perform(post("/api/v1/finance/students/{id}/concessions", studentId)
                        .param("academicYearId", academicYearId.toString())
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"discountId":"%s","amount":25000.00,"reason":"Merit award"}""".formatted(discountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(25000.00));

        String body = getAssessment(token, assessmentId);
        assertThat(number(body, "discountAmount")).isEqualTo(25000L);
        assertThat(number(body, "netAmount")).isEqualTo(75000L);
        assertThat(number(body, "outstandingAmount")).isEqualTo(75000L);
    }

    @Test
    @DisplayName("a concession larger than the fee is refused")
    void refusesAnOverlargeConcession() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("big-concession@example.test");
        String structureId = publishedStructure(token, academicYearId, 10000.00, "FS-SMALL");
        String assessmentId = assess(token, studentId, structureId);
        String discountId = createDiscount(token, "HALF-OFF");

        mockMvc.perform(post("/api/v1/finance/students/{id}/concessions", studentId)
                        .param("academicYearId", academicYearId.toString())
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"discountId":"%s","amount":99999.00}""".formatted(discountId)))
                .andExpect(status().isUnprocessableEntity());
        assertThat(number(getAssessment(token, assessmentId), "netAmount")).isEqualTo(10000L);
    }

    @Test
    @DisplayName("a concession cannot be granted before the student has been assessed")
    void refusesAConcessionWithNoAssessment() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("unassessed@example.test");
        String discountId = createDiscount(token, "LATE-AWARD");

        mockMvc.perform(post("/api/v1/finance/students/{id}/concessions", studentId)
                        .param("academicYearId", academicYearId.toString())
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"discountId":"%s","amount":1000.00}""".formatted(discountId)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a concession must name exactly one of a discount or a scholarship")
    void refusesBothADiscountAndAScholarship() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("both@example.test");
        String discountId = createDiscount(token, "BOTH-A");
        mockMvc.perform(post("/api/v1/finance/scholarships")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"code":"BOTH-B","name":"Both B","valueType":"PERCENTAGE","value":10}"""))
                .andExpect(status().isOk());
        String scholarshipId = jdbcTemplate.queryForObject(
                "select id from scholarships where code = 'BOTH-B'", UUID.class).toString();

        mockMvc.perform(post("/api/v1/finance/students/{id}/concessions", studentId)
                        .param("academicYearId", academicYearId.toString())
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"discountId":"%s","scholarshipId":"%s","amount":100.00}"""
                                .formatted(discountId, scholarshipId)))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- assessment

    @Test
    @DisplayName("a student cannot be assessed twice against the same fee structure")
    void refusesADuplicateAssessment() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("double@example.test");
        String structureId = publishedStructure(token, academicYearId, 30000.00, "FS-ONCE");
        assess(token, studentId, structureId);

        mockMvc.perform(post("/api/v1/finance/students/{id}/fee-assessments", studentId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"feeStructureId":"%s","dueDate":"%s"}""".formatted(structureId, DUE)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a draft fee structure cannot be assessed to a student")
    void refusesToAssessADraftStructure() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("draft@example.test");
        String structureId = createFeeStructure(token, "FS-DRAFT", 30000.00, academicYearId).get("id").toString();

        mockMvc.perform(post("/api/v1/finance/students/{id}/fee-assessments", studentId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"feeStructureId":"%s","dueDate":"%s"}""".formatted(structureId, DUE)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------- invoicing

    @Test
    @DisplayName("an issued invoice carries the assessed net amount and its line items")
    void issuesAnInvoice() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("invoice@example.test");
        String structureId = publishedStructure(token, academicYearId, 60000.00, "FS-INVOICE");
        String assessmentId = assess(token, studentId, structureId);

        String invoiceId = createInvoice(token, studentId, assessmentId);
        mockMvc.perform(post("/api/v1/finance/invoices/{id}/issue", invoiceId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ISSUED"))
                .andExpect(jsonPath("$.data.invoiceNumber").value(org.hamcrest.Matchers.startsWith("INV-")));

        mockMvc.perform(get("/api/v1/finance/invoices/{id}", invoiceId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(60000.00))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    @DisplayName("a discounted and scholarshiped invoice still totals the assessed net")
    void invoicesAConcededAssessment() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("conceded-invoice@example.test");
        String structureId = publishedStructure(token, academicYearId, 100000.00, "FS-CONCEDED");
        String assessmentId = assess(token, studentId, structureId);

        grantConcession(token, studentId, academicYearId, createDiscount(token, "MERIT"), null, 25000.00);
        grantConcession(token, studentId, academicYearId, null, createScholarship(token, "HALF-FEE"), 25000.00);
        assertThat(amount(token, assessmentId, "netAmount")).isEqualTo(50000L);

        String invoiceId = createInvoice(token, studentId, assessmentId);

        // The lines are the gross charge and the two concessions, so the student can see both
        // what they were charged and what was waived, while the invoice still nets to 50000.
        mockMvc.perform(get("/api/v1/finance/invoices/{id}", invoiceId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(50000.00))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                // Lines come back ordered by component type: ADMISSION, OTHER, TUITION.
                .andExpect(jsonPath("$.data.items[0].componentType").value("ADMISSION"))
                .andExpect(jsonPath("$.data.items[0].amount").value(25000.00))
                .andExpect(jsonPath("$.data.items[0].concession").value(true))
                .andExpect(jsonPath("$.data.items[1].componentType").value("OTHER"))
                .andExpect(jsonPath("$.data.items[1].amount").value(25000.00))
                .andExpect(jsonPath("$.data.items[1].concession").value(true))
                .andExpect(jsonPath("$.data.items[2].componentType").value("TUITION"))
                .andExpect(jsonPath("$.data.items[2].amount").value(100000.00))
                .andExpect(jsonPath("$.data.items[2].concession").value(false));

        // Issue-time reconciliation is what used to reject this invoice outright.
        mockMvc.perform(post("/api/v1/finance/invoices/{id}/issue", invoiceId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ISSUED"))
                .andExpect(jsonPath("$.data.balanceDue").value(50000.00));
    }

    @Test
    @DisplayName("a second invoice for the same assessment is refused")
    void refusesADuplicateInvoice() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("two-invoices@example.test");
        String structureId = publishedStructure(token, academicYearId, 60000.00, "FS-INVOICE-2");
        String assessmentId = assess(token, studentId, structureId);
        createInvoice(token, studentId, assessmentId);

        mockMvc.perform(post("/api/v1/finance/students/{id}/invoices", studentId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"assessmentId":"%s","issueDate":"%s"}"""
                                .formatted(assessmentId, LocalDate.now())))
                .andExpect(status().isConflict());
    }

    // -------------------------------------------------------------------- payments

    @Test
    @DisplayName("a cash payment settles the invoice and the assessment")
    void cashPaymentSettlesTheBill() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("cash@example.test");
        String structureId = publishedStructure(token, academicYearId, 40000.00, "FS-CASH");
        String assessmentId = assess(token, studentId, structureId);
        String invoiceId = createInvoice(token, studentId, assessmentId);
        mockMvc.perform(post("/api/v1/finance/invoices/{id}/issue", invoiceId)
                .header("Authorization", bearer(token)));

        String paymentId = pay(token, studentId, invoiceId, 40000.00, "key-cash-1");

        mockMvc.perform(get("/api/v1/finance/payments/{id}", paymentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.receiptNumber").value(org.hamcrest.Matchers.startsWith("RCP-")));

        assertThat(text(getAssessment(token, assessmentId), "status")).isEqualTo("PAID");
        mockMvc.perform(get("/api/v1/finance/invoices/{id}", invoiceId)
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.balanceDue").value(0.00));
    }

    @Test
    @DisplayName("a partial payment leaves the remainder outstanding on both bills")
    void partialPaymentLeavesABalance() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("partial@example.test");
        String structureId = publishedStructure(token, academicYearId, 40000.00, "FS-PARTIAL");
        String assessmentId = assess(token, studentId, structureId);
        String invoiceId = createInvoice(token, studentId, assessmentId);
        mockMvc.perform(post("/api/v1/finance/invoices/{id}/issue", invoiceId)
                .header("Authorization", bearer(token)));

        pay(token, studentId, invoiceId, 15000.00, "key-partial-1");

        assertThat(number(getAssessment(token, assessmentId), "outstandingAmount")).isEqualTo(25000L);
        mockMvc.perform(get("/api/v1/finance/invoices/{id}", invoiceId)
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.data.balanceDue").value(25000.00));
    }

    @Test
    @DisplayName("a repeated idempotency key returns the original payment instead of charging twice")
    void repeatedIdempotencyKeyDoesNotChargeTwice() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("idempotent@example.test");
        String structureId = publishedStructure(token, academicYearId, 40000.00, "FS-IDEMPOTENT");
        String assessmentId = assess(token, studentId, structureId);
        String invoiceId = createInvoice(token, studentId, assessmentId);
        mockMvc.perform(post("/api/v1/finance/invoices/{id}/issue", invoiceId)
                .header("Authorization", bearer(token)));

        String first = pay(token, studentId, invoiceId, 10000.00, "key-retry");
        String second = pay(token, studentId, invoiceId, 10000.00, "key-retry");

        // A client retrying after a timeout must get the same answer, not a second charge.
        assertThat(second).isEqualTo(first);
        assertThat(number(getAssessment(token, assessmentId), "paidAmount")).isEqualTo(10000L);
        assertThat(countPayments(studentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("one student cannot pay another student's invoice")
    void refusesToPaySomebodyElsesInvoice() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID payerId = fixture.createStudent("payer@example.test");
        UUID otherId = fixture.createStudent("debtor@example.test");
        String structureId = publishedStructure(token, academicYearId, 20000.00, "FS-CROSS");
        String assessmentId = assess(token, otherId, structureId);
        String invoiceId = createInvoice(token, otherId, assessmentId);

        mockMvc.perform(post("/api/v1/finance/payments")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"studentId":"%s","invoiceId":"%s","amount":20000.00,"method":"CASH"}"""
                                .formatted(payerId, invoiceId)))
                .andExpect(status().isUnprocessableEntity());

        // The invoice is untouched, so the money is still owed by the student who owes it.
        mockMvc.perform(get("/api/v1/finance/invoices/{id}", invoiceId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidAmount").value(0.00))
                .andExpect(jsonPath("$.data.balanceDue").value(20000.00));
    }

    @Test
    @DisplayName("a payment carries the currency of the assessment it settles")
    void paymentTakesTheCurrencyFromTheAssessment() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("currency@example.test");
        String structureId = publishedStructure(token, academicYearId, 5000.00, "FS-CURRENCY");
        String assessmentId = assess(token, studentId, structureId);
        jdbcTemplate.update("update student_fee_assessments set currency = ? where id = ?",
                "USD", UUID.fromString(assessmentId));

        // No invoice: the payment is taken straight against the assessment, so the assessed
        // currency is the only thing that can tell us what was charged in.
        mockMvc.perform(post("/api/v1/finance/payments")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"studentId":"%s","amount":5000.00,"method":"CASH"}"""
                                .formatted(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("a payment larger than the balance is refused")
    void refusesAnOverpayment() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("overpay@example.test");
        String structureId = publishedStructure(token, academicYearId, 10000.00, "FS-OVERPAY");
        String assessmentId = assess(token, studentId, structureId);
        String invoiceId = createInvoice(token, studentId, assessmentId);
        mockMvc.perform(post("/api/v1/finance/invoices/{id}/issue", invoiceId)
                .header("Authorization", bearer(token)));

        mockMvc.perform(post("/api/v1/finance/payments")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"studentId":"%s","invoiceId":"%s","amount":99999.00,"method":"CASH",
                                 "idempotencyKey":"key-overpay"}""".formatted(studentId, invoiceId)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a digital method without a provider is rejected rather than left pending forever")
    void refusesADigitalMethodWithNoProvider() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("noprovider@example.test");
        String structureId = publishedStructure(token, academicYearId, 10000.00, "FS-NOPROVIDER");
        String assessmentId = assess(token, studentId, structureId);
        String invoiceId = createInvoice(token, studentId, assessmentId);

        mockMvc.perform(post("/api/v1/finance/payments")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"studentId":"%s","invoiceId":"%s","amount":100.00,"method":"ESEWA",
                                 "idempotencyKey":"key-noprovider"}""".formatted(studentId, invoiceId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a provider payment for an unconfigured gateway fails loudly")
    void unconfiguredGatewayFailsLoudly() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("psp@example.test");
        String structureId = publishedStructure(token, academicYearId, 10000.00, "FS-PSP");
        String assessmentId = assess(token, studentId, structureId);
        String invoiceId = createInvoice(token, studentId, assessmentId);
        issue(token, invoiceId);

        // eSewa is off in the test profile, so the request must not silently succeed.
        mockMvc.perform(post("/api/v1/finance/payments")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"studentId":"%s","invoiceId":"%s","amount":100.00,"method":"ESEWA",
                                 "provider":"ESEWA","idempotencyKey":"key-psp"}""".formatted(studentId, invoiceId)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("a callback for an unknown transaction is rejected")
    void rejectsAnUnknownCallback() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/v1/finance/payments/callback/ESEWA")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"provider":"ESEWA","providerTransactionId":"never-seen",
                                 "studentId":"%s","amount":100.00,"status":"CONFIRMED"}"""
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a confirmed payment cannot be marked failed")
    void refusesToFailAConfirmedPayment() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("settled@example.test");
        String structureId = publishedStructure(token, academicYearId, 10000.00, "FS-SETTLED");
        String assessmentId = assess(token, studentId, structureId);
        String invoiceId = createInvoice(token, studentId, assessmentId);
        mockMvc.perform(post("/api/v1/finance/invoices/{id}/issue", invoiceId)
                .header("Authorization", bearer(token)));
        String paymentId = pay(token, studentId, invoiceId, 10000.00, "key-settled");

        // The receipt is evidence; undoing it means a refund, not an edit.
        mockMvc.perform(post("/api/v1/finance/payments/{id}/fail", paymentId)
                        .param("reason", "changed my mind")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
    }

    // --------------------------------------------------------------------- refunds

    @Test
    @DisplayName("a processed refund restores the balance without deleting the payment")
    void refundRestoresTheBalance() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("refund@example.test");
        String structureId = publishedStructure(token, academicYearId, 40000.00, "FS-REFUND");
        String assessmentId = assess(token, studentId, structureId);
        String invoiceId = createInvoice(token, studentId, assessmentId);
        mockMvc.perform(post("/api/v1/finance/invoices/{id}/issue", invoiceId)
                .header("Authorization", bearer(token)));
        String paymentId = pay(token, studentId, invoiceId, 40000.00, "key-refund");

        String refundId = mockMvc.perform(post("/api/v1/finance/students/{id}/refunds", studentId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"paymentId":"%s","amount":15000.00,"reason":"Course dropped","method":"CASH"}"""
                                .formatted(paymentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String refund = objectMapper.readTree(refundId).path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/finance/refunds/{id}/decision", refund)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"status":"APPROVED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSED"));

        // The payment row survives untouched: the refund is the record of the change.
        mockMvc.perform(get("/api/v1/finance/payments/{id}", paymentId)
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.data.amount").value(40000.00));
        assertThat(number(getAssessment(token, assessmentId), "outstandingAmount")).isEqualTo(15000L);
    }

    @Test
    @DisplayName("a refund cannot exceed what is left refundable")
    void refusesAnOverlargeRefund() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("overrefund@example.test");
        String structureId = publishedStructure(token, academicYearId, 10000.00, "FS-OVERREFUND");
        String assessmentId = assess(token, studentId, structureId);
        String invoiceId = createInvoice(token, studentId, assessmentId);
        mockMvc.perform(post("/api/v1/finance/invoices/{id}/issue", invoiceId)
                .header("Authorization", bearer(token)));
        String paymentId = pay(token, studentId, invoiceId, 10000.00, "key-overrefund");

        mockMvc.perform(post("/api/v1/finance/students/{id}/refunds", studentId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"paymentId":"%s","amount":99999.00,"reason":"greedy"}"""
                                .formatted(paymentId)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a rejected refund leaves the settled balance untouched")
    void rejectedRefundLeavesTheBalanceUntouched() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID studentId = fixture.createStudent("rejected-refund@example.test");
        String structureId = publishedStructure(token, academicYearId, 20000.00, "FS-REJECTED-REFUND");
        String assessmentId = assess(token, studentId, structureId);
        String invoiceId = createInvoice(token, studentId, assessmentId);
        mockMvc.perform(post("/api/v1/finance/invoices/{id}/issue", invoiceId)
                        .header("Authorization", bearer(token)));
        String paymentId = pay(token, studentId, invoiceId, 20000.00, "key-rejected-refund");

        String body = mockMvc.perform(post("/api/v1/finance/students/{id}/refunds", studentId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"paymentId":"%s","amount":5000.00,"reason":"Requested in error"}"""
                                .formatted(paymentId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refundId = objectMapper.readTree(body).path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/finance/refunds/{id}/decision", refundId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"status":"REJECTED","reason":"The fee was correctly charged"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        // Nothing moved: the student still owes nothing and the receipt is still intact.
        assertThat(number(getAssessment(token, assessmentId), "outstandingAmount")).isZero();
        mockMvc.perform(get("/api/v1/finance/payments/{id}", paymentId)
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    // --------------------------------------------------------------------- reporting

    @Test
    @DisplayName("the receivables report totals what is still owed and flags overdue rows")
    void receivablesReportTotalsOutstandingBalances() throws Exception {
        String token = adminToken();
        UUID academicYearId = academicYearId();
        UUID payer = fixture.createStudent("paid-up@example.test");
        UUID defaulter = fixture.createStudent("defaulter@example.test");
        String structureId = publishedStructure(token, academicYearId, 20000.00, "FS-AGED");

        String paidAssessment = assess(token, payer, structureId);
        String unpaidAssessment = assess(token, defaulter, structureId);
        String invoiceId = createInvoice(token, payer, paidAssessment);
        mockMvc.perform(post("/api/v1/finance/invoices/{id}/issue", invoiceId)
                .header("Authorization", bearer(token)));
        pay(token, payer, invoiceId, 20000.00, "key-aged");
        jdbcTemplate.update("update student_fee_assessments set due_date = ? where id = ?",
                LocalDate.now().minusDays(10), UUID.fromString(unpaidAssessment));

        mockMvc.perform(get("/api/v1/finance/reports/receivables")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOutstanding").value(20000.00))
                .andExpect(jsonPath("$.data.totalOverdue").value(20000.00))
                .andExpect(jsonPath("$.data.assessmentCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].daysOverdue").value(10));
    }

    @Test
    @DisplayName("a fine can be raised and waived")
    void fineCanBeRaisedAndWaived() throws Exception {
        String token = adminToken();
        UUID studentId = fixture.createStudent("fine@example.test");
        String body = mockMvc.perform(post("/api/v1/finance/fines")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"studentId":"%s","description":"Lost library book","amount":500.00}"""
                                .formatted(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String fineId = objectMapper.readTree(body).path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/finance/fines/{id}/waive", fineId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAIVED"));
    }

    @Test
    @DisplayName("another user cannot read a student's finance record without permission")
    void protectsStudentFinanceRecords() throws Exception {
        String token = adminToken();
        UUID studentId = fixture.createStudent("private@example.test");
        testData.user("financeviewer", "Finance Viewer", "financeviewer@example.test",
                Role.TEACHER, "ViewerPass123");
        String viewer = loginToken("financeviewer", "ViewerPass123");

        mockMvc.perform(get("/api/v1/finance/students/{id}/payments", studentId)
                        .header("Authorization", bearer(viewer)))
                .andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------------------- helpers

    private UUID academicYearId() {
        return jdbcTemplate.queryForObject("select id from academic_years order by created_at limit 1", UUID.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createFeeStructure(String token, String code, double total, UUID academicYearId)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/finance/fee-structures")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","code":"%s","academicYearId":"%s","totalAmount":%.2f,"currency":"NPR"}"""
                                .formatted(code, code, academicYearId, total)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapOf(body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(String json) throws Exception {
        return objectMapper.readValue(objectMapper.readTree(json).path("data").toString(), Map.class);
    }

    private String publishedStructure(String token, UUID academicYearId, double total, String code) throws Exception {
        String id = createFeeStructure(token, code, total, academicYearId).get("id").toString();
        mockMvc.perform(post("/api/v1/finance/fee-structures/{id}/publish", id)
                .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        return id;
    }

    private String assess(String token, UUID studentId, String structureId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/finance/students/{id}/fee-assessments", studentId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"feeStructureId":"%s","dueDate":"%s"}""".formatted(structureId, DUE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private String createInvoice(String token, UUID studentId, String assessmentId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/finance/students/{id}/invoices", studentId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"assessmentId":"%s","issueDate":"%s","dueDate":"%s"}"""
                                .formatted(assessmentId, LocalDate.now(), DUE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private void issue(String token, String invoiceId) throws Exception {
        mockMvc.perform(post("/api/v1/finance/invoices/{id}/issue", invoiceId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ISSUED"));
    }

    private String pay(String token, UUID studentId, String invoiceId, double amount, String idempotencyKey)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/finance/payments")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"studentId":"%s","invoiceId":"%s","amount":%.2f,"method":"CASH",
                                 "idempotencyKey":"%s"}""".formatted(studentId, invoiceId, amount, idempotencyKey)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private String createDiscount(String token, String code) throws Exception {
        mockMvc.perform(post("/api/v1/finance/discounts")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"code":"%s","name":"%s","valueType":"PERCENTAGE","value":10}"""
                                .formatted(code, code)))
                .andExpect(status().isOk());
        return jdbcTemplate.queryForObject("select id from discounts where code = ?", UUID.class, code).toString();
    }

    private void grantConcession(String token, UUID studentId, UUID academicYearId,
                                 String discountId, String scholarshipId, double amount)
            throws Exception {
        String which = discountId != null ? "discountId" : "scholarshipId";
        String value = discountId != null ? discountId : scholarshipId;
        mockMvc.perform(post("/api/v1/finance/students/{id}/concessions", studentId)
                        .param("academicYearId", academicYearId.toString())
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"%s":"%s","amount":%.2f}""".formatted(which, value, amount)))
                .andExpect(status().isOk());
    }

    private String createScholarship(String token, String code) throws Exception {
        mockMvc.perform(post("/api/v1/finance/scholarships")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"code":"%s","name":"%s","valueType":"PERCENTAGE","value":50}"""
                                .formatted(code, code)))
                .andExpect(status().isOk());
        return jdbcTemplate.queryForObject("select id from scholarships where code = ?", UUID.class, code).toString();
    }

    private String getAssessment(String token, String assessmentId) throws Exception {
        return mockMvc.perform(get("/api/v1/finance/fee-assessments/{id}", assessmentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private long amount(String token, String assessmentId, String field) throws Exception {
        return number(getAssessment(token, assessmentId), field);
    }

    private long number(String json, String field) throws Exception {
        return objectMapper.readTree(json).path("data").path(field).asLong();
    }

    private String text(String json, String field) throws Exception {
        return objectMapper.readTree(json).path("data").path(field).asText();
    }

    private long countPayments(UUID studentId) {
        return jdbcTemplate.queryForObject("select count(*) from payments where student_id = ?", Long.class, studentId);
    }
}
