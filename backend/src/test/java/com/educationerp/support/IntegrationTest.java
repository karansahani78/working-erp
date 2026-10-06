package com.educationerp.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base class for tests that need the real schema: Flyway runs, Hibernate validates the
 * mapping and the full application context is wired. The database is emptied before each
 * test so tests stay order independent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected TestData testData;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE audit_logs, refresh_tokens, password_reset_tokens, user_roles, "
                + "role_permissions, roles, users, timetable_entries, course_offerings, time_slots, rooms, "
                + "curriculum_courses, curricula, program_versions, sections, school_classes, semesters, "
                + "courses, programs, departments, faculties, academic_calendar, academic_years, campuses, "
                + "report_card_items, report_cards, transcripts, result_corrections, results, "
                + "grade_boundaries, grading_scales, exam_subjects, examinations, attendance_corrections, "
                + "attendance_records, "
                // HR and payroll.
                + "payslip_items, payslips, payroll_runs, employee_loans, employee_attendance, "
                + "leave_requests, leave_balances, leave_types, employee_documents, "
                + "employee_qualifications, qualifications, employments, employees, salary_components, "
                + "salary_structures, tax_rules, designations, "
                // Finance and accounting. Order does not matter because CASCADE pulls in
                // every referencing table, but each table is named so an unmigrated schema
                // fails loudly here instead of silently leaking rows between tests.
                + "bank_reconciliations, journal_lines, journal_entries, bank_accounts, accounting_periods, "
                + "fiscal_years, chart_of_accounts, refunds, payments, invoice_items, invoices, fines, "
                + "student_fee_assessments, student_concessions, scholarships, discounts, fee_components, "
                + "fee_structures, document_sequences, "
                + "enrollments, student_guardians, guardians, admission_decisions, "
                + "application_documents, "
                + "admission_applications, admission_campaigns, students, student_number_settings, "
                + "student_number_sequences, import_batch_duplicates, import_batches, "
                // Optional modules. Named so an unmigrated schema fails here rather than
                // letting one test's books and stock items leak into the next.
                + "document_versions, document_access, book_copies, library_issues, "
                + "library_reservations, library_fines, library_renewals, library_members, "
                + "library_categories, books_authors, books, authors, publishers, stock_issues, "
                + "stock_transfers, stock_movements, purchase_items, stock, items, "
                + "item_categories, stores, asset_maintenance, asset_assignments, assets, "
                + "asset_categories, documents, "

                + "module_settings, institution RESTART IDENTITY CASCADE");
        testData.reset();
    }

    /**
     * Signs in as a seeded administrator and returns the bearer token for subsequent
     * requests, so individual tests do not repeat the login round trip.
     */
    protected String loginToken(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginPayload(username, password))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected String adminToken() throws Exception {
        // The setup wizard, and with it the administrator account, is created lazily the
        // first time a test touches seeded data. Seeding it here means a test can sign in
        // before it builds any fixtures, instead of failing on a 401 for a user that simply
        // has not been created yet.
        testData.institution();
        return loginToken(TestData.ADMIN_USERNAME, TestData.ADMIN_PASSWORD);
    }

    private record LoginPayload(String loginId, String password) {
    }
}