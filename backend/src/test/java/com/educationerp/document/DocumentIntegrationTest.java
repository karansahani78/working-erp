package com.educationerp.document;

import com.educationerp.institution.ModuleKey;
import com.educationerp.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The document store: uploads, the versions behind them, and who is allowed to read them.
 *
 * <p>The access rules are the part worth pinning down. A document nobody has been granted is
 * a document that does not exist as far as the rest of the institution is concerned, so these
 * check that a stranger gets a refusal from both the list and the download rather than an
 * empty page or a file.
 */
class DocumentIntegrationTest extends IntegrationTest {

    @BeforeEach
    void enableDocuments() {
        testData.enableModule(ModuleKey.DOCUMENTS);
    }

    @Test
    @DisplayName("an uploaded file is stored with a number and a checksum")
    void anUploadedFileIsStoredWithANumberAndAChecksum() throws Exception {
        String token = adminToken();

        JsonNode row = upload(token, "Birth certificate", "CERTIFICATE", "birth.txt",
                "Birth certificate of Asha Rai.");

        assertThat(row.path("documentNumber").asText()).startsWith("DOC-");
        assertThat(row.path("status").asText()).isEqualTo("DRAFT");
        assertThat(row.path("verificationStatus").asText()).isEqualTo("UNVERIFIED");
        assertThat(row.path("currentVersion").asInt()).isEqualTo(1);
        assertThat(row.path("checksumSha256").asText()).hasSize(64);
        assertThat(row.path("fileSize").asLong()).isPositive();
    }

