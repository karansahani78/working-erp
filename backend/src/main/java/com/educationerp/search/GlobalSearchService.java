package com.educationerp.search;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.institution.InstitutionService;
import com.educationerp.institution.ModuleKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * One search box across the records a school keeps.
 *
 * <p>Each kind of record has its own query and, more importantly, its own permission. Search does
 * not widen what a caller can see: a teacher who may look up a student gets students, and
 * nothing else, because the results are collected from separate queries that each ask permission
 * first. Searching for a term that happens to match an invoice number cannot surface the invoice
 * to somebody without {@code INVOICE_READ}.
 *
 * <p>A term is bound as a value and never assembled into SQL, so a search for {@code %} is a
 * search for the percent sign rather than for everything.
 */
@Slf4j
@Service
public class GlobalSearchService {

    /** Enough to be useful without letting one kind of record bury the rest. */
    private static final int PER_SOURCE = 5;
    private static final int MAX_TERM_LENGTH = 120;
    private static final int MIN_TERM_LENGTH = 2;

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationChecker auth;
    private final InstitutionService institutions;

    public GlobalSearchService(NamedParameterJdbcTemplate jdbc, AuthorizationChecker auth,
                               InstitutionService institutions) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.institutions = institutions;
    }

    /**
     * Search every kind of record this caller may read.
     *
     * <p>A source that fails is left out rather than failing the search: one missing table must
     * not stop a clerk finding a student's record because an optional module is switched off.
     */
    @Transactional(readOnly = true)
    public List<SearchDtos.SearchHit> search(String term) {
        auth.requirePermission("SEARCH_GLOBAL");
        String cleaned = cleaned(term);
        List<SearchDtos.SearchHit> hits = new ArrayList<>();
        sources().forEach(source -> {
            if (available(source)) {
                try {
                    hits.addAll(run(source, cleaned));
                } catch (org.springframework.dao.DataAccessException e) {
                    // The source is not available in this installation. Skipped rather than
                    // surfaced: a search box that fails is worse than one that misses a record.
                    // Logged, because a query that is wrong for any other reason must not be
                    // allowed to hide behind this catch for ever.
                    log.warn("Search source {} is unavailable and was skipped", source.name(), e);
                }
            }
        });
        return hits;
    }

    /** A source is offered when the caller may read it and its module is switched on. */
    private boolean available(SearchSource source) {
        return auth.hasPermission(source.permission())
                && (source.module() == null
                || institutions.isModuleEnabled(source.module()));
    }

    /** Search one kind of record, for a screen that asks for students and nothing else. */
    @Transactional(readOnly = true)
    public List<SearchDtos.SearchHit> search(SearchDtos.Source source, String term) {
        auth.requirePermission("SEARCH_GLOBAL");
        SearchSource found = sources().stream()
                .filter(candidate -> candidate.name().name().equalsIgnoreCase(source.name()))
                .findFirst()
                .orElseThrow(() -> AppException.rule("Cannot search " + source));
        auth.requirePermission(found.permission());
        if (found.module() != null) {
            institutions.requireModuleEnabled(found.module());
        }
        return run(found, cleaned(term));
    }

    private List<SearchDtos.SearchHit> run(SearchSource source, String term) {
        MapSqlParameterSource source0 = new MapSqlParameterSource()
                .addValue("term", "%" + term.toLowerCase(Locale.ROOT) + "%")
                .addValue("limit", PER_SOURCE);
        List<SearchDtos.SearchHit> hits = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(source.sql(), source0)) {
            hits.add(new SearchDtos.SearchHit(
                    source.name(),
                    source.label(),
                    toUuid(row.get("id")),
                    SearchDtos.text(row, "heading"),
                    SearchDtos.text(row, "detail"),
                    SearchDtos.text(row, "reference")));
        }
        return hits;
    }

    /**
     * The terms this search will accept.
     *
     * <p>Two characters is the floor because a single letter matches most of the roll, and the
     * answer would be a list too long to be a list. The ceiling keeps an accidental paste of a
     * whole document from becoming a full table scan.
     */
    private String cleaned(String term) {
        if (term == null) {
            throw AppException.rule("Type something to search for.");
        }
        String cleaned = term.trim();
        if (cleaned.length() < MIN_TERM_LENGTH) {
            throw AppException.rule("Search for at least " + MIN_TERM_LENGTH + " characters.");
        }
        if (cleaned.length() > MAX_TERM_LENGTH) {
            throw AppException.rule("That is too long to search for.");
        }
        return cleaned;
    }

    private UUID toUuid(Object value) {
        return value instanceof UUID uuid ? uuid : null;
    }

    /**
     * The searchable sources.
     *
     * <p>Ordered as a person is most likely to be looking for them: a student, then staff, then
     * the paperwork around them.
     */
    private List<SearchSource> sources() {
        return List.of(
                new SearchSource(SearchDtos.Source.STUDENT, "STUDENT_READ", "Students",
                        """
                        select s.id, s.student_number as reference,
                               s.first_name || ' ' || s.last_name as heading,
                               coalesce(c.name, 'Not enrolled') || ' · ' || s.status as detail
                        from students s
                        left join enrollments e on e.student_id = s.id and e.status = 'ENROLLED'
                        left join school_classes c on c.id = e.school_class_id
                        where lower(s.first_name) like :term
                           or lower(s.last_name) like :term
                           or lower(coalesce(s.student_number, '')) like :term
                           or lower(s.first_name || ' ' || s.last_name) like :term
                           or lower(coalesce(s.email, '')) like :term
                           or lower(coalesce(s.phone, '')) like :term
                        order by s.student_number
                        limit :limit
                        """, null),
                new SearchSource(SearchDtos.Source.EMPLOYEE, "EMPLOYEE_READ", "Employees",
                        """
                        select e.id, e.employee_code as reference,
                               e.first_name || ' ' || e.last_name as heading,
                               coalesce(d.name, 'No department') || ' · ' || e.status as detail
                        from employees e
                        left join departments d on d.id = e.department_id
                        where lower(e.first_name) like :term
                           or lower(e.last_name) like :term
                           or lower(coalesce(e.employee_code, '')) like :term
                           or lower(e.first_name || ' ' || e.last_name) like :term
                           or lower(coalesce(e.email, '')) like :term
                        order by e.employee_code
                        limit :limit
                        """, null),
                new SearchSource(SearchDtos.Source.APPLICANT, "ADMISSION_READ", "Applicants",
                        """
                        select a.id, a.reference_code as reference,
                               a.first_name || ' ' || a.last_name as heading,
                               coalesce(a.email, '') || ' · ' || a.status as detail
                        from admission_applications a
                        where lower(a.first_name) like :term
                           or lower(a.last_name) like :term
                           or lower(coalesce(a.reference_code, '')) like :term
                           or lower(a.first_name || ' ' || a.last_name) like :term
                           or lower(coalesce(a.email, '')) like :term
                        order by a.reference_code
                        limit :limit
                        """, null),
                new SearchSource(SearchDtos.Source.COURSE, "ACADEMIC_READ", "Courses",
                        """
                        select c.id, c.code as reference, c.name as heading,
                               coalesce(c.credit_hours, 0)::text as detail
                        from courses c
                        where lower(c.name) like :term or lower(coalesce(c.code, '')) like :term
                        order by c.code
                        limit :limit
                        """, null),
                new SearchSource(SearchDtos.Source.INVOICE, "INVOICE_READ", "Invoices",
                        """
                        select i.id, i.invoice_number as reference,
                               s.first_name || ' ' || s.last_name as heading,
                               i.status || ' · ' || (i.total_amount - i.paid_amount) as detail
                        from invoices i
                        join students s on s.id = i.student_id
                        where lower(i.invoice_number) like :term
                           or lower(s.first_name || ' ' || s.last_name) like :term
                        order by i.issue_date desc
                        limit :limit
                        """, null),
                new SearchSource(SearchDtos.Source.PAYMENT, "PAYMENT_READ", "Payments",
                        """
                        select p.id, coalesce(p.receipt_number, p.reference, '') as reference,
                               s.first_name || ' ' || s.last_name as heading,
                               p.status || ' · ' || p.amount as detail
                        from payments p
                        join students s on s.id = p.student_id
                        where lower(coalesce(p.receipt_number, '')) like :term
                           or lower(coalesce(p.reference, '')) like :term
                           or lower(s.first_name || ' ' || s.last_name) like :term
                        order by p.received_at desc
                        limit :limit
                        """, null),
                new SearchSource(SearchDtos.Source.BOOK, "LIBRARY_READ", "Books",
                        """
                        select b.id, coalesce(b.isbn, '') as reference, b.title as heading,
                               coalesce(p.name, 'Unpublished') as detail
                        from books b
                        left join publishers p on p.id = b.publisher_id
                        where lower(b.title) like :term or lower(coalesce(b.isbn, '')) like :term
                         order by b.title
                         limit :limit
                         """, ModuleKey.LIBRARY),
                new SearchSource(SearchDtos.Source.ASSET, "ASSET_READ", "Assets",
                        """
                        select a.id, a.asset_number as reference, a.name as heading,
                               a.status || ' · ' || coalesce(a.location, 'No location') as detail
                        from assets a
                        where lower(a.name) like :term
                           or lower(coalesce(a.asset_number, '')) like :term
                           or lower(coalesce(a.serial_number, '')) like :term
                         order by a.name
                         limit :limit
                         """, ModuleKey.ASSETS));
    }

    /** One searchable kind of record: what it is called, who may search it, and how to find it. */
    private record SearchSource(SearchDtos.Source name, String permission, String label,
                              String sql, ModuleKey module) {
    }

    /** Search results grouped by source, for a panel that shows a heading per kind of record. */
    public Map<String, List<SearchDtos.SearchHit>> grouped(String term) {
        Map<String, List<SearchDtos.SearchHit>> grouped = new LinkedHashMap<>();
        for (SearchDtos.SearchHit hit : search(term)) {
            grouped.computeIfAbsent(hit.sourceLabel(), key -> new ArrayList<>()).add(hit);
        }
        return grouped;
    }
}