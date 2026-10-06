package com.educationerp.reporting;

import com.educationerp.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs every report in the catalogue.
 *
 * <p>A report is a hand-written query, so the only thing that proves one works is running it.
 * Nothing else notices a column that was renamed, a table that was never created, or a report
 * that assumes a row exists when a period has none. One test over the whole catalogue catches all
 * of that and reports the failures together, so a schema problem can be fixed in one pass rather
 * than one test run at a time.
 *
 * <p>Identifiers are deliberately ones that match nothing. An empty result is the harder case, and
 * a report that copes with an empty database will cope with a real one.
 */
class ReportSmokeTest extends IntegrationTest {

    @Test
    @DisplayName("every report in the catalogue runs and returns a table")
    void everyReportRuns() throws Exception {
        String token = adminToken();
        Map<String, String> parameters = parameters();

        List<String> failures = new ArrayList<>();
        List<String> keys = reportKeys(token);
        assertThat(keys).as("the catalogue should not be empty").isNotEmpty();

        for (String key : keys) {
            try {
                MvcResult result = mockMvc.perform(get("/api/v1/reports/{key}", key)
                                .header("Authorization", bearer(token))
                                .param("from", parameters.get("from"))
                                .param("to", parameters.get("to"))
                                .param("studentId", parameters.get("studentId"))
                                .param("examId", parameters.get("examId"))
                                .param("payrollRunId", parameters.get("payrollRunId"))
                                .param("month", parameters.get("month")))
                        .andReturn();
                expectOk(key, result);
                JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("data");
                assertThat(body.path("columns").isArray())
                        .as("%s should return columns", key).isTrue();
                assertThat(body.path("title").asText())
                        .as("%s should have a title", key).isNotBlank();
            } catch (Throwable e) {
                failures.add(key + ": " + rootCause(e));
            }
        }
        assertThat(failures).as("reports that did not run").isEmpty();
    }

    @Test
    @DisplayName("every report exports as CSV, XLSX and PDF")
    void everyReportExports() throws Exception {
        String token = adminToken();
        Map<String, String> parameters = parameters();
        List<String> failures = new ArrayList<>();

        for (String key : reportKeys(token)) {
            for (String format : List.of("CSV", "XLSX", "PDF")) {
                try {
                    MvcResult result = mockMvc.perform(get("/api/v1/reports/{key}/export", key)
                                    .header("Authorization", bearer(token))
                                    .param("format", format)
                                    .param("from", parameters.get("from"))
                                    .param("to", parameters.get("to"))
                                    .param("studentId", parameters.get("studentId"))
                                    .param("examId", parameters.get("examId"))
                                    .param("payrollRunId", parameters.get("payrollRunId"))
                                    .param("month", parameters.get("month")))
                            .andReturn();
                    expectOk(key + " as " + format, result);
                    byte[] body = result.getResponse().getContentAsByteArray();
                    assertThat(body).as("%s as %s should not be empty", key, format).isNotEmpty();
                    assertMagicBytes(key, format, body);
                } catch (Throwable e) {
                    failures.add(key + " as " + format + ": " + rootCause(e));
                }
            }
        }
        assertThat(failures).as("exports that failed").isEmpty();
    }

    @Test
    @DisplayName("a report that does not exist is refused rather than reported as empty")
    void unknownReportIsRefused() throws Exception {
        String token = adminToken();
        // Knowing a key is not the same as being allowed to see it, and a missing key must not be
        // answered with an empty table that reads like there is nothing to report.
        mockMvc.perform(get("/api/v1/reports/no-such-report")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    /** The keys, read from the catalogue rather than listed here, so a new report is covered too. */
    private List<String> reportKeys(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reports")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode groups = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data");
        List<String> keys = new ArrayList<>();
        for (Iterator<JsonNode> group = groups.elements(); group.hasNext(); ) {
            for (JsonNode definition : group.next()) {
                keys.add(definition.path("key").asText());
            }
        }
        return keys;
    }

    /**
     * Fail with what the server said, not just the status.
     *
     * <p>A bare "expected 200 but was 500" says nothing about which of twenty-nine reports broke
     * and why, and the whole point of running them together is to be told all of it at once.
     */
    private void expectOk(String what, MvcResult result) throws Exception {
        int status = result.getResponse().getStatus();
        if (status == 200) {
            return;
        }
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String message = body;
        try {
            message = objectMapper.readTree(body).path("message").asText(body);
        } catch (Exception ignored) {
            // Not JSON: the raw body is the most useful thing to print.
        }
        throw new AssertionError(what + " returned " + status + ": " + message);
    }

    /** A file that is not really a spreadsheet or a PDF still has a size, so check the header. */
    private void assertMagicBytes(String key, String format, byte[] body) {
        switch (format) {
            case "PDF" -> assertThat(new String(body, 0, 4, StandardCharsets.US_ASCII))
                    .as("%s should be a PDF", key).isEqualTo("%PDF");
            case "XLSX" -> {
                assertThat(body[0] & 0xFF).as("%s should be a zip container", key).isEqualTo(0x50);
                assertThat(body[1] & 0xFF).isEqualTo(0x4B);
            }
            default -> {
            }
        }
    }

    private Map<String, String> parameters() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("from", "2023-01-01");
        parameters.put("to", "2030-12-31");
        parameters.put("studentId", UUID.randomUUID().toString());
        parameters.put("examId", UUID.randomUUID().toString());
        parameters.put("payrollRunId", UUID.randomUUID().toString());
        parameters.put("month", "2024-01");
        return parameters;
    }

    private String rootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}