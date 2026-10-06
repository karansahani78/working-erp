package com.educationerp.reporting;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.educationerp.reporting.ReportRegistry.classScoped;
import static com.educationerp.reporting.ReportRegistry.dates;
import static com.educationerp.reporting.ReportRegistry.department;
import static com.educationerp.reporting.ReportRegistry.student;
import static com.educationerp.reporting.ReportRegistry.term;

/**
 * The reports themselves.
 *
 * <p>Every definition is read-only SQL over the schema, bound through {@link ReportContext}. The
 * shape is the same everywhere: name the columns, run one query, put the rows in. Where a report
 * answers a question about a period, the period is a parameter and a report called without one
 * covers the current month rather than all of history, which is what a reader means by "this
 * month".
 *
 * <p>Nothing here writes, and nothing here trusts a caller for a scope: a report that shows
 * money is gated behind the finance permissions in {@link ReportDefinition}.
 */
@Component
public class ReportDefinitions {

    public List<ReportDefinition> definitions() {
        List<ReportDefinition> reports = new ArrayList<>();
        reports.addAll(studentReports());
        reports.addAll(attendanceReports());
        reports.addAll(examinationReports());
        reports.addAll(financeReports());
        reports.addAll(hrReports());
        reports.addAll(inventoryReports());
        reports.addAll(libraryReports());
        return reports;
    }

    // ---------------------------------------------------------------- students

    private List<ReportDefinition> studentReports() {
        String group = "Students";
        List<ReportDefinition> reports = new ArrayList<>();

        reports.add(new ReportDefinition("student-list", "Student list", group,
                "Every student on the roll, with class and contact details.", "REPORT_READ",
                Set.of("status", "classId"),
                context -> {
                    ReportTable table = new ReportTable("Student list", List.of(
                            "Student number", "Name", "Gender", "Date of birth", "Class",
                            "Enrolled", "Status", "Phone", "Email"));
                    MapSqlParameterSource source = new MapSqlParameterSource()
                            .addValue("status", context.optional("status"))
                            .addValue("classId", context.optional("classId"));
                    for (Map<String, Object> row : context.query("""
                            select s.student_number, s.first_name, s.last_name, s.gender,
                                   s.date_of_birth, c.name as class_name, s.enrollment_date,
                                   s.status, s.phone, s.email
                            from students s
                            left join enrollments e on e.student_id = s.id and e.status = 'ENROLLED'
                            left join school_classes c on c.id = e.school_class_id
                            where (:status::text is null or s.status = :status::text)
                              and (:classId::uuid is null or e.school_class_id = :classId::uuid)
                            order by s.student_number
                            """, source)) {
                        table.row(ReportContext.text(row, "student_number"),
                                ReportContext.text(row, "first_name") + " "
                                        + ReportContext.text(row, "last_name"),
                                ReportContext.text(row, "gender"),
                                ReportContext.text(row, "date_of_birth"),
                                ReportContext.text(row, "class_name"),
                                ReportContext.text(row, "enrollment_date"),
                                ReportContext.text(row, "status"),
                                ReportContext.text(row, "phone"),
                                ReportContext.text(row, "email"));
                    }
                    return table;
                }));

        reports.add(new ReportDefinition("student-demographics", "Demographic report", group,
                "Head counts by gender, age band and nationality.", "REPORT_READ", dates(),
                context -> {
                    ReportTable table = new ReportTable("Demographic report",
                            List.of("Measure", "Breakdown", "Students"));
                    context.query("""
                            select gender as measure, coalesce(gender, 'Not recorded') as breakdown,
                                   count(*) as students
                            from students where status = 'ACTIVE'
                            group by gender
                            """).forEach(row -> table.row(ReportContext.text(row, "measure"),
                            ReportContext.text(row, "breakdown"), ReportContext.number(row, "students")));
                    context.query("""
                            select 'Age band' as measure,
                                   case
                                     when date_of_birth is null then 'Not recorded'
                                     when date_of_birth > current_date - interval '16 years' then 'Under 16'
                                     when date_of_birth > current_date - interval '18 years' then '16 to 17'
                                     when date_of_birth > current_date - interval '25 years' then '18 to 24'
                                     when date_of_birth > current_date - interval '35 years' then '25 to 34'
                                     else '35 and over'
                                   end as breakdown,
                                   count(*) as students
                            from students where status = 'ACTIVE'
                            group by 2 order by 2
                            """).forEach(row -> table.row(ReportContext.text(row, "measure"),
                            ReportContext.text(row, "breakdown"), ReportContext.number(row, "students")));
                    context.query("""
                            select 'Nationality' as measure,
                                   coalesce(nationality, 'Not recorded') as breakdown,
                                   count(*) as students
                            from students where status = 'ACTIVE'
                            group by 2 order by 2
                            """).forEach(row -> table.row(ReportContext.text(row, "measure"),
                            ReportContext.text(row, "breakdown"), ReportContext.number(row, "students")));
                    return table;
                }));

        reports.add(new ReportDefinition("enrollment-report", "Enrollment report", group,
                "Enrollments per class against the capacity of that class.", "REPORT_READ", dates(),
                context -> {
                    ReportTable table = new ReportTable("Enrollment report", List.of(
                            "Class", "Capacity", "Enrolled", "Places left", "Fill rate"));
                    List<Map<String, Object>> rows = context.query("""
                            select c.name as class_name, coalesce(c.capacity, 0) as capacity,
                                   count(e.id) as enrolled
                            from school_classes c
                            left join enrollments e on e.school_class_id = c.id
                                 and e.status = 'ENROLLED'
                            where c.active = true
                            group by c.id, c.name, c.capacity
                            order by c.name
                            """);
                    BigDecimal capacity = BigDecimal.ZERO;
                    BigDecimal enrolled = BigDecimal.ZERO;
                    for (Map<String, Object> row : rows) {
                        long cap = ReportContext.number(row, "capacity");
                        long taken = ReportContext.number(row, "enrolled");
                        capacity = capacity.add(BigDecimal.valueOf(cap));
                        enrolled = enrolled.add(BigDecimal.valueOf(taken));
                        table.row(ReportContext.text(row, "class_name"), cap, taken,
                                Math.max(cap - taken, 0),
                                cap == 0 ? "No capacity set" : percent(taken, cap));
                    }
                    table.summary("Total capacity", capacity.longValue())
                            .summary("Total enrolled", enrolled.longValue());
                    return table;
                }));

        reports.add(new ReportDefinition("graduation-report", "Graduation report", group,
                "Who completed the requirements, and who is still short.", "REPORT_READ", dates(),
                context -> {
                    ReportTable table = new ReportTable("Graduation report", List.of(
                            "Student number", "Name", "Class", "Enrolment date", "Years enrolled"));
                    for (Map<String, Object> row : context.query("""
                            select s.student_number, s.first_name, s.last_name, c.name as class_name,
                                   s.enrollment_date,
                                   extract(year from age(current_date, s.enrollment_date))::int as years
                            from students s
                            join enrollments e on e.student_id = s.id
                            left join school_classes c on c.id = e.school_class_id
                            where s.status in ('ACTIVE', 'GRADUATED')
                            order by years desc nulls last, s.student_number
                            """)) {
                        table.row(ReportContext.text(row, "student_number"),
                                ReportContext.text(row, "first_name") + " "
                                        + ReportContext.text(row, "last_name"),
                                ReportContext.text(row, "class_name"),
                                ReportContext.text(row, "enrollment_date"),
                                ReportContext.value(row, "years"));
                    }
                    table.note("Years enrolled is measured from the enrolment date, not from the "
                            + "curriculum the student is following.");
                    return table;
                }));

        return reports;
    }

