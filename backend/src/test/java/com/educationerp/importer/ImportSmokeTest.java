package com.educationerp.importer;

import com.educationerp.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Walks a spreadsheet all the way through the import workflow.
 *
 * <p>The stages are only worth having if each one holds, and the only way to know that is to walk a
 * file through them: upload it, look at what the system understood, confirm the mapping, validate,
 * and only then confirm. Every test here also checks the thing just before it, because a workflow
 * that quietly accepts a file it never validated is worse than one that refuses it.
 *
 * <p>The file is deliberately mixed: good rows, a row with a broken date, a row missing something
 * required. A spreadsheet where every row is fine tests almost nothing.
 */
class ImportSmokeTest extends IntegrationTest {

    private static final String STUDENTS_CSV = """
            First Name,Last Name,Date of Birth,Gender,Email,Phone
            Aarav,Sharma,2012-04-17,MALE,aarav.sharma@example.com,9800000001
            Bibek,Tamang,not-a-date,MALE,bibek.tamang@example.com,9800000002
            ,Rai,2013-01-09,FEMALE,,9800000003
            Deepa,Gurung,2011-08-30,FEMALE,deepa.gurung@example.com,9800000004
            """;

    @Test
    @DisplayName("an upload stores the file and writes nothing")
    void uploadWritesNothing() throws Exception {
        String token = adminToken();
        JsonNode uploaded = upload(token, "STUDENTS", "students.csv", STUDENTS_CSV);

        assertThat(uploaded.path("batch").path("status").asText()).isEqualTo("UPLOADED");
        assertThat(uploaded.path("batch").path("totalRows").asInt()).isEqualTo(4);
        assertThat(uploaded.path("headers").toString()).contains("First Name", "Email");
        // Nothing has been created: an upload is a question, not an instruction.
        assertThat(count("students")).isZero();
        assertThat(count("import_batches")).isEqualTo(1);
    }

    @Test
    @DisplayName("columns are matched from the header alone when they are sensibly named")
    void headersMatchAutomatically() throws Exception {
        JsonNode uploaded = upload(adminToken(), "STUDENTS", "students.csv", STUDENTS_CSV);

        String unmapped = uploaded.path("unmapped").toString();
        assertThat(unmapped).as("optional columns the file lacks may be unmapped")
                .contains("Middle Name", "Address");
        assertThat(unmapped).as("but nothing required may be")
                .doesNotContain("First Name", "Last Name");
    }

    @Test
    @DisplayName("a field with no matching column is reported as unmapped")
    void unmappedFieldsAreReported() throws Exception {
        String token = adminToken();
        JsonNode uploaded = upload(token, "STUDENTS", "students.csv", """
                First Name,Last Name
                Aarav,Sharma
                """);

        assertThat(uploaded.path("unmapped").toString())
                .as("the missing columns should be named")
                .contains("Date of Birth", "Email");
    }

