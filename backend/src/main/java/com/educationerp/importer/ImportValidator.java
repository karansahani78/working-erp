package com.educationerp.importer;

import com.educationerp.common.error.AppException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a spreadsheet against the fields a record type wants, and says what is wrong with it.
 *
 * <p>Validation happens in one place rather than inside each importer, for two reasons: a
 * spreadsheet full of mistakes should be reported in full before anything is written, and the same
 * mistake should read the same way whichever record type it is.
 *
 * <p>Nothing here touches the database. That is the point of the step: a spreadsheet is judged
 * before it is trusted, and the only thing that can go wrong here is a bad cell.
 */
@Component
public class ImportValidator {

    /** The formats a school actually sends dates in. */
    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"));

    /** One row as read, with the errors it earned. */
    public record ValidatedRow(int rowNumber, Map<String, String> values,
                               List<ImportBatch.RowError> errors) {

        public boolean ok() {
            return errors.isEmpty();
        }
    }

    /**
     * Read every row against the fields.
     *
     * <p>Every row is read even after one has failed, so a person is shown every problem in one
     * pass instead of fixing them a row at a time.
     */
    public List<ValidatedRow> validate(ImportField[] fields, SpreadsheetReader.SheetData sheet,
                                       Map<String, String> mapping) {
        Map<String, Integer> columns = resolveColumns(fields, sheet, mapping);
        List<ValidatedRow> rows = new ArrayList<>();

        for (int index = 0; index < sheet.rows().size(); index++) {
            // Row 1 is the header as the person sees it, so the first data row is row 2. The
            // number in an error message is then the number in their own spreadsheet.
            int rowNumber = index + 2;
            List<String> raw = sheet.rows().get(index);
            Map<String, String> values = new LinkedHashMap<>();
            List<ImportBatch.RowError> errors = new ArrayList<>();

            for (ImportField field : fields) {
                Integer column = columns.get(field.name());
                read(field, cell(raw, column), rowNumber, values, errors);
            }
            rows.add(new ValidatedRow(rowNumber, values, errors));
        }
        return rows;
    }

    /**
     * Which spreadsheet column feeds each field.
     *
     * <p>A mapping the person confirmed always wins. Otherwise the header is matched against the
     * field's label and aliases, ignoring case and spacing, so a sensibly-headed spreadsheet can
     * be imported without a visit to the mapping screen at all.
     */
    public Map<String, Integer> resolveColumns(ImportField[] fields,
                                               SpreadsheetReader.SheetData sheet,
                                               Map<String, String> mapping) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (ImportField field : fields) {
            String chosen = mapping.get(field.name());
            if (chosen != null) {
                int index = sheet.headers().indexOf(chosen);
                if (index >= 0) {
                    columns.put(field.name(), index);
                    continue;
                }
            }
            String matched = match(field, sheet.headers());
            if (matched != null) {
                columns.put(field.name(), sheet.headers().indexOf(matched));
            }
        }
        return columns;
    }

    private String match(ImportField field, List<String> headers) {
        for (String header : headers) {
            if (normalise(header).equals(normalise(field.label()))) {
                return header;
            }
        }
        for (String alias : field.aliases()) {
            for (String header : headers) {
                if (normalise(header).equals(normalise(alias))) {
                    return header;
                }
            }
        }
        return null;
    }

    /** The fields that could not be matched to a column, so the mapping screen can say so. */
    public List<ImportField> unmapped(ImportField[] fields, SpreadsheetReader.SheetData sheet,
                                      Map<String, String> mapping) {
        Map<String, Integer> columns = resolveColumns(fields, sheet, mapping);
        List<ImportField> unmapped = new ArrayList<>();
        for (ImportField field : fields) {
            if (!columns.containsKey(field.name())) {
                unmapped.add(field);
            }
        }
        return unmapped;
    }

    /** Refuse to go further while a required field has no column to read from. */
    public void requireMapped(ImportField[] fields, SpreadsheetReader.SheetData sheet,
                              Map<String, String> mapping) {
        Map<String, Integer> columns = resolveColumns(fields, sheet, mapping);
        List<String> missing = new ArrayList<>();
        for (ImportField field : fields) {
            if (field.required() && !columns.containsKey(field.name())) {
                missing.add(field.label());
            }
        }
        if (!missing.isEmpty()) {
            throw AppException.rule("These columns still need to be mapped: "
                    + String.join(", ", missing));
        }
    }

    /**
     * One cell, by position.
     *
     * <p>A short row is padded rather than refused: a spreadsheet that stops halfway is more
     * likely to have a trailing blank than to be an attack, and the field's own required check
     * will complain about whatever is actually missing.
     */
    private String cell(List<String> row, Integer column) {
        if (column == null || column < 0 || column >= row.size()) {
            return "";
        }
        String value = row.get(column);
        return value == null ? "" : value.trim();
    }

    /** Read one cell into the row, reporting anything unusable. */
    private void read(ImportField field, String value, int rowNumber, Map<String, String> values,
                      List<ImportBatch.RowError> errors) {
        if (value == null || value.isBlank()) {
            if (field.required()) {
                errors.add(new ImportBatch.RowError(rowNumber, field.name(),
                        field.label() + " is required", ""));
            }
            values.put(field.name(), "");
            return;
        }

        switch (field.kind()) {
            case TEXT -> {
                if (field.maxLength() > 0 && value.length() > field.maxLength()) {
                    errors.add(new ImportBatch.RowError(rowNumber, field.name(),
                            field.label() + " is longer than " + field.maxLength() + " characters",
                            value));
                } else {
                    values.put(field.name(), value);
                }
            }
            case EMAIL -> {
                if (!EMAIL.matcher(value).matches()) {
                    errors.add(new ImportBatch.RowError(rowNumber, field.name(),
                            "That is not an email address", value));
                } else {
                    values.put(field.name(), value);
                }
            }
            case NUMBER -> {
                try {
                    values.put(field.name(),
                            new BigDecimal(value.replace(",", "")).toPlainString());
                } catch (NumberFormatException e) {
                    errors.add(new ImportBatch.RowError(rowNumber, field.name(),
                            "That is not a number", value));
                }
            }
            case DATE -> {
                LocalDate date = date(value);
                if (date == null) {
                    errors.add(new ImportBatch.RowError(rowNumber, field.name(),
                            "Not a date we recognise, for example 2026-01-31", value));
                } else {
                    values.put(field.name(), date.toString());
                }
            }
            case CHOICE -> {
                String matched = match(field, value);
                if (matched == null) {
                    errors.add(new ImportBatch.RowError(rowNumber, field.name(),
                            "Must be one of: " + String.join(", ", field.allowed()), value));
                } else {
                    values.put(field.name(), matched);
                }
            }
        }
    }

    /** Deliberately simple: something, an @, something, and no spaces. */
    private static final java.util.regex.Pattern EMAIL =
            java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private LocalDate date(String value) {
        for (DateTimeFormatter format : DATES) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException e) {
                // Try the next shape.
            }
        }
        return null;
    }

    /** Match a value against an accepted list, ignoring case, spacing and underscores. */
    private String match(ImportField field, String value) {
        String cleaned = tidy(value);
        for (String allowed : field.allowed()) {
            if (allowed.equalsIgnoreCase(value)) {
                return allowed;
            }
        }
        for (String allowed : field.allowed()) {
            if (tidy(allowed).equalsIgnoreCase(cleaned)) {
                return allowed;
            }
        }
        return null;
    }

    private String normalise(String value) {
        return tidy(value);
    }

    private String tidy(String value) {
        return value == null ? ""
                : value.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", " ");
    }
}