    // -------------------------------------------------------------- attendance

    private List<ReportDefinition> attendanceReports() {
        String group = "Attendance";
        List<ReportDefinition> reports = new ArrayList<>();

        reports.add(new ReportDefinition("attendance-daily", "Daily attendance", group,
                "Attendance for one day, by class and period.", "ATTENDANCE_READ",
                Set.of("date", "classId"),
                context -> {
                    LocalDate day = context.date("date", LocalDate.now());
                    ReportTable table = new ReportTable("Daily attendance for " + day, List.of(
                            "Class", "Period", "Present", "Absent", "Late", "Total",
                            "Attendance rate"));
                    MapSqlParameterSource source = new MapSqlParameterSource()
                            .addValue("day", day)
                            .addValue("classId", context.optional("classId"));
                    for (Map<String, Object> row : context.query("""
                            select coalesce(c.name, 'Unassigned') as class_name,
                                   coalesce(a.period_type, 'Any') as period_type,
                                   count(*) filter (where a.status = 'PRESENT') as present,
                                   count(*) filter (where a.status = 'ABSENT') as absent,
                                   count(*) filter (where a.status = 'LATE') as late,
                                   count(*) as total
                            from attendance_records a
                            join enrollments e on e.id = a.enrollment_id
                            left join school_classes c on c.id = e.school_class_id
                            where a.attendance_date = :day::date
                              and (:classId::uuid is null or e.school_class_id = :classId::uuid)
                            group by c.name, a.period_type
                            order by c.name, a.period_type
                            """, source)) {
                        long total = ReportContext.number(row, "total");
                        long present = ReportContext.number(row, "present")
                                + ReportContext.number(row, "late");
                        table.row(ReportContext.text(row, "class_name"),
                                ReportContext.text(row, "period_type"),
                                ReportContext.number(row, "present"),
                                ReportContext.number(row, "absent"),
                                ReportContext.number(row, "late"),
                                total,
                                total == 0 ? "No records" : percent(present, total));
                    }
                    return table;
                }));

        reports.add(new ReportDefinition("attendance-shortage", "Attendance shortage", group,
                "Students below the attendance threshold in the period.", "ATTENDANCE_READ", dates(),
                context -> {
                    ReportTable table = new ReportTable("Attendance shortage", List.of(
                            "Student number", "Name", "Class", "Held", "Present", "Absent",
                            "Attendance rate"));
                    List<Map<String, Object>> rows = context.query("""
                            select s.student_number, s.first_name, s.last_name,
                                   coalesce(c.name, 'Unassigned') as class_name,
                                   count(a.id) as held,
                                   count(a.id) filter (where a.status = 'PRESENT') as present,
                                   count(a.id) filter (where a.status = 'ABSENT') as absent
                            from students s
                            join enrollments e on e.student_id = s.id and e.status = 'ENROLLED'
                            left join school_classes c on c.id = e.school_class_id
                            join attendance_records a on a.enrollment_id = e.id
                                 and a.attendance_date between :from::date and :to::date
                            where s.status = 'ACTIVE'
                            group by s.id, s.student_number, s.first_name, s.last_name, c.name
                            having count(a.id) > 0
                               and count(a.id) filter (where a.status = 'PRESENT')
                                   < count(a.id) * 0.75
                            order by s.student_number
                            """, period(context));
                    for (Map<String, Object> row : rows) {
                        long held = ReportContext.number(row, "held");
                        table.row(ReportContext.text(row, "student_number"),
                                ReportContext.text(row, "first_name") + " "
                                        + ReportContext.text(row, "last_name"),
                                ReportContext.text(row, "class_name"), held,
                                ReportContext.number(row, "present"),
                                ReportContext.number(row, "absent"),
                                percent(ReportContext.number(row, "present"), held));
                    }
                    table.note("A student appears once they have been marked at least once; the "
                            + "rate below is present against all records in the period.");
                    return table;
                }));

        reports.add(new ReportDefinition("attendance-student", "Student attendance", group,
                "One student's attendance record across a period.", "ATTENDANCE_READ", student(),
                context -> {
                    ReportTable table = new ReportTable("Student attendance", List.of(
                            "Date", "Period", "Status", "Minutes late", "Remarks"));
                    MapSqlParameterSource source = period(context)
                            .addValue("studentId", context.required("studentId"));
                    for (Map<String, Object> row : context.query("""
                            select a.attendance_date, coalesce(a.period_type, 'Any') as period_type,
                                   a.status, coalesce(a.minutes_late, 0) as minutes_late, a.remarks
                            from attendance_records a
                            join enrollments e on e.id = a.enrollment_id
                            where e.student_id = :studentId::uuid
                              and a.attendance_date between :from::date and :to::date
                            order by a.attendance_date desc
                            """, source)) {
                        table.row(ReportContext.text(row, "attendance_date"),
                                ReportContext.text(row, "period_type"),
                                ReportContext.text(row, "status"),
                                ReportContext.number(row, "minutes_late"),
                                ReportContext.text(row, "remarks"));
                    }
                    return table;
                }));

        return reports;
    }

