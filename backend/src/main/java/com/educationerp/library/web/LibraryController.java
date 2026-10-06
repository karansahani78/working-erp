package com.educationerp.library.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.api.PageResponse;
import com.educationerp.library.BookCopy;
import com.educationerp.library.LibraryDtos;
import com.educationerp.library.LibraryFine;
import com.educationerp.library.LibraryMember;
import com.educationerp.library.LibraryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/library")
@Tag(name = "Library")
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService service;

    // ---------------------------------------------------------------- catalogue

    @GetMapping("/books")
    public ApiResponse<PageResponse<LibraryDtos.BookResponse>> books(
            @RequestParam(required = false) String term,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "false") boolean referenceOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.searchBooks(term, categoryId, referenceOnly, pageable));
    }

    @GetMapping("/books/{id}")
    public ApiResponse<LibraryDtos.BookResponse> book(@PathVariable UUID id) {
        return ApiResponse.ok(service.getBook(id));
    }

    @PostMapping("/books")
    public ResponseEntity<ApiResponse<LibraryDtos.BookResponse>> createBook(
            @Valid @RequestBody LibraryDtos.BookRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.createBook(request), "Book catalogued"));
    }

    @PutMapping("/books/{id}")
    public ApiResponse<LibraryDtos.BookResponse> updateBook(
            @PathVariable UUID id, @Valid @RequestBody LibraryDtos.BookRequest request) {
        return ApiResponse.ok(service.updateBook(id, request));
    }

    @DeleteMapping("/books/{id}")
    public ApiResponse<Void> deleteBook(@PathVariable UUID id) {
        service.deleteBook(id);
        return ApiResponse.ok();
    }

    // -------------------------------------------------------------------- copies

    @GetMapping("/books/{bookId}/copies")
    public ApiResponse<PageResponse<LibraryDtos.CopyResponse>> copiesOf(
            @PathVariable UUID bookId, @PageableDefault(size = 50) Pageable pageable) {
        return ApiResponse.ok(service.listCopies(bookId, pageable));
    }

    @PostMapping("/books/{bookId}/copies")
    public ResponseEntity<ApiResponse<LibraryDtos.CopyResponse>> addCopy(
            @PathVariable UUID bookId, @Valid @RequestBody LibraryDtos.CopyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.registerCopy(bookId, request), "Copy registered"));
    }

    /** For the librarian with a scanner: the barcode is issued rather than typed. */
    @PostMapping("/books/{bookId}/copies/generated")
    public ResponseEntity<ApiResponse<LibraryDtos.CopyResponse>> addCopyWithBarcode(
            @PathVariable UUID bookId, @Valid @RequestBody LibraryDtos.GeneratedCopyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.registerCopyWithGeneratedBarcode(bookId, request),
                        "Copy registered"));
    }

    @GetMapping("/copies")
    public ApiResponse<PageResponse<LibraryDtos.CopyResponse>> copiesByStatus(
            @RequestParam(defaultValue = "AVAILABLE") BookCopy.Status status,
            @PageableDefault(size = 50) Pageable pageable) {
        return ApiResponse.ok(service.copiesByStatus(status, pageable));
    }

    @PatchMapping("/copies/{copyId}/status")
    public ApiResponse<LibraryDtos.CopyResponse> copyStatus(
            @PathVariable UUID copyId,
            @RequestParam BookCopy.Status status,
            @RequestParam(required = false) String notes) {
        return ApiResponse.ok(service.changeCopyStatus(copyId, status, notes));
    }

    // ------------------------------------------------------------------- members

    @GetMapping("/members")
    public ApiResponse<PageResponse<LibraryDtos.MemberResponse>> members(
            @RequestParam(defaultValue = "ACTIVE") LibraryMember.Status status,
            @RequestParam(required = false) String term,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.searchMembers(status, term, pageable));
    }

    /** The signed-in person's own card. No identifier, so no way to ask for somebody else's. */
    @GetMapping("/members/me")
    public ApiResponse<LibraryDtos.MemberResponse> myMembership() {
        return ApiResponse.ok(service.myMembership());
    }

    @GetMapping("/members/{id}")
    public ApiResponse<LibraryDtos.MemberResponse> member(@PathVariable UUID id) {
        return ApiResponse.ok(service.getMember(id));
    }

    @PostMapping("/members")
    public ResponseEntity<ApiResponse<LibraryDtos.MemberResponse>> registerMember(
            @Valid @RequestBody LibraryDtos.MemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.registerMember(request), "Member registered"));
    }

    @PutMapping("/members/{id}")
    public ApiResponse<LibraryDtos.MemberResponse> updateMember(
            @PathVariable UUID id, @Valid @RequestBody LibraryDtos.MemberRequest request) {
        return ApiResponse.ok(service.updateMember(id, request));
    }

    // ------------------------------------------------------------ catalogue refs

    @GetMapping("/categories")
    public ApiResponse<List<LibraryDtos.CategoryResponse>> categories() {
        return ApiResponse.ok(service.listCategories());
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LibraryDtos.CategoryResponse> createCategory(
            @Valid @RequestBody LibraryDtos.CategoryRequest request) {
        return ApiResponse.ok(service.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<LibraryDtos.CategoryResponse> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody LibraryDtos.CategoryRequest request) {
        return ApiResponse.ok(service.updateCategory(id, request));
    }

    @GetMapping("/publishers")
    public ApiResponse<List<LibraryDtos.PublisherResponse>> publishers() {
        return ApiResponse.ok(service.listPublishers());
    }

    @PostMapping("/publishers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LibraryDtos.PublisherResponse> createPublisher(
            @Valid @RequestBody LibraryDtos.PublisherRequest request) {
        return ApiResponse.ok(service.createPublisher(request));
    }

    @PutMapping("/publishers/{id}")
    public ApiResponse<LibraryDtos.PublisherResponse> updatePublisher(
            @PathVariable UUID id,
            @Valid @RequestBody LibraryDtos.PublisherRequest request) {
        return ApiResponse.ok(service.updatePublisher(id, request));
    }

    @GetMapping("/authors")
    public ApiResponse<List<LibraryDtos.AuthorResponse>> authors() {
        return ApiResponse.ok(service.listAuthors());
    }

    @PostMapping("/authors")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LibraryDtos.AuthorResponse> createAuthor(
            @Valid @RequestBody LibraryDtos.AuthorRequest request) {
        return ApiResponse.ok(service.createAuthor(request));
    }

    @PutMapping("/authors/{id}")
    public ApiResponse<LibraryDtos.AuthorResponse> updateAuthor(
            @PathVariable UUID id,
            @Valid @RequestBody LibraryDtos.AuthorRequest request) {
        return ApiResponse.ok(service.updateAuthor(id, request));
    }

    // --------------------------------------------------------------------- loans

    @PostMapping("/loans")
    public ResponseEntity<ApiResponse<LibraryDtos.LoanSummary>> issue(
            @Valid @RequestBody LibraryDtos.IssueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.issue(request), "Book issued"));
    }

    @PostMapping("/loans/{id}/return")
    public ApiResponse<LibraryDtos.LoanSummary> giveBack(
            @PathVariable UUID id, @RequestBody(required = false) LibraryDtos.ReturnRequest request) {
        return ApiResponse.ok(service.giveBack(id, request == null
                ? new LibraryDtos.ReturnRequest(null, false, null, null, false, null)
                : request));
    }

    @PostMapping("/loans/{id}/renew")
    public ApiResponse<LibraryDtos.LoanSummary> renew(
            @PathVariable UUID id,
            @RequestBody(required = false) LibraryDtos.RenewRequest request) {
        return ApiResponse.ok(service.renew(id, request == null
                ? new LibraryDtos.RenewRequest(null) : request));
    }

    @GetMapping("/loans/{memberId}")
    public ApiResponse<PageResponse<LibraryDtos.LoanSummary>> loanHistory(
            @PathVariable UUID memberId, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.loanHistory(memberId, pageable));
    }

    @GetMapping("/loans-overdue")
    public ApiResponse<List<LibraryDtos.LoanSummary>> overdue() {
        return ApiResponse.ok(service.overdueLoans());
    }

    // -------------------------------------------------------------- reservations

    @PostMapping("/reservations")
    public ResponseEntity<ApiResponse<LibraryDtos.ReservationResponse>> reserve(
            @Valid @RequestBody LibraryDtos.ReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.reserve(request), "Book reserved"));
    }

    @GetMapping("/reservations/book/{bookId}")
    public ApiResponse<List<LibraryDtos.ReservationResponse>> queueFor(@PathVariable UUID bookId) {
        return ApiResponse.ok(service.queueForBook(bookId));
    }

    @DeleteMapping("/reservations/{id}")
    public ApiResponse<LibraryDtos.ReservationResponse> cancelReservation(@PathVariable UUID id) {
        return ApiResponse.ok(service.cancelReservation(id));
    }

    // --------------------------------------------------------------------- fines

    @PostMapping("/fines")
    public ResponseEntity<ApiResponse<LibraryDtos.FineResponse>> assessFine(
            @Valid @RequestBody LibraryDtos.FineRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.assessFine(request), "Fine assessed"));
    }

    @PostMapping("/fines/{id}/pay")
    public ApiResponse<LibraryDtos.FineResponse> payFine(@PathVariable UUID id) {
        return ApiResponse.ok(service.settleFine(id, false, null));
    }

    @PostMapping("/fines/{id}/waive")
    public ApiResponse<LibraryDtos.FineResponse> waiveFine(
            @PathVariable UUID id, @RequestParam String reason) {
        return ApiResponse.ok(service.settleFine(id, true, reason));
    }

    @GetMapping("/fines")
    public ApiResponse<PageResponse<LibraryDtos.FineResponse>> fines(
            @RequestParam(defaultValue = "OUTSTANDING") LibraryFine.Status status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.listFines(status, pageable));
    }

    // ------------------------------------------------------------------ overview

    @GetMapping("/overview")
    public ApiResponse<LibraryDtos.LibraryOverview> overview() {
        return ApiResponse.ok(service.overview());
    }
}