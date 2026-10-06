package com.educationerp.reporting;

import java.util.Locale;

/** The three shapes a report leaves the building in. */
public enum ReportFormat {

    CSV,
    XLSX,
    PDF;

    public static ReportFormat of(String value) {
        if (value == null || value.isBlank()) {
            return CSV;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw com.educationerp.common.error.AppException.rule(
                    "Export format must be CSV, XLSX or PDF.");
        }
    }

    public String contentType() {
        return switch (this) {
            case CSV -> "text/csv";
            case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case PDF -> "application/pdf";
        };
    }

    public String extension() {
        return name().toLowerCase(Locale.ROOT);
    }
}