    // ------------------------------------------------------------- examination

    private List<ReportDefinition> examinationReports() {
        String group = "Examination";
        List<ReportDefinition> reports = new ArrayList<>();

        reports.add(new ReportDefinition("result-summary", "Result summary", group,
                "Published and pending results for an examination.", "RESULT_READ", term(),
                context -> {
                    ReportTable table = new ReportTable("Result summary", List.of(
                            "Student number", "Name", "Subject", "Marks", "Maximum",
                            "Percentage", "Grade", "Grade point", "Result", "Published"));
                    MapSqlParameterSource source = period(context)
                            .addValue("examId", context.optional("examId"));
                    for (Map<String, Object> row : context.query("""
                            select s.student_number, s.first_name, s.last_name,
                                   es.subject_name, r.marks_obtained, es.max_marks,
                                   r.percentage, r.letter_grade, r.grade_point, r.is_pass,
                                   r.published_at
                            from results r
                            join students s on s.id = r.student_id
                            join exam_subjects es on es.id = r.exam_subject_id
                            where (:examId::uuid is null or r.examination_id = :examId::uuid)
                              and (r.published_at between :from::date and :to::date or :examId::uuid is not null)
                            order by s.student_number, es.subject_name
                            """, source)) {
                        table.row(ReportContext.text(row, "student_number"),
                                ReportContext.text(row, "first_name") + " "
                                        + ReportContext.text(row, "last_name"),
                                ReportContext.text(row, "subject_name"),
                                ReportContext.text(row, "marks_obtained"),
                                ReportContext.text(row, "max_marks"),
                                ReportContext.text(row, "percentage"),
                                ReportContext.text(row, "letter_grade"),
                                ReportContext.text(row, "grade_point"),
                                ReportContext.bool(row, "is_pass"),
                                ReportContext.text(row, "published_at"));
                    }
                    return table;
                }));

        reports.add(new ReportDefinition("grade-sheet", "Grade sheet", group,
                "One student's grades across every subject of an examination.", "RESULT_READ",
                Set.of("studentId", "examId"),
                context -> {
                    ReportTable table = new ReportTable("Grade sheet", List.of(
                            "Subject", "Code", "Marks", "Maximum", "Percentage", "Grade",
                            "Grade point", "Result"));
                    MapSqlParameterSource source = new MapSqlParameterSource()
                            .addValue("studentId", context.required("studentId"))
                            .addValue("examId", context.required("examId"));
                    BigDecimal total = BigDecimal.ZERO;
                    BigDecimal maximum = BigDecimal.ZERO;
                    for (Map<String, Object> row : context.query("""
                            select es.subject_name, es.subject_code, r.marks_obtained, es.max_marks,
                                   r.percentage, r.letter_grade, r.grade_point, r.is_pass
                            from results r
                            join exam_subjects es on es.id = r.exam_subject_id
                            where r.student_id = :studentId::uuid and r.examination_id = :examId::uuid
                            order by es.subject_name
                            """, source)) {
                        total = total.add(ReportContext.decimal(row, "marks_obtained"));
                        maximum = maximum.add(ReportContext.decimal(row, "max_marks"));
                        table.row(ReportContext.text(row, "subject_name"),
                                ReportContext.text(row, "subject_code"),
                                ReportContext.text(row, "marks_obtained"),
                                ReportContext.text(row, "max_marks"),
                                ReportContext.text(row, "percentage"),
                                ReportContext.text(row, "letter_grade"),
                                ReportContext.text(row, "grade_point"),
                                ReportContext.bool(row, "is_pass"));
                    }
                    table.summary("Total marks", total)
                            .summary("Total maximum", maximum)
                            .summary("Aggregate", maximum.signum() == 0 ? "No marks"
                                    : percent(total.intValue(), maximum.intValue()));
                    return table;
                }));

        reports.add(new ReportDefinition("gpa-report", "GPA and CGPA", group,
                "Grade point averages per student for a period.", "RESULT_READ", dates(),
                context -> {
                    ReportTable table = new ReportTable("GPA and CGPA", List.of(
                            "Student number", "Name", "Subjects", "GPA", "CGPA", "Band"));
                    for (Map<String, Object> row : context.query("""
                            select s.student_number, s.first_name, s.last_name,
                                   count(r.id) as subjects,
                                   round(avg(r.grade_point), 2) as gpa,
                                   (select round(avg(h.grade_point), 2)
                                      from results h
                                     where h.student_id = s.id
                                       and h.grade_point is not null
                                       and h.published_at is not null) as cgpa,
                                   case
                                     when avg(r.grade_point) >= 3.5 then 'Distinction'
                                     when avg(r.grade_point) >= 2.5 then 'Merit'
                                     when avg(r.grade_point) >= 1.5 then 'Pass'
                                     else 'Fail'
                                   end as band
                            from results r
                            join students s on s.id = r.student_id
                            where r.grade_point is not null
                              and r.published_at >= :from::date
                              and r.published_at < :to::date
                            group by s.id, s.student_number, s.first_name, s.last_name
                            order by gpa desc nulls last, s.student_number
                            """, period(context))) {
                        table.row(ReportContext.text(row, "student_number"),
                                ReportContext.text(row, "first_name") + " "
                                        + ReportContext.text(row, "last_name"),
                                ReportContext.number(row, "subjects"),
                                ReportContext.text(row, "gpa"),
                                ReportContext.text(row, "cgpa"),
                                ReportContext.text(row, "band"));
                    }
                    return table;
                }));

        return reports;
    }

    // ----------------------------------------------------------------- finance

