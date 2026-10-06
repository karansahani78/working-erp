package com.educationerp.reporting;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Turns a finished table into the three formats the blueprint asks for.
 *
 * <p>All three are built from the same {@link ReportTable}, so a spreadsheet and a printout of
 * the same report cannot disagree about a figure. Nothing is summed or re-derived here: the
 * exporter's only job is to present what the report decided.
 */
@Component
public class ReportExporter {

    private static final float MARGIN = 36f;
    private static final float LINE_HEIGHT = 11f;
    private static final PDType1Font REGULAR =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /**
     * CSV, with a byte-order mark.
     *
     * <p>The mark is what makes Excel read a UTF-8 file as text rather than as Latin-1, which
     * is the difference between a Nepali name arriving intact and arriving as mojibake.
     */
    public byte[] toCsv(ReportTable table) {
        StringBuilder csv = new StringBuilder();
        csv.append(table.title()).append('\n');
        table.summary().forEach((label, value) ->
                csv.append(csv(label)).append(',').append(csv(String.valueOf(value))).append('\n'));
        if (!table.summary().isEmpty()) {
            csv.append('\n');
        }
        csv.append('﻿');
        csv.append(String.join(",", table.columns().stream().map(this::csv).toList()))
                .append('\n');
        for (List<Object> row : table.rows()) {
            csv.append(String.join(",", row.stream()
                    .map(value -> csv(value == null ? "" : String.valueOf(value)))
                    .toList())).append('\n');
        }
        for (String note : table.notes()) {
            csv.append('\n').append(csv(note)).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** A spreadsheet with the heading, the summary and a frozen header row. */
    public byte[] toXlsx(ReportTable table) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(safeSheetName(table.title()));
            int rowNumber = 0;

            Row heading = sheet.createRow(rowNumber++);
            CellStyle headingStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headingStyle.setFont(bold);
            heading.createCell(0).setCellValue(table.title());
            heading.getCell(0).setCellStyle(headingStyle);

            for (Map.Entry<String, Object> entry : table.summary().entrySet()) {
                Row summary = sheet.createRow(rowNumber++);
                summary.createCell(0).setCellValue(entry.getKey());
                summary.createCell(1).setCellValue(String.valueOf(entry.getValue()));
            }
            if (!table.summary().isEmpty()) {
                rowNumber++;
            }

            int headerRow = rowNumber++;
            Row header = sheet.createRow(headerRow);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(bold);
            for (int column = 0; column < table.columns().size(); column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(table.columns().get(column));
                cell.setCellStyle(headerStyle);
            }

            for (List<Object> row : table.rows()) {
                Row sheetRow = sheet.createRow(rowNumber++);
                for (int column = 0; column < row.size(); column++) {
                    Object value = row.get(column);
                    Cell cell = sheetRow.createCell(column);
                    if (value instanceof Number number) {
                        // Written as a number so a spreadsheet can total it, and a report of
                        // money that cannot be summed in Excel is not much of a report.
                        cell.setCellValue(number.doubleValue());
                    } else {
                        cell.setCellValue(value == null ? "" : String.valueOf(value));
                    }
                }
            }

            for (int column = 0; column < table.columns().size(); column++) {
                sheet.autoSizeColumn(column);
            }
            sheet.createFreezePane(0, headerRow + 1);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not build the spreadsheet", e);
        }
    }

    /**
     * A PDF, one table.
     *
     * <p>Landscape, so a nine-column report is readable, and the header repeats on every page so
     * a printed report can be followed without counting back to the first sheet.
     */
    public byte[] toPdf(ReportTable table) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            float[] widths = columnWidths(table);
            float pageHeight = pageHeight(widths);

