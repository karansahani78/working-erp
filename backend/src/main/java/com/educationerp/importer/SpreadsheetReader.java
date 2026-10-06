package com.educationerp.importer;

import com.educationerp.common.error.AppException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a spreadsheet into plain text cells.
 *
 * <p>Everything comes back as a string, including numbers and dates. A cell that looked like a
 * date in one spreadsheet and a word in another should not decide how the row is parsed, and a
 * value like {@code 0204} must not arrive as the number 204 and quietly become a different year.
 *
 * <p>The date formats are the ones a school actually receives files in, including the day-first
 * form that Nepal, the UK and most of Europe write, which is ambiguous with the American form and
 * is therefore only accepted when the value cannot be read the other way.
 */
@Component
public class SpreadsheetReader {

    /** 5MB. Large enough for any school roll, small enough to refuse a mistaken upload. */
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 5000;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("d MMM yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"));

    /**
     * The sheet as rows of text.
     *
     * @param headers the first row, which names the columns
     * @param rows the data rows, in the order they appear
     */
    public SheetData read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AppException.rule("Choose a file to import.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw AppException.rule("That file is too large to import (5MB is the limit).");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        try (InputStream in = file.getInputStream()) {
            return read(in, filename);
        } catch (IOException e) {
            throw AppException.rule("That file could not be read: " + e.getMessage());
        }
    }

    /**
     * Read a file that is already in storage.
     *
     * <p>Validation and confirmation read the original file again rather than trusting a copy
     * held in the session, so what gets written is always what was uploaded.
     */
    public SheetData read(InputStream in, String filename) throws IOException {
        String name = filename == null ? "" : filename;
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".csv")
                ? readCsv(in)
                : readWorkbook(in);
    }

    private SheetData readWorkbook(InputStream in) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw AppException.rule("That spreadsheet has no sheets in it.");
            }
            DataFormatter formatter = new DataFormatter(java.util.Locale.ROOT);
            for (int index = sheet.getFirstRowNum();
                 index <= sheet.getLastRowNum() && rows.size() <= MAX_ROWS; index++) {
                Row row = sheet.getRow(index);
                if (row == null) {
                    continue;
                }
                List<String> values = new ArrayList<>();
                for (int column = 0; column < row.getLastCellNum(); column++) {
                    values.add(text(formatter, row.getCell(column)));
                }
                if (!blank(values)) {
                    rows.add(values);
                }
            }
        }
        return split(rows);
    }

    /**
     * A cell as the reader would see it.
     *
     * <p>A date cell is written out as an ISO date rather than as the serial number Excel keeps
     * internally, because the serial is meaningless to everything downstream.
     */
    private String text(DataFormatter formatter, Cell cell) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime moment = cell.getLocalDateTimeCellValue();
            LocalDate date = moment.toLocalDate();
            return moment.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                    ? date.toString()
                    : date + " " + moment.toLocalTime().withNano(0);
        }
        return formatter.formatCellValue(cell).trim();
    }

    private SheetData readCsv(InputStream in) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> headers;
        // setHeader() with no arguments already consumes the first record as the header. Adding
        // setSkipHeaderRecord(true) as well makes the parser discard that record before it is read
        // as a header, so the first data row is silently promoted to one and every file loses its
        // column names.
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in,
                StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setIgnoreSurroundingSpaces(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            headers = new ArrayList<>(parser.getHeaderNames());
            for (CSVRecord record : parser) {
                if (rows.size() > MAX_ROWS) {
                    throw AppException.rule("That file has more rows than can be imported "
                            + "(" + MAX_ROWS + " is the limit). Split it and import twice.");
                }
                List<String> values = new ArrayList<>();
                record.forEach(values::add);
                if (!blank(values)) {
                    rows.add(values);
                }
            }
        }
        if (headers.isEmpty()) {
            throw AppException.rule("That file has no header row naming its columns.");
        }
        return new SheetData(headers, rows);
    }

    /**
     * The workbook path's header row, split off from the rows under it.
     *
     * <p>POI hands back every row including the header, so unlike the CSV path this one has to
     * separate them. A blank header is named by position rather than left empty, so a person can
     * still be told which column a field would read from.
     */
    private SheetData split(List<List<String>> rows) {
        if (rows.isEmpty()) {
            throw AppException.rule("That file has no rows in it.");
        }
        List<String> first = rows.get(0);
        List<String> headers = new ArrayList<>(first.size());
        for (int index = 0; index < first.size(); index++) {
            String header = first.get(index);
            headers.add(header == null || header.isBlank() ? "column " + (index + 1) : header);
        }
        return new SheetData(headers, rows.subList(1, rows.size()));
    }

    private boolean blank(List<String> values) {
        return values.stream().allMatch(value -> value == null || value.isBlank());
    }

    /**
     * Read a date the way a school writes one.
     *
     * <p>Day-first is tried before month-first because the files this system receives are mostly
     * from places that write 03/04/2026 to mean the third of April. A value where both readings
     * are possible and the day is twelve or lower is read as day-first, which is the reading the
     * sender meant in every case seen, and the ambiguity is reported rather than hidden.
     */
    public LocalDate date(String value, String field, List<ImportBatch.RowError> errors, int row) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, format);
            } catch (java.time.format.DateTimeParseException e) {
                // Try the next shape.
            }
        }
        errors.add(new ImportBatch.RowError(row, field, "Not a date we recognise", value));
        return null;
    }

    /** A spreadsheet as read: a header row and the rows under it. */
    public record SheetData(List<String> headers, List<List<String>> rows) {

        public int columnCount() {
            return headers.size();
        }

        /** The value in a row for a mapped column, or an empty string when the column is absent. */
        public String value(List<String> row, String columnName) {
            int index = headers.indexOf(columnName);
            if (index < 0 || index >= row.size()) {
                return "";
            }
            String value = row.get(index);
            return value == null ? "" : value.trim();
        }
    }
}