    private List<ReportDefinition> financeReports() {
        String group = "Finance";
        List<ReportDefinition> reports = new ArrayList<>();

        reports.add(new ReportDefinition("fee-collections", "Fee collections", group,
                "Money received per method, with what is still outstanding.", "FINANCE_REPORT_READ",
                dates(),
                context -> {
                    ReportTable table = new ReportTable("Fee collections", List.of(
                            "Method", "Receipts", "Received", "Average receipt"));
                    BigDecimal total = BigDecimal.ZERO;
                    for (Map<String, Object> row : context.query("""
                            select method, count(*) as receipts, sum(amount) as received,
                                   round(avg(amount), 2) as average
                            from payments
                            where status = 'CONFIRMED'
                              and received_at::date >= :from::date
                              and received_at::date < :to::date
                            group by method order by sum(amount) desc
                            """, period(context))) {
                        total = total.add(ReportContext.decimal(row, "received"));
                        table.row(ReportContext.text(row, "method"),
                                ReportContext.number(row, "receipts"),
                                ReportContext.text(row, "received"),
                                ReportContext.text(row, "average"));
                    }
                    table.summary("Total received", total);
                    return table;
                }));

        reports.add(new ReportDefinition("fee-outstanding", "Outstanding fees", group,
                "Invoices unpaid, oldest first.", "FINANCE_REPORT_READ", dates(),
                context -> {
                    ReportTable table = new ReportTable("Outstanding fees", List.of(
                            "Invoice", "Student", "Class", "Issued", "Due", "Total", "Paid",
                            "Outstanding", "Days late"));
                    BigDecimal outstanding = BigDecimal.ZERO;
                    for (Map<String, Object> row : context.query("""
                            select i.invoice_number, s.student_number, s.first_name, s.last_name,
                                   coalesce(c.name, '') as class_name, i.issue_date, i.due_date,
                                   i.total_amount, i.paid_amount,
                                   i.total_amount - i.paid_amount as outstanding,
                                   greatest(current_date - i.due_date, 0) as days_late
                            from invoices i
                            join students s on s.id = i.student_id
                            left join enrollments e on e.student_id = s.id and e.status = 'ENROLLED'
                            left join school_classes c on c.id = e.school_class_id
                            where i.status in ('ISSUED', 'PARTIALLY_PAID', 'OVERDUE')
                              and i.total_amount > i.paid_amount
                            order by i.due_date
                            """)) {
                        outstanding = outstanding.add(ReportContext.decimal(row, "outstanding"));
                        table.row(ReportContext.text(row, "invoice_number"),
                                ReportContext.text(row, "student_number") + " "
                                        + ReportContext.text(row, "first_name") + " "
                                        + ReportContext.text(row, "last_name"),
                                ReportContext.text(row, "class_name"),
                                ReportContext.text(row, "issue_date"),
                                ReportContext.text(row, "due_date"),
                                ReportContext.text(row, "total_amount"),
                                ReportContext.text(row, "paid_amount"),
                                ReportContext.text(row, "outstanding"),
                                ReportContext.number(row, "days_late"));
                    }
                    table.summary("Outstanding total", outstanding);
                    return table;
                }));

        reports.add(new ReportDefinition("payment-register", "Payment register", group,
                "Every confirmed receipt in the period.", "PAYMENT_READ", dates(),
                context -> {
                    ReportTable table = new ReportTable("Payment register", List.of(
                            "Receipt", "Student", "Invoice", "Amount", "Method", "Provider",
                            "Reference", "Received"));
                    for (Map<String, Object> row : context.query("""
                            select p.receipt_number, s.student_number, s.first_name, s.last_name,
                                   coalesce(i.invoice_number, '') as invoice_number, p.amount,
                                   p.method, coalesce(p.provider, '') as provider,
                                   coalesce(p.reference, '') as reference, p.received_at
                            from payments p
                            join students s on s.id = p.student_id
                            left join invoices i on i.id = p.invoice_id
                            where p.received_at::date >= :from::date
                              and p.received_at::date < :to::date
                            order by p.received_at desc
                            """, period(context))) {
                        table.row(ReportContext.text(row, "receipt_number"),
                                ReportContext.text(row, "student_number") + " "
                                        + ReportContext.text(row, "first_name") + " "
                                        + ReportContext.text(row, "last_name"),
                                ReportContext.text(row, "invoice_number"),
                                ReportContext.text(row, "amount"),
                                ReportContext.text(row, "method"),
                                ReportContext.text(row, "provider"),
                                ReportContext.text(row, "reference"),
                                ReportContext.text(row, "received_at"));
                    }
                    return table;
                }));

        reports.add(new ReportDefinition("refund-register", "Refund register", group,
                "Refunds requested and what became of them.", "PAYMENT_REFUND", dates(),
                context -> {
                    ReportTable table = new ReportTable("Refund register", List.of(
                            "Student", "Receipt", "Amount", "Reason", "Method", "Status",
                            "Requested", "Processed"));
                    for (Map<String, Object> row : context.query("""
                            select s.student_number, s.first_name, s.last_name,
                                   p.receipt_number, r.amount, r.reason, r.method, r.status,
                                   r.created_at, r.processed_at
                            from refunds r
                            join students s on s.id = r.student_id
                            left join payments p on p.id = r.payment_id
                            where r.created_at::date >= :from::date
                              and r.created_at::date < :to::date
                            order by r.created_at desc
                            """, period(context))) {
                        table.row(ReportContext.text(row, "student_number") + " "
                                        + ReportContext.text(row, "first_name") + " "
                                        + ReportContext.text(row, "last_name"),
                                ReportContext.text(row, "receipt_number"),
                                ReportContext.text(row, "amount"),
                                ReportContext.text(row, "reason"),
                                ReportContext.text(row, "method"),
                                ReportContext.text(row, "status"),
                                ReportContext.text(row, "created_at"),
                                ReportContext.text(row, "processed_at"));
                    }
                    return table;
                }));

        reports.add(new ReportDefinition("receivables-ageing", "Receivables ageing", group,
                "What is owed, and how long it has been owed for.", "FINANCE_REPORT_READ", dates(),
                context -> {
                    ReportTable table = new ReportTable("Receivables ageing", List.of(
                            "Age", "Invoices", "Outstanding"));
                    // The bands are what a finance office actually chases on, so they are fixed
                    // rather than configurable: a report whose bands move cannot be compared to
                    // last month's.
                    String[] bands = {"Current", "1 to 30 days", "31 to 60 days",
                            "61 to 90 days", "Over 90 days"};
                    String[] predicates = {
                            "coalesce(i.due_date, i.issue_date) >= current_date",
                            "coalesce(i.due_date, i.issue_date) < current_date"
                                    + " and coalesce(i.due_date, i.issue_date) >= current_date - 30",
                            "coalesce(i.due_date, i.issue_date) < current_date - 30"
                                    + " and coalesce(i.due_date, i.issue_date) >= current_date - 60",
                            "coalesce(i.due_date, i.issue_date) < current_date - 60"
                                    + " and coalesce(i.due_date, i.issue_date) >= current_date - 90",
                            "coalesce(i.due_date, i.issue_date) < current_date - 90"};
                    for (int index = 0; index < bands.length; index++) {
                        // One query per band. A single pass with a case expression would be
                        // shorter, but the bands would then have to be repeated in SQL and in
                        // the label list, and the two would eventually disagree.
                        String sql = """
                                select count(*) as invoices,
                                       coalesce(sum(i.total_amount - i.paid_amount), 0) as outstanding
                                from invoices i
                                where i.status in ('ISSUED', 'PARTIALLY_PAID', 'OVERDUE')
                                  and i.total_amount > i.paid_amount
                                  and %s
                                """.formatted(predicates[index]);
                        Map<String, Object> row = context.query(sql).get(0);
                        table.row(bands[index], ReportContext.number(row, "invoices"),
                                ReportContext.text(row, "outstanding"));
                    }
                    return table;
                }));

        reports.add(new ReportDefinition("fee-defaulters", "Fee defaulters", group,
                "Students with an unpaid invoice past its due date.", "FINANCE_REPORT_READ", dates(),
                context -> {
                    ReportTable table = new ReportTable("Fee defaulters", List.of(
                            "Student number", "Name", "Class", "Invoices", "Outstanding",
                            "Oldest due"));
                    for (Map<String, Object> row : context.query("""
                            select s.student_number, s.first_name, s.last_name,
                                   coalesce(max(c.name), 'Unassigned') as class_name,
                                   count(i.id) as invoices,
                                   sum(i.total_amount - i.paid_amount) as outstanding,
                                   min(i.due_date) as oldest_due
                            from students s
                            join invoices i on i.student_id = s.id
                                 and i.total_amount > i.paid_amount
                                 and coalesce(i.due_date, i.issue_date) < current_date
                            left join enrollments e on e.student_id = s.id and e.status = 'ENROLLED'
                            left join school_classes c on c.id = e.school_class_id
                            where s.status = 'ACTIVE'
                            group by s.id, s.student_number, s.first_name, s.last_name
                            order by outstanding desc
                            """)) {
                        table.row(ReportContext.text(row, "student_number"),
                                ReportContext.text(row, "first_name") + " "
                                        + ReportContext.text(row, "last_name"),
                                ReportContext.text(row, "class_name"),
                                ReportContext.number(row, "invoices"),
                                ReportContext.text(row, "outstanding"),
                                ReportContext.text(row, "oldest_due"));
                    }
                    return table;
                }));

        return reports;
    }

