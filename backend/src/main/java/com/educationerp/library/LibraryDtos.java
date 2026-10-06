package com.educationerp.library;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What the library reads and writes.
 *
 * <p>Availability is returned with every book rather than stored, and a fine is reported with
 * the days it covers, so the screens can say "three days late" instead of making the reader
 * do the subtraction.
 */
public final class LibraryDtos {

    private LibraryDtos() {
    }

    // ---------------------------------------------------------------- catalogue

    public record CategoryRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description
    ) {
    }

    public record CategoryResponse(UUID id, String code, String name, String description) {
    }

    public record PublisherRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 400) String address,
            @Email @Size(max = 180) String email,
            @Size(max = 60) String phone
    ) {
    }

    public record PublisherResponse(UUID id, String name, String address, String email,
                                    String phone) {
    }

    public record AuthorRequest(
            @NotBlank @Size(max = 200) String name,
            String biography
    ) {
    }

    public record AuthorResponse(UUID id, String name, String biography) {
    }

    public record BookRequest(
            @Size(max = 20) String isbn,
            @NotBlank @Size(max = 300) String title,
            @Size(max = 60) String edition,
            Integer publicationYear,
            @Size(max = 60) String language,
            UUID publisherId,
            UUID categoryId,
            List<UUID> authorIds,
            @Size(max = 60) String callNumber,
            @Size(max = 80) String shelfLocation,
            @Size(max = 400) String coverUrl,
            String description,
            boolean reference
    ) {
    }

    /**
     * A book as the catalogue shows it, with the two figures a member actually asks for: how
     * many copies exist and how many are on the shelf today.
     */
    public record BookResponse(
            UUID id,
            String isbn,
            String title,
            String edition,
            Integer publicationYear,
            String language,
            PublisherResponse publisher,
            CategoryResponse category,
            List<String> authors,
            String callNumber,
            String shelfLocation,
            String coverUrl,
            String description,
            boolean reference,
            long totalCopies,
            long availableCopies
    ) {
    }

    public record CopyRequest(
            @NotBlank @Size(max = 60) String barcode,
            @Size(max = 20) String acquisitionType,
            LocalDate acquiredOn,
            BigDecimal price,
            @Size(max = 20) String conditionStatus,
            @Size(max = 500) String notes
    ) {
    }

    /**
     * The same copy, registered by a librarian with a scanner. The barcode is issued by the
     * system rather than typed, so it is deliberately absent from this request.
     */
    public record GeneratedCopyRequest(
            @Size(max = 20) String acquisitionType,
            LocalDate acquiredOn,
            BigDecimal price,
            @Size(max = 20) String conditionStatus,
            @Size(max = 500) String notes
    ) {
        public CopyRequest withBarcode(String barcode) {
            return new CopyRequest(barcode, acquisitionType, acquiredOn, price,
                    conditionStatus, notes);
        }
    }

    public record CopyResponse(
            UUID id,
            UUID bookId,
            String bookTitle,
            String barcode,
            String acquisitionType,
            LocalDate acquiredOn,
            BigDecimal price,
            String conditionStatus,
            String status,
            String notes
    ) {
    }

    // ------------------------------------------------------------------ members

    /**
     * Registers a member. Exactly one of the four ways of naming them is given: a sign-in
     * account, a student, a member of staff, or an outsider's own details.
     */
    public record MemberRequest(
            @Size(max = 40) String memberCode,
            UUID userId,
            UUID studentId,
            UUID employeeId,
            @Size(max = 200) String externalName,
            @Size(max = 60) String externalPhone,
            @Email @Size(max = 180) String externalEmail,
            @Min(1) @Max(50) Integer maxBooks,
            LocalDate membershipStart,
            LocalDate membershipEnd,
            @Size(max = 20) String status
    ) {
    }

    public record MemberResponse(
            UUID id,
            String memberCode,
            String name,
            UUID userId,
            UUID studentId,
            UUID employeeId,
            String externalName,
            String externalPhone,
            String externalEmail,
            int maxBooks,
            long booksOut,
            LocalDate membershipStart,
            LocalDate membershipEnd,
            String status,
            List<LoanSummary> currentLoans,
            BigDecimal outstandingFines
    ) {
    }

    // -------------------------------------------------------------------- loans

    /**
     * Lends a copy. When {@code memberId} is left out the copy is lent to the signed-in
     * member, which is how a student borrows from their own portal; a librarian names the
     * member instead.
     */
    public record IssueRequest(
            @NotNull UUID copyId,
            UUID memberId,
            Integer loanDays
    ) {
    }

    /**
     * Closing a loan. The fine is worked out from how late the book is unless the counter
     * names its own amount, and {@code waiveFine} needs a reason before it is allowed.
     */
    public record ReturnRequest(
            @Size(max = 20) String conditionIn,
            boolean lost,
            @DecimalMin("0.00") BigDecimal fineAmount,
            @Size(max = 300) String fineReason,
            boolean waiveFine,
            @Size(max = 300) String waiverReason
    ) {
    }

    public record RenewRequest(Integer extraDays) {
    }

    /**
     * A loan as the counter sees it, including whether it is late and by how much. A member
     * sees the same numbers through the member record.
     */
    public record LoanSummary(
            UUID id,
            UUID copyId,
            String barcode,
            UUID bookId,
            String bookTitle,
            String author,
            String memberCode,
            String memberName,
            Instant issuedAt,
            Instant dueAt,
            Instant returnedAt,
            int renewalCount,
            String status,
            boolean overdue,
            long daysOverdue,
            long daysLoaned,
            BigDecimal fineAmount
    ) {
    }

    // ------------------------------------------------------------- reservations

    /**
     * Reserves a title. As with a loan, leaving {@code memberId} out reserves it for the
     * signed-in member rather than for whoever happens to be at the counter.
     */
    public record ReservationRequest(
            @NotNull UUID bookId,
            UUID memberId,
            Integer holdDays
    ) {
    }

    public record ReservationResponse(
            UUID id,
            UUID bookId,
            String bookTitle,
            UUID memberId,
            String memberName,
            Instant reservedAt,
            Instant expiresAt,
            int queuePosition,
            String status
    ) {
    }

    // -------------------------------------------------------------------- fines

    public record FineRequest(
            @NotNull UUID memberId,
            UUID issueId,
            @NotBlank @Size(max = 300) String reason,
            @NotNull @jakarta.validation.constraints.DecimalMin("0.00") BigDecimal amount,
            @Size(max = 10) String currency
    ) {
    }

    public record FineResponse(
            UUID id,
            UUID memberId,
            String memberName,
            UUID issueId,
            String reason,
            BigDecimal amount,
            String currency,
            LocalDate assessedOn,
            Instant paidAt,
            Instant waivedAt,
            String waiverReason,
            String status
    ) {
    }

    // ------------------------------------------------------------------ reports

    /** What the librarian looks at in the morning: what is out, what is late, what is owing. */
    public record LibraryOverview(
            long titles,
            long copies,
            long copiesAvailable,
            long members,
            long loansOut,
            long overdue,
            long waitingReservations,
            BigDecimal finesOutstanding
    ) {
    }
}