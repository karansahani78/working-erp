package com.educationerp.reporting.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.reporting.ReportDefinition;
import com.educationerp.reporting.ReportFormat;
import com.educationerp.reporting.ReportService;
import com.educationerp.reporting.ReportTable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The reports endpoint.
 *
 * <p>Two ways to ask for the same thing: the table as JSON, for a screen, and the same table as
 * a file, for a person who wants to take it away. They run the same query, so the number on the
 * screen is the number in the spreadsheet.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    /** The catalogue: every report this caller may run, grouped for a menu. */
    @GetMapping
    public ApiResponse<Map<String, List<Map<String, Object>>>> catalogue() {
        Map<String, List<Map<String, Object>>> catalogue = new java.util.LinkedHashMap<>();
        reports.catalogue().forEach((group, definitions) ->
                catalogue.put(group, definitions.stream().map(reports::describe).toList()));
        return ApiResponse.ok(catalogue);
    }

    @GetMapping("/{key}")
    public ApiResponse<Map<String, Object>> run(
            @PathVariable String key,
            @RequestParam Map<String, String> parameters) {
        return ApiResponse.ok(view(reports.run(key, parameters)));
    }

    /** The same report as a file. */
    @GetMapping("/{key}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable String key,
            @RequestParam(defaultValue = "CSV") String format,
            @RequestParam Map<String, String> parameters) {
        ReportFormat chosen = ReportFormat.of(format);
        ReportService.Export export = reports.export(key, parameters, chosen);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + export.fileName() + "\"")
                .contentType(MediaType.parseMediaType(export.contentType()))
                .body(export.body());
    }

    /** Convenience for the common case: this month's copy of a dated report. */
    @GetMapping("/{key}/this-month")
    public ApiResponse<Map<String, Object>> thisMonth(@PathVariable String key) {
        return ApiResponse.ok(view(reports.run(key, Map.of(
                "from", LocalDate.now().withDayOfMonth(1).toString(),
                "to", LocalDate.now().toString()))));
    }

    /** A table as a screen wants it. LinkedHashMap so the keys keep the order they were written. */
    private Map<String, Object> view(ReportTable table) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("title", table.title());
        body.put("columns", table.columns());
        body.put("rows", table.rows());
        body.put("summary", table.summary());
        body.put("notes", table.notes());
        body.put("rowCount", table.rowCount());
        return body;
    }
}