    // --------------------------------------------------------------------- HR

    private List<ReportDefinition> hrReports() {
        String group = "HR";
        List<ReportDefinition> reports = new ArrayList<>();

        reports.add(new ReportDefinition("employee-list", "Employee list", group,
                "Staff on the books, with department and designation.", "EMPLOYEE_READ", dates(),
                context -> {
                    ReportTable table = new ReportTable("Employee list", List.of(
                            "Employee code", "Name", "Department", "Designation", "Type",
                            "Joined", "Status"));
                    for (Map<String, Object> row : context.query("""
                            select e.employee_code, e.first_name, e.last_name,
                                   coalesce(d.name, '') as department,
                                   coalesce(g.name, '') as designation, e.employment_type,
                                   e.join_date, e.status
                            from employees e
                            left join departments d on d.id = e.department_id
                            left join designations g on g.id = e.designation_id
                            order by e.employee_code
                            """)) {
                        table.row(ReportContext.text(row, "employee_code"),
                                ReportContext.text(row, "first_name") + " "
                                        + ReportContext.text(row, "last_name"),
                                ReportContext.text(row, "department"),
                                ReportContext.text(row, "designation"),
                                ReportContext.text(row, "employment_type"),
                                ReportContext.text(row, "join_date"),
                                ReportContext.text(row, "status"));
                    }
                    return table;
                }));

        reports.add(new ReportDefinition("employee-attendance", "Employee attendance", group,
                "Attendance for one month across all staff.", "ATTENDANCE_EMPLOYEE_READ",
                Set.of("month", "departmentId"),
                context -> {
                    String month = context.optional("month") == null
                            ? LocalDate.now().toString().substring(0, 7)
                            : context.required("month");
                    ReportTable table = new ReportTable("Employee attendance for " + month, List.of(
                            "Employee code", "Name", "Department", "Present", "Absent", "Leave",
                            "Late days", "Overtime minutes"));
                    MapSqlParameterSource source = new MapSqlParameterSource()
                            .addValue("month", month + "-01")
                            .addValue("departmentId", context.optional("departmentId"));
                    for (Map<String, Object> row : context.query("""
                            select e.employee_code, e.first_name, e.last_name,
                                   coalesce(d.name, '') as department,
                                   count(a.id) filter (where a.status = 'PRESENT') as present,
                                   count(a.id) filter (where a.status = 'ABSENT') as absent,
                                   count(a.id) filter (where a.status = 'LEAVE') as on_leave,
                                   count(a.id) filter (where a.status = 'LATE') as late,
                                   coalesce(sum(a.overtime_minutes), 0) as overtime
                            from employees e
                            join employee_attendance a on a.employee_id = e.id
                                 and a.attendance_date >= :month::date
                                 and a.attendance_date < (:month::date + interval '1 month')
                            left join departments d on d.id = e.department_id
                            where (:departmentId::uuid is null or e.department_id = :departmentId::uuid)
                            group by e.id, e.employee_code, e.first_name, e.last_name, d.name
                            order by e.employee_code
                            """, source)) {
                        table.row(ReportContext.text(row, "employee_code"),
                                ReportContext.text(row, "first_name") + " "
                                        + ReportContext.text(row, "last_name"),
                                ReportContext.text(row, "department"),
                                ReportContext.number(row, "present"),
                                ReportContext.number(row, "absent"),
                                ReportContext.number(row, "on_leave"),
                                ReportContext.number(row, "late"),
                                ReportContext.number(row, "overtime"));
                    }
                    return table;
                }));

        reports.add(new ReportDefinition("leave-report", "Leave report", group,
                "Leave taken and still pending.", "LEAVE_READ", dates(),
                context -> {
                    ReportTable table = new ReportTable("Leave report", List.of(
                            "Employee", "Type", "From", "To", "Days", "Reason", "Status",
                            "Decided on"));
                    for (Map<String, Object> row : context.query("""
                            select e.employee_code, e.first_name, e.last_name,
                                   coalesce(t.name, '') as leave_type, l.start_date, l.end_date,
                                   l.days, l.reason, l.status, l.decided_at
                            from leave_requests l
                            join employees e on e.id = l.employee_id
                            left join leave_types t on t.id = l.leave_type_id
                            where l.start_date <= :to::date and coalesce(l.end_date, l.start_date) >= :from::date
                            order by l.start_date desc
                            """, period(context))) {
                        table.row(ReportContext.text(row, "employee_code") + " "
                                        + ReportContext.text(row, "first_name") + " "
                                        + ReportContext.text(row, "last_name"),
                                ReportContext.text(row, "leave_type"),
                                ReportContext.text(row, "start_date"),
                                ReportContext.text(row, "end_date"),
                                ReportContext.number(row, "days"),
                                ReportContext.text(row, "reason"),
                                ReportContext.text(row, "status"),
                                ReportContext.text(row, "decided_at"));
                    }
                    return table;
                }));

        reports.add(new ReportDefinition("payroll-register", "Payroll register", group,
                "One payroll run, line by line.", "PAYROLL_READ", Set.of("payrollRunId"),
                context -> {
                    ReportTable table = new ReportTable("Payroll register", List.of(
                            "Employee", "Basic", "Allowances", "Overtime", "Bonus", "Loans",
                            "Tax", "Gross", "Net", "Status"));
                    BigDecimal net = BigDecimal.ZERO;
                    MapSqlParameterSource source = new MapSqlParameterSource()
                            .addValue("payrollRunId", context.required("payrollRunId"));
                    for (Map<String, Object> row : context.query("""
                            select e.employee_code, e.first_name, e.last_name, p.basic_salary,
                                   p.total_allowances, p.overtime_amount, p.bonus,
                                   p.loan_deduction, p.tax, p.gross_salary, p.net_salary,
                                   p.status
                            from payslips p
                            join employees e on e.id = p.employee_id
                            where p.payroll_run_id = :payrollRunId::uuid
                            order by e.employee_code
                            """, source)) {
                        net = net.add(ReportContext.decimal(row, "net_salary"));
                        table.row(ReportContext.text(row, "employee_code") + " "
                                        + ReportContext.text(row, "first_name") + " "
                                        + ReportContext.text(row, "last_name"),
                                ReportContext.text(row, "basic_salary"),
                                ReportContext.text(row, "total_allowances"),
                                ReportContext.text(row, "overtime_amount"),
                                ReportContext.text(row, "bonus"),
                                ReportContext.text(row, "loan_deduction"),
                                ReportContext.text(row, "tax"),
                                ReportContext.text(row, "gross_salary"),
                                ReportContext.text(row, "net_salary"),
                                ReportContext.text(row, "status"));
                    }
                    table.summary("Net paid", net);
                    return table;
                }));

        return reports;
    }

