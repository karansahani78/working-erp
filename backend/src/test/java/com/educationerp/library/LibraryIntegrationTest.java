package com.educationerp.library;

import com.educationerp.institution.ModuleKey;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cataloguing and circulation.
 *
 * <p>The scanner workflow is the part worth pinning down: a librarian registers a copy
 * without typing a barcode and the system issues one. That endpoint used to reuse the
 * typed-copy request, so validation demanded a barcode the caller was never asked for and
 * the endpoint could only ever answer 400.
 */
class LibraryIntegrationTest extends IntegrationTest {

    @Autowired
    private TestLibraryFixture fixture;

    @BeforeEach
    void enableLibrary() {
        testData.enableModule(ModuleKey.LIBRARY);
    }

    @Test
    @DisplayName("a copy can be registered by a scanner without typing a barcode")
    void generatedBarcodeCopyIsAccepted() throws Exception {
        String token = adminToken();
        String bookId = bookThroughApi(token, "The Guide");

        mockMvc.perform(post("/api/v1/library/books/{id}/copies/generated", bookId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"acquisitionType":"PURCHASE","conditionStatus":"GOOD"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.barcode").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("a typed barcode is still required when the librarian types one")
    void typedCopyStillNeedsABarcode() throws Exception {
        String token = adminToken();
        String bookId = bookThroughApi(token, "Waiting for the Barbarians");

        mockMvc.perform(post("/api/v1/library/books/{id}/copies", bookId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"acquisitionType":"DONATION","conditionStatus":"GOOD"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.barcode").isNotEmpty());
    }

    @Test
    @DisplayName("catalogue counts copies and available copies separately")
    void overviewCountsCopies() throws Exception {
        String token = adminToken();
        UUID bookId = fixture.book("FICT", "The English Patient");
        fixture.copy(bookId);
        fixture.copy(bookId);

        mockMvc.perform(get("/api/v1/library/overview").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.titles").value(1))
                .andExpect(jsonPath("$.data.copies").value(2))
                .andExpect(jsonPath("$.data.copiesAvailable").value(2));

        mockMvc.perform(get("/api/v1/library/books/{id}", bookId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("The English Patient"))
                .andExpect(jsonPath("$.data.totalCopies").value(2))
                .andExpect(jsonPath("$.data.availableCopies").value(2));
    }

    @Test
    @DisplayName("borrowing a copy takes it off the shelf until it is returned")
    void lendingReducesAvailability() throws Exception {
        String token = adminToken();
        UUID bookId = fixture.book("FICT", "A Fine Balance");
        UUID copyId = fixture.copy(bookId);
        UUID memberId = fixture.member("Meena Rai");

        mockMvc.perform(post("/api/v1/library/loans")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "copyId", copyId, "memberId", memberId))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/library/overview").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.copiesAvailable").value(0))
                .andExpect(jsonPath("$.data.loansOut").value(1));

        assertThat(fixture.copyStatus(copyId)).isEqualTo("ISSUED");
    }

    @Test
    @DisplayName("a book only goes out with circulation rights")
    void lendingNeedsCirculationRights() throws Exception {
        String token = adminToken();
        UUID bookId = fixture.book("FICT", "A Borrowed Book");
        UUID copyId = fixture.copy(bookId);

        // The permission used to be asked for only when a member was named, so leaving the
        // member out lent the book on the caller's own card to anyone holding a library
        // card of their own — which is the whole circulation desk, minus the permission.
        var borrower = testData.user("lib.borrower", "Lib Borrower",
                "lib.borrower@sunrise.edu.test",
                com.educationerp.auth.role.Role.STUDENT, "TestPass123");
        String borrowerToken = loginToken(borrower.getUsername(), "TestPass123");
        // Give the borrower a library card of their own. Without one the old code would refuse
        // the loan for want of a member and the test would pass for the wrong reason, proving
        // nothing about the missing permission check.
        fixture.memberForUser(borrower.getId());

        mockMvc.perform(post("/api/v1/library/loans")
                        .header("Authorization", bearer(borrowerToken))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("copyId", copyId))))
                .andExpect(status().isForbidden());

        // Nothing moved, which is the part that matters: a refusal is only useful if it
        // leaves the shelf alone.
        assertThat(fixture.copyStatus(copyId)).isEqualTo("AVAILABLE");

        // The same request from the desk succeeds once the permission is there.
        UUID memberId = fixture.member("Meena Rai");
        mockMvc.perform(post("/api/v1/library/loans")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "copyId", copyId, "memberId", memberId))))
                .andExpect(status().isCreated());
        assertThat(fixture.copyStatus(copyId)).isEqualTo("ISSUED");
    }

    @Test
    @DisplayName("a book cannot be reserved without circulation rights")
    void reservingNeedsCirculationRights() throws Exception {
        UUID bookId = fixture.book("FICT", "A Reserved Book");

        var borrower = testData.user("lib.holder", "Lib Holder",
                "lib.holder@sunrise.edu.test",
                com.educationerp.auth.role.Role.STUDENT, "TestPass123");
        String borrowerToken = loginToken(borrower.getUsername(), "TestPass123");
        fixture.memberForUser(borrower.getId());

        mockMvc.perform(post("/api/v1/library/reservations")
                        .header("Authorization", bearer(borrowerToken))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("bookId", bookId))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a member can be found by name or by card number at the desk")
    void membersCanBeSearchedByNameOrCode() throws Exception {
        String token = adminToken();
        fixture.member("Sita Gurung");

        mockMvc.perform(get("/api/v1/library/members")
                        .header("Authorization", bearer(token))
                        .param("term", "gurung"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].name").value("Sita Gurung"));

        // The card number is what the librarian reads off the card in the reader's hand.
        String code = jdbcTemplate.queryForObject(
                "select member_code from library_members where external_name = 'Sita Gurung'",
                String.class);
        mockMvc.perform(get("/api/v1/library/members")
                        .header("Authorization", bearer(token))
                        .param("term", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // And a search that matches nobody is empty rather than falling back to everybody.
        mockMvc.perform(get("/api/v1/library/members")
                        .header("Authorization", bearer(token))
                        .param("term", "nobody-by-that-name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    private String bookThroughApi(String token, String title) throws Exception {
        mockMvc.perform(post("/api/v1/library/books")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("title", title))))
                .andExpect(status().isCreated());
        return jdbcTemplate.queryForObject(
                "select id from books where title = ?", String.class, title);
    }
}