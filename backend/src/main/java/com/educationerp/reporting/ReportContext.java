package com.educationerp.reporting;

import com.educationerp.common.error.AppException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * What a report is given: its parameters, and the database to answer from.
 *
 * <p>Parameters are read through here rather than straight from the request so that a report
 * cannot be handed a date, an identifier or a column name that has not been checked. Identifiers
 * arrive as text from a query string and are only ever used as bound values.
 */
public class ReportContext {

    private final String key;
    private final Map<String, String> parameters;
    private final NamedParameterJdbcTemplate jdbc;

    public ReportContext(String key, Map<String, String> parameters, NamedParameterJdbcTemplate jdbc) {
        this.key = key;
        this.parameters = parameters;
        this.jdbc = jdbc;
    }

    public String key() {
        return key;
    }

    /** A parameter that must be present. */
    public String required(String name) {
        String value = parameters.get(name);
        if (value == null || value.isBlank()) {
            throw AppException.rule("Report " + key + " needs a " + name + ".");
        }
        return value.trim();
    }

    /** A parameter that may be absent, where absent means "do not filter on this". */
    public String optional(String name) {
        String value = parameters.get(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public LocalDate date(String name, LocalDate fallback) {
        String value = optional(name);
        if (value == null) {
            return fallback;
        }
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            throw AppException.rule(name + " must be a date like 2026-01-31.");
        }
    }

    public UUID_ uuid(String name) {
        String value = optional(name);
        if (value == null) {
            return null;
        }
        try {
            return new UUID_(java.util.UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw AppException.rule(name + " must be an identifier.");
        }
    }

    public int integer(String name, int fallback) {
        String value = optional(name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw AppException.rule(name + " must be a whole number.");
        }
    }

    public LocalDate from() {
        return date("from", LocalDate.now().withDayOfMonth(1));
    }

    public LocalDate to() {
        return date("to", LocalDate.now());
    }

    /**
     * Run the query, binding whatever parameters it declared.
     *
     * <p>Only the named values are bound; the SQL itself is fixed in the report definitions and
     * never assembled from a request.
     */
    public List<Map<String, Object>> query(String sql, String... names) {
        MapSqlParameterSource source = new MapSqlParameterSource();
        for (String name : names) {
            String value = optional(name);
            if (value != null) {
                source.addValue(name, value);
            }
        }
        return jdbc.queryForList(sql, source);
    }

    public List<Map<String, Object>> query(String sql, MapSqlParameterSource source) {
        return jdbc.queryForList(sql, source);
    }

    public NamedParameterJdbcTemplate jdbc() {
        return jdbc;
    }

    /** Read a column that may be absent from the projection. */
    public static Object value(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? "" : value;
    }

    public static String text(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? "" : value.toString();
    }

    public static BigDecimal decimal(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    /** A yes or no column, printed the way a reader expects rather than as a database flag. */
    public static String bool(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean flag) {
            return flag ? "Yes" : "No";
        }
        return switch (value.toString()) {
            case "t", "true", "1" -> "Yes";
            case "f", "false", "0" -> "No";
            default -> value.toString();
        };
    }

    public static long number(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? 0L : ((Number) value).longValue();
    }

    /** A wrapped identifier, so a missing one stays null rather than becoming a parse error. */
    public record UUID_(java.util.UUID value) {
    }
}