    // --------------------------------------------------------------- inventory

    private List<ReportDefinition> inventoryReports() {
        String group = "Inventory";
        List<ReportDefinition> reports = new ArrayList<>();

        reports.add(new ReportDefinition("stock-report", "Stock report", group,
                "What is held, by store, valued at the running average cost.", "INVENTORY_READ",
                Set.of("storeId"),
                context -> {
                    ReportTable table = new ReportTable("Stock report", List.of(
                            "Item code", "Item", "Category", "Store", "Quantity", "Unit",
                            "Average cost", "Value", "Last movement"));
                    BigDecimal value = BigDecimal.ZERO;
                    MapSqlParameterSource source = new MapSqlParameterSource()
                            .addValue("storeId", context.optional("storeId"));
                    for (Map<String, Object> row : context.query("""
                            select i.code as item_code, i.name as item_name,
                                   coalesce(c.name, '') as category, s.name as store_name,
                                   st.quantity, i.unit, st.average_cost,
                                   round(coalesce(st.average_cost, 0) * st.quantity, 2) as value,
                                   st.last_movement_at
                            from stock st
                            join items i on i.id = st.item_id
                            join stores s on s.id = st.store_id
                            left join item_categories c on c.id = i.category_id
                            where (:storeId::uuid is null or st.store_id = :storeId::uuid)
                              and st.quantity <> 0
                            order by i.name, s.name
                            """, source)) {
                        value = value.add(ReportContext.decimal(row, "value"));
                        table.row(ReportContext.text(row, "item_code"),
                                ReportContext.text(row, "item_name"),
                                ReportContext.text(row, "category"),
                                ReportContext.text(row, "store_name"),
                                ReportContext.text(row, "quantity"),
                                ReportContext.text(row, "unit"),
                                ReportContext.text(row, "average_cost"),
                                ReportContext.text(row, "value"),
                                ReportContext.text(row, "last_movement_at"));
                    }
                    table.summary("Stock value", value);
                    return table;
                }));

        reports.add(new ReportDefinition("stock-movement-report", "Stock movement report", group,
                "Every movement in a period, with the balance it left behind.", "INVENTORY_READ",
                Set.of("from", "to", "storeId"),
                context -> {
                    ReportTable table = new ReportTable("Stock movement report", List.of(
                            "When", "Item", "Type", "Quantity", "Change", "Balance after",
                            "Unit cost", "Reason", "Reference"));
                    MapSqlParameterSource source = new MapSqlParameterSource()
                            .addValue("from", context.from())
                            .addValue("to", context.to())
                            .addValue("storeId", context.optional("storeId"));
                    for (Map<String, Object> row : context.query("""
                            select m.moved_at, i.name as item_name, m.movement_type, m.quantity,
                                   case when m.movement_type in ('PURCHASE','RECEIPT','RETURN','TRANSFER_IN')
                                        then m.quantity
                                        when m.movement_type = 'ADJUSTMENT' and m.quantity >= 0
                                        then m.quantity
                                        else -m.quantity
                                   end as change,
                                   m.balance_after, m.unit_cost, m.reason,
                                   coalesce(m.reference_type, '') as reference_type
                            from stock_movements m
                            join items i on i.id = m.item_id
                            where m.moved_at between :from::date and :to::date
                              and (:storeId::uuid is null or m.store_id = :storeId::uuid)
                            order by m.moved_at desc
                            limit 5000
                            """, source)) {
                        table.row(ReportContext.text(row, "moved_at"),
                                ReportContext.text(row, "item_name"),
                                ReportContext.text(row, "movement_type"),
                                ReportContext.text(row, "quantity"),
                                ReportContext.text(row, "change"),
                                ReportContext.text(row, "balance_after"),
                                ReportContext.text(row, "unit_cost"),
                                ReportContext.text(row, "reason"),
                                ReportContext.text(row, "reference_type"));
                    }
                    table.note("Capped at 5000 movements; narrow the dates or the store for a "
                            + "longer period.");
                    return table;
                }));

        reports.add(new ReportDefinition("low-stock-report", "Low stock report", group,
                "Items at or below their reorder level.", "INVENTORY_READ", Set.of(),
                context -> {
                    ReportTable table = new ReportTable("Low stock report", List.of(
                            "Item code", "Item", "Category", "On hand", "Reorder level",
                            "Reorder quantity", "Suggested order"));
                    for (Map<String, Object> row : context.query("""
                            select i.code as item_code, i.name as item_name,
                                   coalesce(c.name, '') as category,
                                   coalesce(sum(st.quantity), 0) as on_hand, i.reorder_level,
                                   i.reorder_quantity, i.unit
                            from items i
                            left join stock st on st.item_id = i.id
                            left join item_categories c on c.id = i.category_id
                            where i.is_active = true
                            group by i.id, i.code, i.name, c.name, i.reorder_level,
                                     i.reorder_quantity, i.unit
                            having coalesce(sum(st.quantity), 0) <= i.reorder_level
                            order by on_hand, i.name
                            """)) {
                        table.row(ReportContext.text(row, "item_code"),
                                ReportContext.text(row, "item_name"),
                                ReportContext.text(row, "category"),
                                ReportContext.text(row, "on_hand"),
                                ReportContext.text(row, "reorder_level"),
                                ReportContext.text(row, "reorder_quantity"),
                                ReportContext.text(row, "reorder_quantity") + " "
                                        + ReportContext.text(row, "unit"));
                    }
                    return table;
                }));

        reports.add(new ReportDefinition("purchase-report", "Purchase report", group,
                "Orders placed with suppliers, and what arrived.", "INVENTORY_READ", dates(),
                context -> {
                    ReportTable table = new ReportTable("Purchase report", List.of(
                            "Purchase", "Supplier", "Store", "Ordered", "Expected", "Received",
                            "Status", "Subtotal", "Tax", "Total"));
                    for (Map<String, Object> row : context.query("""
                            select p.purchase_number, p.supplier_name, s.name as store_name,
                                   p.ordered_on, p.expected_on, p.received_on, p.status,
                                   p.subtotal, p.tax_amount, p.total_amount
                            from purchases p
                            join stores s on s.id = p.store_id
                            where p.ordered_on between :from::date and :to::date
                            order by p.ordered_on desc
                            """, period(context))) {
                        table.row(ReportContext.text(row, "purchase_number"),
                                ReportContext.text(row, "supplier_name"),
                                ReportContext.text(row, "store_name"),
                                ReportContext.text(row, "ordered_on"),
                                ReportContext.text(row, "expected_on"),
                                ReportContext.text(row, "received_on"),
                                ReportContext.text(row, "status"),
                                ReportContext.text(row, "subtotal"),
                                ReportContext.text(row, "tax_amount"),
                                ReportContext.text(row, "total_amount"));
                    }
                    return table;
                }));

        reports.add(new ReportDefinition("stock-issue-report", "Stock issue report", group,
                "Who took what, and whether it came back.", "INVENTORY_READ", dates(),
                context -> {
                    ReportTable table = new ReportTable("Stock issue report", List.of(
                            "Issue", "Item", "Quantity", "Taken by", "Kind", "Store", "Issued",
                            "Returned", "Status"));
                    for (Map<String, Object> row : context.query("""
                            select x.issue_number, i.name as item_name, x.quantity,
                                   x.issued_to_name, x.issued_to_type, s.name as store_name,
                                   x.issued_at, x.returned_at, x.status
                            from stock_issues x
                            join items i on i.id = x.item_id
                            join stores s on s.id = x.store_id
                            where x.issued_at between :from::date and :to::date
                            order by x.issued_at desc
                            """, period(context))) {
                        table.row(ReportContext.text(row, "issue_number"),
                                ReportContext.text(row, "item_name"),
                                ReportContext.text(row, "quantity"),
                                ReportContext.text(row, "issued_to_name"),
                                ReportContext.text(row, "issued_to_type"),
                                ReportContext.text(row, "store_name"),
                                ReportContext.text(row, "issued_at"),
                                ReportContext.text(row, "returned_at"),
                                ReportContext.text(row, "status"));
                    }
                    return table;
                }));

        return reports;
    }