    @Test
    @DisplayName("validation reports every bad row and still writes no students")
    void validationReportsWithoutWriting() throws Exception {
        String token = adminToken();
        UUID id = upload(token, "STUDENTS", "students.csv", STUDENTS_CSV)
                .path("batch").path("id").asText().transform(UUID::fromString);

        MvcResult result = mockMvc.perform(post("/api/v1/imports/{id}/validate", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = json(result).path("data");

        assertThat(data.path("batch").path("status").asText()).isEqualTo("VALIDATED");
        assertThat(data.path("batch").path("validRows").asInt()).isEqualTo(2);
        assertThat(data.path("batch").path("invalidRows").asInt()).isEqualTo(2);

        // Row numbers count the header, so they match what the person sees in their file.
        String errors = data.toString();
        assertThat(errors).contains("Not a date we recognise");
        assertThat(errors).contains("First Name is required");
        assertThat(count("students"))
                .as("validation must not write records").isZero();
    }

    @Test
    @DisplayName("a preview shows the rows that would be written")
    void previewShowsUsableRows() throws Exception {
        String token = adminToken();
        UUID id = uploadId(token, "students.csv", STUDENTS_CSV);
        mockMvc.perform(post("/api/v1/imports/{id}/validate", id)
                .header("Authorization", bearer(token))).andExpect(status().isOk());

        JsonNode preview = json(mockMvc.perform(get("/api/v1/imports/{id}", id)
                .header("Authorization", bearer(token))).andReturn()).path("data");
        assertThat(preview.path("validRows").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("confirming is refused until the file has been validated")
    void confirmRequiresValidation() throws Exception {
        String token = adminToken();
        UUID id = uploadId(token, "students.csv", STUDENTS_CSV);

        mockMvc.perform(post("/api/v1/imports/{id}/confirm", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.message").value(
                                org.hamcrest.Matchers.containsString("not been validated")));
        assertThat(count("students")).isZero();
    }

    @Test
    @DisplayName("confirming is refused while rows have problems, and naming the count")
    void confirmRefusesInvalidRows() throws Exception {
        String token = adminToken();
        UUID id = uploadId(token, "students.csv", STUDENTS_CSV);
        validate(token, id);

        mockMvc.perform(post("/api/v1/imports/{id}/confirm", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.message").value(
                                org.hamcrest.Matchers.containsString("2 rows have problems")));
        assertThat(count("students")).isZero();
    }

    @Test
    @DisplayName("confirming with skipInvalidRows writes the good rows and names the rest")
    void confirmSkipsInvalidRows() throws Exception {
        String token = adminToken();
        UUID id = uploadId(token, "students.csv", STUDENTS_CSV);
        validate(token, id);

        JsonNode report = json(mockMvc.perform(post("/api/v1/imports/{id}/confirm", id)
                        .param("skipInvalidRows", "true")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn()).path("data");

        assertThat(report.path("imported").asInt()).isEqualTo(2);
        assertThat(report.path("skipped").asInt()).isEqualTo(2);
        assertThat(report.path("notes").toString()).contains("Row 3", "Row 4");
        assertThat(count("students")).isEqualTo(2);

        // A student number is issued by the system, never taken from the file.
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from students where student_number is not null", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("a batch cannot be confirmed twice")
    void confirmIsNotRepeatable() throws Exception {
        String token = adminToken();
        UUID id = uploadId(token, "students.csv", STUDENTS_CSV);
        validate(token, id);
        confirm(token, id);

        mockMvc.perform(post("/api/v1/imports/{id}/confirm", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity());
        assertThat(count("students")).isEqualTo(2);
    }

    @Test
    @DisplayName("rows without an email are not treated as duplicates of each other")
    void blankEmailIsNotADuplicate() throws Exception {
        String token = adminToken();
        // Two rows with no email used to be looked up as "the student with an empty email",
        // which matched both of them and failed the whole validation with a 500.
        UUID id = uploadId(token, "no-emails.csv", """
                First Name,Last Name,Date of Birth,Gender,Email,Phone
                Sunita,Karki,2012-02-11,FEMALE,,9800000011
                Bimal,Thapa,2012-04-05,MALE,,9800000012
                """);

        JsonNode validated = json(mockMvc.perform(
                post("/api/v1/imports/{id}/validate", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn()).path("data");

        assertThat(validated.path("duplicates")).isEmpty();
        assertThat(validated.path("batch").path("invalidRows").asInt()).isZero();
    }

    @Test
    @DisplayName("a row matching somebody already in the system is reported as a duplicate")
    void duplicatesAreReported() throws Exception {
        String token = adminToken();
        UUID first = uploadId(token, "students.csv", STUDENTS_CSV);
        validate(token, first);
        confirm(token, first);
        assertThat(count("students")).isEqualTo(2);

        // Importing the same two people again: the emails now already exist.
        UUID second = uploadId(token, "students.csv", STUDENTS_CSV);
        JsonNode validated = json(mockMvc.perform(
                post("/api/v1/imports/{id}/validate", second)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn()).path("data");

        assertThat(validated.path("duplicates").toString()).contains("aarav.sharma@example.com");
        // A duplicate is not an error: the rows that were clean the first time are still clean,
        // so the count of unusable rows is the same two the file itself is at fault for.
        assertThat(validated.path("batch").path("invalidRows").asInt()).isEqualTo(2);
        assertThat(validated.path("errors").toString()).doesNotContain("duplicate");
    }

    @Test
    @DisplayName("a fee file with repeated structures makes one structure, not one per row")
    void feeRowsAreGroupedIntoOneStructure() throws Exception {
        String token = adminToken();
        UUID id = uploadId(token, "FEES", "fees.csv", """
                Structure Name,Structure Code,Component Name,Amount,Currency
                Grade 5 Standard,GR5-STD,Tuition,40000,NPR
                Grade 5 Standard,GR5-STD,Admission,3000,NPR
                Grade 5 Standard,GR5-STD,Examination,1500,NPR
                """);
        validate(token, id);
        JsonNode report = confirm(token, id);

        assertThat(report.path("imported").asInt()).as("all three rows are components").isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from fee_structures", Integer.class))
                .as("three rows of one structure must not make three structures").isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from fee_components", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "select total_amount from fee_structures", java.math.BigDecimal.class))
                .isEqualByComparingTo("44500");
    }

    @Test
    @DisplayName("an unknown column in a confirmed mapping is refused by name")
    void mappingRefusesUnknownColumns() throws Exception {
        String token = adminToken();
        UUID id = uploadId(token, "students.csv", STUDENTS_CSV);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/imports/{id}/mapping", id)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("mapping",
                                        java.util.Map.of("firstName", "Telephone")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.message").value(
                                org.hamcrest.Matchers.containsString("Telephone")));
    }

    @Test
    @DisplayName("the type list describes what each import needs")
    void helpDescribesTheTypes() throws Exception {
        JsonNode types = json(mockMvc.perform(get("/api/v1/imports/types")
                        .header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk()).andReturn()).path("data");

        assertThat(types.toString()).contains("STUDENTS", "GUARDIANS", "EMPLOYEES", "COURSES",
                "FEES", "INVENTORY", "BOOKS");
        assertThat(types.toString()).contains("First Name", "Component Name", "Title");
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode upload(String token, String type, String filename, String content)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/v1/imports")
                        .file(file).param("type", type)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return json(result).path("data");
    }

    private UUID uploadId(String token, String filename, String content) throws Exception {
        return uploadId(token, "STUDENTS", filename, content);
    }

    private UUID uploadId(String token, String type, String filename, String content)
            throws Exception {
        return upload(token, type, filename, content).path("batch").path("id").asText()
                .transform(UUID::fromString);
    }

    private void validate(String token, UUID id) throws Exception {
        mockMvc.perform(post("/api/v1/imports/{id}/validate", id)
                .header("Authorization", bearer(token))).andExpect(status().isOk());
    }

    private JsonNode confirm(String token, UUID id) throws Exception {
        return json(mockMvc.perform(post("/api/v1/imports/{id}/confirm", id)
                        .param("skipInvalidRows", "true")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn()).path("data");
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}