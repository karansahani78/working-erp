package com.educationerp.dashboard;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.institution.InstitutionService;
import com.educationerp.institution.ModuleKey;
import com.educationerp.common.error.AppException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The two dashboards the blueprint asks a school to open the system with.
 *
 * <p>Teachers, students and parents already have their own portals. What is missing is the view
 * from the top of the building and the view from the finance desk, and those two look at
 * different things: a principal wants to know whether the school is running, an accountant wants
 * to know what has come in and what is owed.
 *
 * <p>Every figure is its own query behind its own permission, and a figure the caller may not see
 * is left out rather than reported as zero. An empty dashboard for somebody without the
 * permission is the correct answer; a dashboard of zeroes is a lie.
 */
@Service
public class DashboardService {

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationChecker auth;
    private final InstitutionService institutions;

    public DashboardService(NamedParameterJdbcTemplate jdbc, AuthorizationChecker auth,
                            InstitutionService institutions) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.institutions = institutions;
    }

    // ------------------------------------------------------------------ principal

    /**
     * The principal's view: size, attendance, money owed, results, staff, and what is untidy.
     */
    @Transactional(readOnly = true)
    public DashboardDtos.PrincipalDashboard principal() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        Map<String, Object> dates = new MapSqlParameterSource()
                .addValue("today", today)
                .addValue("from", monthStart)
                .addValue("to", today.plusDays(1))
                .getValues();

        List<DashboardDtos.Section> sections = new ArrayList<>();
        sections.add(students(dates));
        sections.add(attendance(dates));
        sections.add(admissions(dates));
        sections.add(fees(dates, monthStart));
        sections.add(results());
        sections.add(staff());
        sections.removeIf(section -> section == null || section.tiles().isEmpty());

        return new DashboardDtos.PrincipalDashboard(today.toString(), sections, alerts());
    }

    /**
     * The accountant's view: money in, money owed, refunds to make, what has been spent.
     */
    @Transactional(readOnly = true)
    public DashboardDtos.AccountantDashboard accountant() {
        auth.requirePermission("FINANCE_REPORT_READ");
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        Map<String, Object> dates = new MapSqlParameterSource()
                .addValue("today", today)
                .addValue("from", monthStart)
                .addValue("to", today.plusDays(1))
                .getValues();

        List<DashboardDtos.Section> sections = new ArrayList<>();
        sections.add(new DashboardDtos.Section("Collection", "FINANCE_REPORT_READ", List.of(
                DashboardDtos.Tile.money("Received today", scalar(dates,
                        "select coalesce(sum(amount), 0) from payments"
                                + " where status = 'CONFIRMED' and received_at >= :today"
                                + " and received_at < :to"),
                        "Confirmed payments dated today"),
                DashboardDtos.Tile.money("Received this month", scalar(dates,
                        "select coalesce(sum(amount), 0) from payments"
                                + " where status = 'CONFIRMED' and received_at >= :from"
                                + " and received_at < :to"),
                        "Confirmed payments from " + monthStart),
                DashboardDtos.Tile.count("Payments today", count(dates,
                        "select count(*) from payments where status = 'CONFIRMED'"
                                + " and received_at >= :today and received_at < :to"),
                        "Confirmed payments dated today"))));

        sections.add(receivables());
        sections.add(refunds());
        sections.add(expenses(monthStart));
        sections.removeIf(section -> section == null || section.tiles().isEmpty());

        return new DashboardDtos.AccountantDashboard(today.toString(),
                sections.stream().filter(section -> !section.tiles().isEmpty()).toList(),
                financeAlerts());
    }

    // ------------------------------------------------------------------ sections

    private DashboardDtos.Section students(Map<String, Object> dates) {
        if (!auth.hasPermission("STUDENT_READ")) {
            return null;
        }
        return new DashboardDtos.Section("Students", "STUDENT_READ", List.of(
                DashboardDtos.Tile.count("On the roll", count(dates,
                        "select count(*) from students where status = 'ACTIVE'"),
                        "Active students"),
                DashboardDtos.Tile.count("Male", count(dates,
                        "select count(*) from students where status = 'ACTIVE' and gender = 'MALE'"),
                        "Active students recorded as male"),
                DashboardDtos.Tile.count("Female", count(dates,
                        "select count(*) from students where status = 'ACTIVE'"
                                + " and gender = 'FEMALE'"),
                        "Active students recorded as female"),
                DashboardDtos.Tile.count("New this month", count(dates,
                        "select count(*) from students"
                                + " where enrollment_date >= :from and enrollment_date < :to"),
                        "Enrolled between " + dates.get("from") + " and " + dates.get("to"))));
    }

    private DashboardDtos.Section attendance(Map<String, Object> dates) {
        if (!auth.hasPermission("ATTENDANCE_READ")) {
            return null;
        }
        Map<String, Object> today = one(dates,
                """
                select count(*) as marked,
                       count(*) filter (where status = 'PRESENT') as present,
                       count(*) filter (where status = 'LATE') as late,
                       count(*) filter (where status = 'ABSENT') as absent
                from attendance_records
                where attendance_date = :today
                """);
        long marked = number(today, "marked");
        long present = number(today, "present") + number(today, "late");
        return new DashboardDtos.Section("Attendance", "ATTENDANCE_READ", List.of(
                DashboardDtos.Tile.count("Marked today", marked,
                        "Attendance records dated today"),
                DashboardDtos.Tile.count("Present today", present, "Present or late"),
                DashboardDtos.Tile.count("Absent today", number(today, "absent"), "Marked absent"),
                DashboardDtos.Tile.rate("Present rate", present, marked,
                        "Percent of today's records marked present")));
    }

    private DashboardDtos.Section admissions(Map<String, Object> dates) {
        if (!auth.hasPermission("ADMISSION_READ")) {
            return null;
        }
        return new DashboardDtos.Section("Admissions", "ADMISSION_READ", List.of(
                DashboardDtos.Tile.count("Applications", count(dates,
                        "select count(*) from admission_applications"), "All applications"),
                DashboardDtos.Tile.count("Awaiting review", count(dates,
                        "select count(*) from admission_applications"
                                + " where status = 'SUBMITTED'"), "Submitted, not yet reviewed"),
                DashboardDtos.Tile.count("Approved", count(dates,
                        "select count(*) from admission_applications"
                                + " where status = 'APPROVED'"), "Approved applications"),
                DashboardDtos.Tile.count("This month", count(dates,
                        "select count(*) from admission_applications"
                                + " where submitted_at >= :from and submitted_at < :to"),
                        "Applications received this month")));
    }

    private DashboardDtos.Section fees(Map<String, Object> dates, LocalDate monthStart) {
        if (!auth.hasPermission("FINANCE_REPORT_READ")) {
            return null;
        }
        BigDecimal collected = scalar(dates,
                "select coalesce(sum(amount), 0) from payments"
                        + " where status = 'CONFIRMED' and received_at >= :from"
                        + " and received_at < :to");
        BigDecimal outstanding = scalar(dates,
                "select coalesce(sum(total_amount - paid_amount), 0) from invoices"
                        + " where status <> 'CANCELLED' and total_amount > paid_amount");
        return new DashboardDtos.Section("Money", "FINANCE_REPORT_READ", List.of(
                DashboardDtos.Tile.money("Collected this month", collected,
                        "Confirmed payments from " + monthStart),
                DashboardDtos.Tile.money("Outstanding", outstanding,
                        "Invoiced and not yet paid, all time"),
                DashboardDtos.Tile.count("Invoices unpaid", count(dates,
                        "select count(*) from invoices"
                                + " where status <> 'CANCELLED' and total_amount > paid_amount"),
                        "Invoices with a balance")));
    }

    private DashboardDtos.Section results() {
        if (!auth.hasPermission("RESULT_READ")) {
            return null;
        }
        Map<String, Object> none = new LinkedHashMap<>();
        Map<String, Object> published = one(none,
                """
                select count(*) as published,
                       count(*) filter (where is_pass) as passed
                from results
                where status = 'PUBLISHED'
                """);
        long total = number(published, "published");
        long passed = number(published, "passed");
        return new DashboardDtos.Section("Results", "RESULT_READ", List.of(
                DashboardDtos.Tile.count("Published results", total, "Published result records"),
                DashboardDtos.Tile.rate("Pass rate", passed, total,
                        "Percent of published results marked as passed")));
    }

    private DashboardDtos.Section staff() {
        if (!auth.hasPermission("EMPLOYEE_READ")) {
            return null;
        }
        Map<String, Object> none = new LinkedHashMap<>();
        return new DashboardDtos.Section("Staff", "EMPLOYEE_READ", List.of(
                DashboardDtos.Tile.count("On the books", count(none,
                        "select count(*) from employees where status = 'ACTIVE'"),
                        "Active employees"),
                DashboardDtos.Tile.count("On leave today", count(none,
                        """
                        select count(*) from leave_requests
                        where status = 'APPROVED'
                          and start_date <= current_date
                          and coalesce(end_date, start_date) >= current_date
                        """),
                        "Approved leave covering today"),
                DashboardDtos.Tile.count("Absent today", count(none,
                        "select count(*) from employee_attendance"
                                + " where attendance_date = current_date and status = 'ABSENT'"),
                        "Staff attendance marked absent today")));
    }

    private DashboardDtos.Section receivables() {
        Map<String, Object> none = new LinkedHashMap<>();
        return new DashboardDtos.Section("Receivables", "FINANCE_REPORT_READ", List.of(
                DashboardDtos.Tile.money("Total outstanding", scalar(none,
                        "select coalesce(sum(total_amount - paid_amount), 0) from invoices"
                                + " where status <> 'CANCELLED' and total_amount > paid_amount"),
                        "Invoiced and not yet paid"),
                DashboardDtos.Tile.count("Overdue invoices", count(none,
                        """
                        select count(*) from invoices
                        where status <> 'CANCELLED'
                          and total_amount > paid_amount
                          and coalesce(due_date, issue_date) < current_date
                        """),
                        "Past the due date"),
                DashboardDtos.Tile.money("Overdue amount", scalar(none,
                        """
                        select coalesce(sum(total_amount - paid_amount), 0) from invoices
                        where status <> 'CANCELLED'
                          and total_amount > paid_amount
                          and coalesce(due_date, issue_date) < current_date
                        """),
                        "Balance on invoices past the due date")));
    }

    private DashboardDtos.Section refunds() {
        if (!auth.hasPermission("PAYMENT_REFUND")) {
            return null;
        }
        Map<String, Object> none = new LinkedHashMap<>();
        return new DashboardDtos.Section("Refunds", "PAYMENT_REFUND", List.of(
                DashboardDtos.Tile.count("Awaiting approval", count(none,
                        "select count(*) from refunds where status = 'PENDING'"),
                        "Refund requests not yet decided"),
                DashboardDtos.Tile.money("Awaiting amount", scalar(none,
                        "select coalesce(sum(amount), 0) from refunds where status = 'PENDING'"),
                        "Value of refunds not yet decided"),
                DashboardDtos.Tile.count("Processed this month", count(none,
                        """
                        select count(*) from refunds
                        where status = 'COMPLETED'
                          and processed_at >= date_trunc('month', current_date)
                        """),
                        "Refunds completed this month")));
    }

    /**
     * What has been spent, from the ledger rather than from the fee tables.
     *
     * <p>Expenses are the debits on expense accounts: whatever a purchase was paid from, the
     * ledger is where it was recorded, and a figure taken from anywhere else would disagree with
     * the accounts sooner or later.
     */
    private DashboardDtos.Section expenses(LocalDate monthStart) {
        if (!auth.hasPermission("ACCOUNTING_READ")) {
            return null;
        }
        Map<String, Object> none = new LinkedHashMap<>();
        return new DashboardDtos.Section("Expenditure", "ACCOUNTING_READ", List.of(
                DashboardDtos.Tile.money("Expenses this month", scalar(none,
                        """
                        select coalesce(sum(l.debit), 0)
                        from journal_lines l
                        join chart_of_accounts a on a.id = l.account_id
                        join journal_entries e on e.id = l.journal_entry_id
                        where a.account_type = 'EXPENSE'
                          and e.status = 'POSTED'
                          and e.entry_date >= date_trunc('month', current_date)::date
                        """),
                        "Debits on expense accounts from " + monthStart),
                DashboardDtos.Tile.count("Entries posted this month", count(none,
                        """
                        select count(*) from journal_entries
                        where status = 'POSTED'
                          and entry_date >= date_trunc('month', current_date)::date
                        """),
                        "Posted journal entries this month")));
    }

    // ------------------------------------------------------------------ alerts

    /**
     * What is untidy, across the parts of the school that can be untidy.
     *
     * <p>Each alert says how many and how urgent, so a screen can put the one that matters first
     * rather than listing everything alphabetically.
     */
    private List<DashboardDtos.Alert> alerts() {
        List<DashboardDtos.Alert> alerts = new ArrayList<>();
        alerts.addAll(financeAlerts());

        if (auth.hasPermission("LIBRARY_READ") && institutions.isModuleEnabled(ModuleKey.LIBRARY)) {
            long overdue = count(new LinkedHashMap<>(),
                    """
                    select count(*) from library_issues
                    where returned_at is null and due_at < now()
                    """);
            if (overdue > 0) {
                alerts.add(new DashboardDtos.Alert("warning", "library",
                        "Books overdue", overdue));
            }
        }
        if (auth.hasPermission("ADMISSION_READ")) {
            long waiting = count(new LinkedHashMap<>(),
                    "select count(*) from admission_applications where status = 'SUBMITTED'");
            if (waiting > 0) {
                alerts.add(new DashboardDtos.Alert("warning", "admissions",
                        "Applications waiting for review", waiting));
            }
        }
        if (auth.hasPermission("INVENTORY_READ") && institutions.isModuleEnabled(ModuleKey.INVENTORY)) {
            long low = count(new LinkedHashMap<>(),
                    """
                    select count(*) from (
                        select i.id
                        from items i
                        left join stock st on st.item_id = i.id
                        where i.is_active
                        group by i.id, i.reorder_level
                        having coalesce(sum(st.quantity), 0) <= i.reorder_level
                    ) low
                    """);
            if (low > 0) {
                alerts.add(new DashboardDtos.Alert("warning", "inventory",
                        "Items at or below their reorder level", low));
            }
        }
        if (auth.hasPermission("DOCUMENT_READ") && institutions.isModuleEnabled(ModuleKey.DOCUMENTS)) {
            long expiring = count(new LinkedHashMap<>(),
                    """
                    select count(*) from documents
                    where deleted_at is null and expires_on is not null
                      and expires_on between current_date and current_date + 90
                    """);
            if (expiring > 0) {
                alerts.add(new DashboardDtos.Alert("warning", "documents",
                        "Documents expiring within 90 days", expiring));
            }
        }
        return alerts;
    }

    /** The finance alerts on their own, for the accountant. */
    private List<DashboardDtos.Alert> financeAlerts() {
        List<DashboardDtos.Alert> alerts = new ArrayList<>();
        Map<String, Object> none = new LinkedHashMap<>();
        long pendingRefunds = count(none,
                "select count(*) from refunds where status = 'PENDING'");
        if (pendingRefunds > 0 && auth.hasPermission("PAYMENT_REFUND")) {
            alerts.add(new DashboardDtos.Alert("warning", "refunds",
                    "Refund requests awaiting approval", pendingRefunds));
        }
        long overdue = count(none,
                """
                select count(*) from invoices
                where status <> 'CANCELLED'
                  and total_amount > paid_amount
                  and coalesce(due_date, issue_date) < current_date
                """);
        if (overdue > 0 && auth.hasPermission("INVOICE_READ")) {
            alerts.add(new DashboardDtos.Alert("action", "receivables",
                    "Invoices past their due date", overdue));
        }
        return alerts;
    }

    // ------------------------------------------------------------------ helpers

    private BigDecimal scalar(Map<String, Object> parameters, String sql) {
        Object value = one(parameters, sql).values().iterator().next();
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private long count(Map<String, Object> parameters, String sql) {
        return (long) scalar(parameters, sql).longValue();
    }

    private Map<String, Object> one(Map<String, Object> parameters, String sql) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, new MapSqlParameterSource(parameters));
        if (rows.isEmpty()) {
            throw AppException.rule("That figure could not be worked out.");
        }
        return rows.get(0);
    }

    private long number(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value instanceof Number number ? number.longValue() : 0L;
    }
}