    @Test
    @DisplayName("a new version keeps the old file rather than replacing it")
    void aNewVersionKeepsTheOldFile() throws Exception {
        String token = adminToken();

        JsonNode original = upload(token, "Birth certificate", "CERTIFICATE", "birth.txt",
                "Scanned copy.");
        UUID id = idOf(original);

        JsonNode second = addVersion(token, id, "birth-corrected.txt", "Notarised copy.");
        assertThat(second.path("currentVersion").asInt()).isEqualTo(2);
        // Different bytes, so the document now points at a different checksum.
        assertThat(second.path("checksumSha256").asText())
                .isNotEqualTo(original.path("checksumSha256").asText());

        String body = mockMvc.perform(get("/api/v1/documents/{id}/versions", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        JsonNode versions = json(body).path("data");
        assertThat(versions.get(0).path("versionNumber").asInt()).isEqualTo(2);
        assertThat(versions.get(1).path("changeNote").asText()).isEqualTo("First version");
    }

    @Test
    @DisplayName("approving a document also brings it into circulation")
    void approvingADocumentBringsItIntoCirculation() throws Exception {
        String token = adminToken();
        UUID id = idOf(upload(token, "Birth certificate", "CERTIFICATE", "birth.txt", "Copy."));

        mockMvc.perform(patch("/api/v1/documents/{id}/verification", id)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"approved\":true,\"note\":\"Checked against the register\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.verifiedAt").isNotEmpty());

        // A rejected one stays on file so the uploader can send a correction.
        UUID other = idOf(upload(token, "Transfer certificate", "CERTIFICATE", "transfer.txt",
                "Copy."));
        mockMvc.perform(patch("/api/v1/documents/{id}/verification", other)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"approved\":false,\"note\":\"Illegible scan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("a document nobody has been granted stays out of other people's lists")
    void anUnsharedDocumentStaysOutOfOtherPeoplesLists() throws Exception {
        String token = adminToken();
        UUID id = idOf(upload(token, "Staff handbook", "POLICY", "handbook.txt", "Rules."));

        String stranger = testData.user("doc.stranger", "Doc Stranger", "doc.stranger@sunrise.edu.test",
                com.educationerp.auth.role.Role.TEACHER, "TestPass123").getUsername();
        String strangerToken = loginToken(stranger, "TestPass123");

        // A teacher with DOCUMENT_READ but no grant is refused the file itself...
        mockMvc.perform(get("/api/v1/documents/{id}/download", id)
                        .header("Authorization", bearer(strangerToken)))
                .andExpect(status().isForbidden());

        // ...and does not see it in the list either.
        String body = mockMvc.perform(get("/api/v1/documents")
                        .header("Authorization", bearer(strangerToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json(body).path("data").path("totalElements").asInt()).isZero();

        // Counts and byte totals give the library away just as well as titles do.
        mockMvc.perform(get("/api/v1/documents/overview")
                        .header("Authorization", bearer(strangerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDocuments").value(0))
                .andExpect(jsonPath("$.data.totalBytes").value(0));

        mockMvc.perform(post("/api/v1/documents/{id}/access", id)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"principalType\":\"ROLE\",\"principalRole\":\"TEACHER\","
                                + "\"accessLevel\":\"VIEW\"}"))
                .andExpect(status().isCreated());

        // The grant is enough to read it and its metadata, and not enough to edit it.
        mockMvc.perform(get("/api/v1/documents/{id}/access-level", id)
                        .header("Authorization", bearer(strangerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canView").value(true))
                .andExpect(jsonPath("$.data.canEdit").value(false));

        mockMvc.perform(get("/api/v1/documents/overview")
                        .header("Authorization", bearer(strangerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDocuments").value(1));

        mockMvc.perform(get("/api/v1/documents/{id}/download", id)
                        .header("Authorization", bearer(strangerToken)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/documents/{id}/verification", id)
                        .header("Authorization", bearer(strangerToken))
                        .contentType("application/json")
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a grant held by a user can be taken back")
    void aGrantCanBeTakenBack() throws Exception {
        String token = adminToken();
        UUID id = idOf(upload(token, "Payroll advice", "PAYSLIP", "advice.txt", "March."));

        String username = testData.user("doc.holder", "Doc Holder", "doc.holder@sunrise.edu.test",
                com.educationerp.auth.role.Role.ACCOUNTANT, "TestPass123").getUsername();
        String holderToken = loginToken(username, "TestPass123");

        String granted = mockMvc.perform(post("/api/v1/documents/{id}/access", id)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"principalType\":\"USER\","
                                + "\"principalUserId\":\"" + userId(username) + "\","
                                + "\"accessLevel\":\"VIEW\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID grantId = idOf(json(granted).path("data"));
        assertThat(grantId).isNotNull();

        mockMvc.perform(delete("/api/v1/documents/{id}/access/{grantId}", id, grantId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/documents/{id}/access-level", id)
                        .header("Authorization", bearer(holderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canView").value(false));
    }

    @Test
    @DisplayName("metadata is edited in place and the change is reflected in the list")
    void metadataIsEditedInPlace() throws Exception {
        String token = adminToken();
        UUID id = idOf(upload(token, "Birth certificate", "CERTIFICATE", "birth.txt", "Copy."));

        mockMvc.perform(put("/api/v1/documents/{id}/metadata", id)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"title\":\"Birth certificate (notarised)\","
                                + "\"documentType\":\"CERTIFICATE\",\"category\":\"Identity\","
                                + "\"notes\":\"Scanned copy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Birth certificate (notarised)"))
                .andExpect(jsonPath("$.data.category").value("Identity"));

        mockMvc.perform(get("/api/v1/documents")
                        .header("Authorization", bearer(token))
                        .param("term", "notarised"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].id").value(id.toString()));
    }

    @Test
    @DisplayName("an expiry before the issue date is refused")
    void anExpiryBeforeTheIssueDateIsRefused() throws Exception {
        String token = adminToken();
        MockMultipartFile file = new MockMultipartFile("file", "passport.txt", "text/plain",
                "Passport copy.".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile metadata = metadata("{\"title\":\"Passport\",\"documentType\":\"IDENTITY\","
                + "\"issuedOn\":\"2024-05-01\",\"expiresOn\":\"2020-05-01\"}");

        mockMvc.perform(multipart("/api/v1/documents").file(file).file(metadata)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("deleting a document takes it out of circulation but leaves the record")
    void deletingADocumentLeavesTheRecord() throws Exception {
        String token = adminToken();
        UUID id = idOf(upload(token, "Old circular", "NOTICE", "circular.txt", "Superseded."));

        mockMvc.perform(delete("/api/v1/documents/{id}", id)
                        .header("Authorization", bearer(token))
                        .param("reason", "Replaced by the 2026 policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.deleted").value(true));

        mockMvc.perform(get("/api/v1/documents")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("what expires soon is counted and listed")
    void whatExpiresSoonIsCountedAndListed() throws Exception {
        String token = adminToken();
        UUID soon = idOf(upload(token, "Insurance policy", "POLICY", "insurance.txt",
                "Cover note.", "{\"expiresOn\":\"" + LocalDate.now().plusDays(30) + "\"}"));
        upload(token, "Land deed", "POLICY", "deed.txt", "Deed.", "{\"expiresOn\":\""
                + LocalDate.now().plusYears(5) + "\"}");

        String body = mockMvc.perform(get("/api/v1/documents/expiring")
                        .header("Authorization", bearer(token))
                        .param("days", "90"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json(body).path("data").findValuesAsText("id")).contains(soon.toString());

        mockMvc.perform(get("/api/v1/documents/overview")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDocuments").value(2))
                .andExpect(jsonPath("$.data.expiringSoon").value(1));
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode upload(String token, String title, String type, String filename, String body)
            throws Exception {
        return upload(token, title, type, filename, body, null);
    }

    private JsonNode upload(String token, String title, String type, String filename, String body,
                            String extraMetadata) throws Exception {
        String metadata = "{\"title\":\"" + title + "\",\"documentType\":\"" + type + "\""
                + (extraMetadata == null ? "" : "," + extraMetadata.substring(1,
                extraMetadata.length() - 1))
                + "}";
        MvcResult result = mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", filename, "text/plain",
                                body.getBytes(StandardCharsets.UTF_8)))
                        .file(metadata(metadata))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).path("data");
    }

    private JsonNode addVersion(String token, UUID id, String filename, String changeNote)
            throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/documents/{id}/versions", id)
                        .file(new MockMultipartFile("file", filename, "text/plain",
                                ("Corrected " + filename).getBytes(StandardCharsets.UTF_8)))
                        .file(metadata("{\"changeNote\":\"" + changeNote + "\"}"))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).path("data");
    }

    private MockMultipartFile metadata(String json) {
        return new MockMultipartFile("metadata", "metadata.json", "application/json",
                json.getBytes(StandardCharsets.UTF_8));
    }

    private UUID userId(String username) {
        return jdbcTemplate.queryForObject("select id from users where username = ?",
                UUID.class, username);
    }

    private UUID idOf(JsonNode row) {
        return UUID.fromString(row.path("id").asText());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return json(result.getResponse().getContentAsString());
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
