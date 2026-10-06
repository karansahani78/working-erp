package com.educationerp.reporting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A finished report: a heading, a row of column names, and the rows themselves.
 *
 * <p>One shape for all three exports, so a CSV and a PDF of the same report can never disagree
 * about what the numbers were. Values arrive as they came out of the database and are formatted
 * at the point of writing rather than here, so a figure that is text in one place -- a status,
 * a code -- is not turned into something it is not.
 */
public final class ReportTable {

    private final String title;
    private final List<String> columns;
    private final List<List<Object>> rows = new ArrayList<>();
    private final Map<String, Object> summary = new LinkedHashMap<>();
    private final List<String> notes = new ArrayList<>();

    public ReportTable(String title, List<String> columns) {
        this.title = title;
        this.columns = List.copyOf(columns);
    }

    public ReportTable row(Object... values) {
        if (values.length != columns.size()) {
            throw new IllegalStateException("A row of " + values.length + " values does not fit "
                    + columns.size() + " columns for " + title);
        }
        // List.of refuses nulls, and a null here is an absent column, not a broken report.
        rows.add(Collections.unmodifiableList(new ArrayList<>(Arrays.asList(values))));
        return this;
    }

    public ReportTable summary(String label, Object value) {
        summary.put(label, value);
        return this;
    }

    /** A line under the table explaining a caveat the reader would otherwise have to guess at. */
    public ReportTable note(String note) {
        notes.add(note);
        return this;
    }

    public String title() {
        return title;
    }

    public List<String> columns() {
        return columns;
    }

    public List<List<Object>> rows() {
        return rows;
    }

    public Map<String, Object> summary() {
        return summary;
    }

    public List<String> notes() {
        return notes;
    }

    public int rowCount() {
        return rows.size();
    }

    /** The table as plain text, used by the CSV writer and by nothing else. */
    public String toPlainText() {
        StringBuilder text = new StringBuilder(title).append('\n');
        summary.forEach((label, value) -> text.append(label).append(": ").append(value).append('\n'));
        if (!summary.isEmpty()) {
            text.append('\n');
        }
        text.append(String.join("\t", columns)).append('\n');
        for (List<Object> row : rows) {
            text.append(String.join("\t", row.stream().map(this::asText).toList())).append('\n');
        }
        notes.forEach(note -> text.append('\n').append(note).append('\n'));
        return text.toString();
    }

    private String asText(Object value) {
        if (value == null) {
            return "";
        }
        // A tab or a newline inside a value would break the row it sits in.
        return value.toString().replace("\t", " ").replaceAll("\\R", " ").trim();
    }
}
