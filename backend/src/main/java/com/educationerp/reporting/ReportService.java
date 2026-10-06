package com.educationerp.reporting;

import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs reports and hands back the bytes.
 *
 * <p>The permission check lives here rather than in each report, so a report cannot forget it and
 * the catalogue can be filtered to what a caller may run. Reports are read-only: nothing in this
 * service opens a write transaction, because a report that could change something would be a
 * report nobody could trust.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRegistry registry;
    private final ReportDefinitions definitions;
    private final ReportExporter exporter;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationChecker auth;
    private final AuditService audit;

    /** The catalogue, grouped, containing only what this caller may run. */
    public Map<String, List<ReportDefinition>> catalogue() {
        auth.requirePermission("REPORT_READ");
        return registry.visibleTo(auth::hasPermission);
    }

    /** Build one report. */
    @Transactional(readOnly = true)
    public ReportTable run(String key, Map<String, String> parameters) {
        ReportDefinition definition = require(key);
        ReportContext context = new ReportContext(key, parameters, jdbc);
        ReportTable table = definition.build().apply(context);
        audit.record(AuditEvent.builder()
                .action(AuditAction.READ)
                .module("REPORTING")
                .entityType("Report")
                .entityId(key)
                .summary("Ran the " + definition.title() + " report ("
                        + table.rowCount() + " rows)")
                .succeeded(true)
                .build());
        return table;
    }

    /**
     * Build one report and render it.
     *
     * <p>Recorded in the audit trail, because an export is the report leaving the system: a
     * finance spreadsheet that was downloaded is worth being able to show afterwards.
     */
    @Transactional(readOnly = true)
    public Export export(String key, Map<String, String> parameters, ReportFormat format) {
        ReportDefinition definition = require(key);
        ReportTable table = run(key, parameters);
        // Reading a report on screen and taking a copy of it away are different acts. The data
        // permission was already checked; exporting is the one that needs its own.
        auth.requirePermission("REPORT_EXPORT");
        ReportFormat chosen = format == null ? ReportFormat.CSV : format;
        byte[] body = switch (chosen) {
            case CSV -> exporter.toCsv(table);
            case XLSX -> exporter.toXlsx(table);
            case PDF -> exporter.toPdf(table);
        };
        audit.record(AuditEvent.builder()
                .action(AuditAction.EXPORT)
                .module("REPORTING")
                .entityType("Report")
                .entityId(key)
                .summary("Exported " + table.rowCount() + " rows of " + definition.title()
                        + " as " + chosen)
                .succeeded(true)
                .build());
        return new Export(body, chosen.contentType(),
                fileName(definition.title(), chosen));
    }

    private ReportDefinition require(String key) {
        ReportDefinition definition = registry.require(key);
        auth.requirePermission(definition.permission());
        return definition;
    }

    /** The rendered bytes and how to name them. */
    public record Export(byte[] body, String contentType, String fileName) {
    }

    private String fileName(String title, ReportFormat format) {
        String slug = title.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug + "." + format.extension();
    }

    /** One report as the catalogue presents it, parameters included. */
    public Map<String, Object> describe(ReportDefinition definition) {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("key", definition.key());
        description.put("title", definition.title());
        description.put("group", definition.group());
        description.put("description", definition.description());
        description.put("parameters", definition.parameterList());
        return description;
    }
}