    // ----------------------------------------------------------------- library

    private List<ReportDefinition> libraryReports() {
        String group = "Library";
        List<ReportDefinition> reports = new ArrayList<>();

        reports.add(new ReportDefinition("library-circulation", "Circulation report", group,
                "Books lent in a period, and what is still out.", "LIBRARY_READ", dates(),
                context -> {
                    ReportTable table = new ReportTable("Circulation report", List.of(
                            "Barcode", "Title", "Member", "Member type", "Issued", "Due",
                            "Returned", "Status", "Extensions"));
                    for (Map<String, Object> row : context.query("""
                            select bc.barcode, b.title, m.member_code,
                                   case
                                     when m.student_id is not null then 'Student'
                                     when m.employee_id is not null then 'Staff'
                                     else 'External'
                                   end as member_type,
                                   l.issued_at,
                                   l.due_at, l.returned_at, l.status, l.renewal_count
                            from library_issues l
                            join book_copies bc on bc.id = l.copy_id
                            join books b on b.id = bc.book_id
                            join library_members m on m.id = l.member_id
                            where l.issued_at between :from::date and :to::date
                            order by l.issued_at desc
                            """, period(context))) {
                        table.row(ReportContext.text(row, "barcode"),
                                ReportContext.text(row, "title"),
                                ReportContext.text(row, "member_code"),
                                ReportContext.text(row, "member_type"),
                                ReportContext.text(row, "issued_at"),
                                ReportContext.text(row, "due_at"),
                                ReportContext.text(row, "returned_at"),
                                ReportContext.text(row, "status"),
                                ReportContext.number(row, "renewal_count"));
                    }
                    return table;
                }));

        reports.add(new ReportDefinition("library-overdue", "Overdue books", group,
                "What is late, and how late.", "LIBRARY_READ", dates(),
                context -> {
                    ReportTable table = new ReportTable("Overdue books", List.of(
                            "Barcode", "Title", "Member", "Due", "Days late", "Fines owed"));
                    for (Map<String, Object> row : context.query("""
                            select bc.barcode, b.title, m.member_code, l.due_at,
                                   (current_date - l.due_at::date) as days_late,
                                   coalesce(f.amount, 0) as fine
                            from library_issues l
                            join book_copies bc on bc.id = l.copy_id
                            join books b on b.id = bc.book_id
                            join library_members m on m.id = l.member_id
                            left join library_fines f on f.issue_id = l.id
                                 and f.status = 'OUTSTANDING'
                            where l.returned_at is null and l.due_at < now()
                            order by l.due_at
                            """)) {
                        table.row(ReportContext.text(row, "barcode"),
                                ReportContext.text(row, "title"),
                                ReportContext.text(row, "member_code"),
                                ReportContext.text(row, "due_at"),
                                ReportContext.number(row, "days_late"),
                                ReportContext.text(row, "fine"));
                    }
                    return table;
                }));

        reports.add(new ReportDefinition("library-fines", "Fines report", group,
                "Fines raised, paid and waived.", "LIBRARY_READ", dates(),
                context -> {
                    ReportTable table = new ReportTable("Fines report", List.of(
                            "Member", "Reason", "Amount", "Status", "Raised", "Waived on",
                            "Waiver reason"));
                    for (Map<String, Object> row : context.query("""
                            select m.member_code, f.reason, f.amount, f.status, f.created_at,
                                   f.waived_at, f.waiver_reason
                            from library_fines f
                            join library_members m on m.id = f.member_id
                            where f.created_at between :from::date and :to::date
                            order by f.created_at desc
                            """, period(context))) {
                        table.row(ReportContext.text(row, "member_code"),
                                ReportContext.text(row, "reason"),
                                ReportContext.text(row, "amount"),
                                ReportContext.text(row, "status"),
                                ReportContext.text(row, "created_at"),
                                ReportContext.text(row, "waived_at"),
                                ReportContext.text(row, "waiver_reason"));
                    }
                    return table;
                }));

        reports.add(new ReportDefinition("asset-register", "Asset register", group,
                "Everything owned, where it is and who has it.", "ASSET_READ", Set.of("status"),
                context -> {
                    ReportTable table = new ReportTable("Asset register", List.of(
                            "Asset number", "Name", "Category", "Serial", "Location",
                            "Department", "Status", "Condition", "Holder", "Purchased", "Cost"));
                    for (Map<String, Object> row : context.query("""
                            select a.asset_number, a.name as asset_name,
                                   coalesce(c.name, '') as category, coalesce(a.serial_number, ''),
                                   coalesce(a.location, ''), coalesce(d.name, '') as department,
                                   a.status, a.condition_status,
                                   coalesce(e.first_name || ' ' || e.last_name, '') as holder,
                                   a.purchase_date, a.purchase_cost
                            from assets a
                            left join asset_categories c on c.id = a.category_id
                            left join departments d on d.id = a.department_id
                            left join employees e on e.id = a.assigned_employee_id
                            where (:status::text is null or a.status = :status::text)
                            order by a.name
                            """, new MapSqlParameterSource()
                            .addValue("status", context.optional("status")))) {
                        table.row(ReportContext.text(row, "asset_number"),
                                ReportContext.text(row, "asset_name"),
                                ReportContext.text(row, "category"),
                                ReportContext.text(row, "serial_number"),
                                ReportContext.text(row, "location"),
                                ReportContext.text(row, "department"),
                                ReportContext.text(row, "status"),
                                ReportContext.text(row, "condition_status"),
                                ReportContext.text(row, "holder"),
                                ReportContext.text(row, "purchase_date"),
                                ReportContext.text(row, "purchase_cost"));
                    }
                    return table;
                }));

        return reports;
    }

    // ----------------------------------------------------------------- helpers

    /** A share as a reader wants it, with the awkward divisions named rather than hidden. */
    private static String percent(long part, long whole) {
        if (whole == 0) {
            return "n/a";
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(whole), 1, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "%";
    }

    /** The period parameters, bound. */
    private static MapSqlParameterSource period(ReportContext context) {
        return new MapSqlParameterSource()
                .addValue("from", context.from())
                .addValue("to", context.to().plusDays(1));
    }
}