            PdfWriter writer = new PdfWriter(document, pageHeight, widths);
            writer.title(table.title(), table.summary(), BOLD);
            writer.header(table.columns(), BOLD);
            for (List<Object> row : table.rows()) {
                writer.line(row, REGULAR);
            }
            writer.notes(table.notes());
            writer.finish();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not build the PDF", e);
        }
    }

    private float pageHeight(float[] widths) {
        float total = 0;
        for (float width : widths) {
            total += width;
        }
        return Math.max(total + 2 * MARGIN, 595);
    }

    /**
     * The page-by-page mechanics of the PDF, kept apart from the report.
     *
     * <p>PDFBox needs the text stream opened, positioned and closed by hand for every line, and
     * a page break has to happen between lines. Doing that inline makes the report code
     * unreadable, so it lives here: the report says what to write, this decides where.
     */
    private final class PdfWriter {

        private final PDDocument document;
        private final float pageHeight;
        private final float[] widths;
        private PDPageContentStream stream;
        private float y;

        PdfWriter(PDDocument document, float pageHeight, float[] widths) {
            this.document = document;
            this.pageHeight = pageHeight;
            this.widths = widths;
        }

        void title(String text, Map<String, Object> summary, PDType1Font bold) throws IOException {
            startPage();
            y = line(text, bold, 13, y);
            for (Map.Entry<String, Object> entry : summary.entrySet()) {
                y = line(entry.getKey() + ": " + entry.getValue(), REGULAR, 9, y);
            }
            closeStream();
        }

        void header(List<String> columns, PDType1Font bold) throws IOException {
            startPage();
            y = line(List.copyOf(columns), bold, 9, y);
        }

        void line(List<Object> values, PDType1Font font) throws IOException {
            if (y - LINE_HEIGHT < MARGIN) {
                closeStream();
                startPage();
                y = pageHeight - MARGIN;
            }
            y = line(values, font, 8, y);
        }

        void notes(List<String> notes) throws IOException {
            if (!notes.isEmpty()) {
                line("", REGULAR, 9, y - 4);
                for (String note : notes) {
                    y = line(note, REGULAR, 8, y);
                }
            }
            closeStream();
        }

        void finish() throws IOException {
            closeStream();
        }

        private void startPage() throws IOException {
            PDPage page = new PDPage(new PDRectangle(widths[widths.length - 1] + MARGIN * 2,
                    pageHeight));
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = pageHeight - MARGIN;
        }

        private void closeStream() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private float line(String text, PDType1Font font, float size, float y) throws IOException {
            return line(List.of(text), font, size, y);
        }

        private float line(List<Object> values, PDType1Font font, float size, float y)
                throws IOException {
            float left = MARGIN;
            for (int column = 0; column < values.size(); column++) {
                Object value = values.get(column);
                if (column > 0) {
                    left += widths[column - 1];
                }
                if (value != null && !String.valueOf(value).isBlank()) {
                    stream.beginText();
                    stream.setFont(font, size);
                    stream.newLineAtOffset(left, y - size);
                    stream.showText(clip(String.valueOf(value), widths[column], font));
                    stream.endText();
                }
            }
            return y - LINE_HEIGHT;
        }
    }

    /** Stop a cell at the width it has, with an ellipsis rather than text over the next column. */
    private String clip(String text, float width, PDType1Font font) {
        String value = text.replaceAll("[\\r\\n\\t]", " ");
        float available = width - 6;
        if (value.isEmpty()) {
            return value;
        }
        try {
            if (font.getStringWidth(value) <= available) {
                return value;
            }
            String trimmed = value;
            while (trimmed.length() > 1 && font.getStringWidth(trimmed + "...") > available) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed + "...";
        } catch (IOException e) {
            return value;
        } catch (IllegalArgumentException e) {
            // Not every character the database holds is drawable in a standard font. The report
            // is still worth printing without it rather than failing the whole export.
            return value.replaceAll("[^\\x20-\\x7E]", "?");
        }
    }

    private float[] columnWidths(ReportTable table) {
        int count = table.columns().size();
        float[] widths = new float[count];
        java.util.Arrays.fill(widths, 90f);
        for (int column = 0; column < count; column++) {
            widths[column] = Math.max(widths[column],
                    Math.min(220f, table.columns().get(column).length() * 6f + 12f));
        }
        return widths;
    }

    private String safeSheetName(String title) {
        String cleaned = title.replaceAll("[\\\\/*?:\\[\\]]", " ").trim();
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    /** Quote a CSV field, and neutralise anything that would be read as a formula. */
    private String csv(String value) {
        String safe = value.replaceAll("^[=+\\-@]", "'$&");
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }
}
