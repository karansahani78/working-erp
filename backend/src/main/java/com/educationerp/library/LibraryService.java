package com.educationerp.library;

import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.api.PageResponse;
import com.educationerp.common.error.AppException;
import com.educationerp.common.numbering.DocumentSequence;
import com.educationerp.common.numbering.SequenceNumberGenerator;
import com.educationerp.hr.Employee;
import com.educationerp.hr.EmployeeRepository;
import com.educationerp.institution.InstitutionService;
import com.educationerp.institution.ModuleKey;
import com.educationerp.student.Student;
import com.educationerp.student.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The library: a catalogue, the copies on the shelves, and the loans between them.
 *
 * <p>Three rules run through the whole class.
 *
 * <p>A copy is on loan to one person at a time. That is enforced by the database as well as
 * here, because the failure mode is two members walking away with the same book.
 *
 * <p>A loan is history, not a flag. Returning a book adds a row and a timestamp rather than
 * editing the old one, because "what was lent to whom, and in what condition" is the part of
 * a library that matters after the fact.
 *
 * <p>Nothing here invents a due date silently: the loan period is a visible setting, and a
 * renewal says so in a row of its own.
 */
@Service
@RequiredArgsConstructor
public class LibraryService {

    /** Default loan period when nobody states one. Two weeks is the ordinary school term. */
    public static final int DEFAULT_LOAN_DAYS = 14;

    /** A loan may be extended at most this many times, so a book always comes back. */
    public static final int MAX_RENEWALS = 2;

    /** A reservation is held for this long if the holder does not collect it. */
    public static final int DEFAULT_HOLD_DAYS = 3;

    /** A day of lateness costs this, used when a return does not name its own amount. */
    public static final BigDecimal FINE_PER_DAY = new BigDecimal("10.00");

    private static final BigDecimal LOST_FINE = new BigDecimal("500.00");

    private final LibraryCategoryRepository categories;
    private final PublisherRepository publishers;
    private final AuthorRepository authors;
    private final BookRepository books;
    private final BookCopyRepository copies;
    private final LibraryMemberRepository members;
    private final LibraryIssueRepository issues;
    private final LibraryRenewalRepository renewals;
    private final LibraryReservationRepository reservations;
    private final LibraryFineRepository fines;
    private final StudentRepository students;
    private final EmployeeRepository employees;
    private final AuthorizationChecker auth;
    private final AuditService audit;
    private final InstitutionService institutions;
    private final SequenceNumberGenerator numbers;

    // ------------------------------------------------------------------ shelf

    @Transactional(readOnly = true)
    public PageResponse<LibraryDtos.BookResponse> searchBooks(String term, UUID categoryId,
                                                              boolean referenceOnly, Pageable pageable) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_READ");
        String like = term == null || term.isBlank() ? null
                : "%" + term.trim().toLowerCase(Locale.ROOT) + "%";
        Page<Book> page = books.search(categoryId, referenceOnly, like, pageable);
        return PageResponse.from(page, this::bookResponse);
    }

    @Transactional(readOnly = true)
    public LibraryDtos.BookResponse getBook(UUID id) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_READ");
        return bookResponse(requireBook(id));
    }

    @Transactional
    public LibraryDtos.BookResponse createBook(LibraryDtos.BookRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_MANAGE");
        if (request.isbn() != null && !request.isbn().isBlank()) {
            String isbn = request.isbn().trim();
            books.findByIsbn(isbn).ifPresent(existing -> {
                throw AppException.duplicate("ISBN " + isbn + " is already catalogued.");
            });
        }
        Book book = new Book();
        apply(book, request);
        return bookResponse(books.save(book));
    }

    @Transactional
    public LibraryDtos.BookResponse updateBook(UUID id, LibraryDtos.BookRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_MANAGE");
        Book book = requireBook(id);
        apply(book, request);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("Book")
                .entityId(id.toString())
                .entityLabel(book.getTitle())
                .summary("Updated the catalogue entry")
                .module("LIBRARY")
                .succeeded(true)
                .build());
        return bookResponse(books.save(book));
    }

    /**
     * Withdraws a title from the catalogue.
     *
     * <p>Refused while copies are still on loan: removing the entry would leave those loans
     * pointing at something the catalogue no longer admits exists.
     */
    @Transactional
    public void deleteBook(UUID id) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_MANAGE");
        Book book = requireBook(id);
        long onLoan = copies.countByBookIdAndStatus(id, BookCopy.Status.ISSUED);
        if (onLoan > 0) {
            throw AppException.rule("This book still has " + onLoan + " copy/copies on loan. "
                    + "Wait for them back or mark them lost first.");
        }
        books.delete(book);
        audit.record(AuditEvent.builder()
                .action(AuditAction.DELETE)
                .entityType("Book")
                .entityId(id.toString())
                .entityLabel(book.getTitle())
                .summary("Removed the catalogue entry")
                .module("LIBRARY")
                .succeeded(true)
                .build());
    }

    @Transactional
    public LibraryDtos.CopyResponse registerCopy(UUID bookId, LibraryDtos.CopyRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_MANAGE");
        Book book = requireBook(bookId);
        String barcode = request.barcode().trim();
        copies.findByBarcodeIgnoreCase(barcode).ifPresent(existing -> {
            throw AppException.duplicate("Barcode " + barcode + " is already on a copy.");
        });
        BookCopy copy = new BookCopy();
        copy.setBook(book);
        copy.setBarcode(barcode);
        applyCopy(copy, request);
        BookCopy saved = copies.save(copy);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("BookCopy")
                .entityId(saved.getId().toString())
                .entityLabel(saved.getBarcode())
                .summary("Registered a copy of " + book.getTitle())
                .module("LIBRARY")
                .succeeded(true)
                .build());
        return copyResponse(saved);
    }

    /** Barcode-generated copy number, for the librarian stocking a shelf. */
    @Transactional
    public LibraryDtos.CopyResponse registerCopyWithGeneratedBarcode(UUID bookId,
                                                                    LibraryDtos.GeneratedCopyRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_MANAGE");
        return registerCopy(bookId, request.withBarcode(numbers.next(DocumentSequence.Kind.BARCODE)));
    }

    @Transactional(readOnly = true)
    public PageResponse<LibraryDtos.CopyResponse> listCopies(UUID bookId, Pageable pageable) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_READ");
        requireBook(bookId);
        List<BookCopy> held = copies.findByBookIdOrderByBarcodeAsc(bookId);
        return PageResponse.from(new PageImpl<>(held, pageable, held.size()), this::copyResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<LibraryDtos.CopyResponse> copiesByStatus(BookCopy.Status status,
                                                                Pageable pageable) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_READ");
        return PageResponse.from(copies.byStatus(status, pageable), this::copyResponse);
    }

    /** Marks a copy withdrawn, damaged or missing without touching its loan history. */
    @Transactional
    public LibraryDtos.CopyResponse changeCopyStatus(UUID copyId, BookCopy.Status status,
                                                     String notes) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_MANAGE");
        BookCopy copy = requireCopy(copyId);
        if (copy.getStatus() == BookCopy.Status.ISSUED) {
            throw AppException.rule("This copy is on loan. Record a return or mark the loan lost first.");
        }
        BookCopy.Status from = copy.getStatus();
        copy.setStatus(status);
        if (notes != null && !notes.isBlank()) {
            copy.setNotes(notes.trim());
        }
        BookCopy saved = copies.save(copy);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("BookCopy")
                .entityId(saved.getId().toString())
                .entityLabel(saved.getBarcode())
                .summary("Copy status " + from + " -> " + status)
                .module("LIBRARY")
                .succeeded(true)
                .build());
        return copyResponse(saved);
    }

    // ------------------------------------------------------------------ members

    @Transactional
    public LibraryDtos.MemberResponse registerMember(LibraryDtos.MemberRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_MANAGE");
        LibraryMember member = new LibraryMember();
        applyMember(member, request);
        if (member.getMemberCode() == null || member.getMemberCode().isBlank()) {
            member.setMemberCode(numbers.next(DocumentSequence.Kind.MEMBER));
        }
        members.findByMemberCodeIgnoreCase(member.getMemberCode()).ifPresent(existing -> {
            throw AppException.duplicate("Member code " + member.getMemberCode() + " is already in use.");
        });
        checkMemberNotAlreadyLinked(member);
        LibraryMember saved = members.save(member);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("LibraryMember")
                .entityId(saved.getId().toString())
                .entityLabel(saved.getMemberCode())
                .summary("Registered a library member")
                .module("LIBRARY")
                .succeeded(true)
                .build());
        return memberResponse(saved);
    }

    @Transactional
    public LibraryDtos.MemberResponse updateMember(UUID id, LibraryDtos.MemberRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_MANAGE");
        LibraryMember member = requireMember(id);
        String previousCode = member.getMemberCode();
        applyMember(member, request);
        if (member.getMemberCode() == null || member.getMemberCode().isBlank()) {
            member.setMemberCode(previousCode);
        }
        members.findByMemberCodeIgnoreCase(member.getMemberCode())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw AppException.duplicate("Member code " + member.getMemberCode()
                            + " is already in use.");
                });
        checkMemberNotAlreadyLinked(member);
        return memberResponse(members.save(member));
    }

    @Transactional(readOnly = true)
    public LibraryDtos.MemberResponse getMember(UUID id) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_READ");
        return memberResponse(requireMember(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<LibraryDtos.MemberResponse> listMembers(LibraryMember.Status status,
                                                               Pageable pageable) {
        return listMembers(status, null, pageable);
    }

    /** The same list, narrowed to what somebody typed at the counter. */
    @Transactional(readOnly = true)
    public PageResponse<LibraryDtos.MemberResponse> searchMembers(LibraryMember.Status status,
                                                                 String term, Pageable pageable) {
        return listMembers(status, term, pageable);
    }

    private PageResponse<LibraryDtos.MemberResponse> listMembers(LibraryMember.Status status,
                                                                String term, Pageable pageable) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_READ");
        String trimmed = term == null ? "" : term.trim();
        return PageResponse.from(trimmed.isEmpty()
                ? members.findByStatusOrderByMemberCodeAsc(status, pageable)
                : members.search(status, "%" + trimmed.toLowerCase(Locale.ROOT) + "%", pageable),
                this::memberResponse);
    }

    /**
     * The signed-in person's own library card, with what they currently have out.
     *
     * <p>Found from the token, not from a path: a member may only ever read their own card,
     * so there is no identifier here to get wrong.
     */
    @Transactional(readOnly = true)
    public LibraryDtos.MemberResponse myMembership() {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        UUID userId = auth.requireUser().userId();
        LibraryMember member = membershipFor(userId)
                .orElseThrow(() -> AppException.notFound("Library membership"));
        return memberResponse(member);
    }

    // -------------------------------------------------------------------- loans

    /**
     * Lends a copy to a member.
     *
     * <p>Everything that could make this the wrong thing to do is checked before the copy
     * moves: the copy is on the shelf, the title is lendable, the member can still borrow, and
     * they are not already holding the limit.
     */
    @Transactional
    public LibraryDtos.LoanSummary issue(LibraryDtos.IssueRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        // Always, and not only when a member is named: leaving the member out is how a book
        // goes out on the caller's own card, which is still a circulation decision.
        auth.requirePermission("LIBRARY_CIRCULATE");
        BookCopy copy = requireCopy(request.copyId());
        if (copy.getBook().isReference()) {
            throw AppException.rule("Reference books are for reading in the library, not lending.");
        }
        if (copy.getStatus() == BookCopy.Status.ISSUED) {
            throw AppException.rule("Copy " + copy.getBarcode() + " is already on loan.");
        }
        if (!copy.isLendable()) {
            throw AppException.rule("Copy " + copy.getBarcode() + " is " + copy.getStatus()
                    + " and cannot be lent.");
        }
        LibraryMember member = memberFor(request.memberId());
        LocalDate today = LocalDate.now();
        if (!member.isActiveOn(today)) {
            throw AppException.rule("Membership " + member.getMemberCode() + " is "
                    + member.getStatus().name().toLowerCase(Locale.ROOT)
                    + (member.getMembershipEnd() != null && today.isAfter(member.getMembershipEnd())
                       ? " and expired on " + member.getMembershipEnd() : "") + ".");
        }
        long out = issues.countByMemberIdAndReturnedAtIsNull(member.getId());
        if (out >= member.getMaxBooks()) {
            throw AppException.rule(member.getMemberCode() + " already has " + out
                    + " book(s) out, which is their limit. Return one first.");
        }
        int days = request.loanDays() == null || request.loanDays() <= 0
                ? DEFAULT_LOAN_DAYS : request.loanDays();
        LibraryIssue issue = new LibraryIssue();
        issue.setCopy(copy);
        issue.setMember(member);
        issue.setIssuedAt(Instant.now());
        issue.setDueAt(Instant.now().plus(Duration.ofDays(days)));
        issue.setConditionOut(copy.getConditionStatus());
        issue.setStatus(LibraryIssue.Status.ISSUED);
        LibraryIssue saved = issues.save(issue);
        copy.setStatus(BookCopy.Status.ISSUED);
        copies.save(copy);
        fulfilReservationIfHolder(copy, member, saved);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("LibraryIssue")
                .entityId(saved.getId().toString())
                .entityLabel(copy.getBarcode())
                .summary("Issued " + copy.getBook().getTitle() + " to " + member.getMemberCode()
                        + ", due " + saved.getDueAt().atZone(ZoneOffset.UTC).toLocalDate())
                .module("LIBRARY")
                .succeeded(true)
                .build());
        return loanSummary(saved);
    }

    /**
     * Takes a copy back.
     *
     * <p>A late return carries a fine unless the librarian waives it, and the waiver keeps its
     * reason. A copy returned damaged is recorded as such, which is what later justifies
     * replacing it.
     */
    @Transactional
    public LibraryDtos.LoanSummary giveBack(UUID issueId, LibraryDtos.ReturnRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_CIRCULATE");
        LibraryIssue issue = requireIssue(issueId);
        if (issue.getReturnedAt() != null) {
            throw AppException.rule("This loan was already closed on "
                    + issue.getReturnedAt().atZone(ZoneOffset.UTC).toLocalDate() + ".");
        }
        boolean lost = request.lost();
        Instant now = Instant.now();
        issue.setReturnedAt(now);
        issue.setStatus(lost ? LibraryIssue.Status.LOST : LibraryIssue.Status.RETURNED);
        if (request.conditionIn() != null && !request.conditionIn().isBlank()) {
            BookCopy.ConditionStatus condition;
            try {
                condition = BookCopy.ConditionStatus.valueOf(
                        request.conditionIn().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw AppException.rule("Unknown condition: " + request.conditionIn());
            }
            issue.setConditionIn(condition);
        }
        LibraryIssue saved = issues.save(issue);

        BookCopy copy = issue.getCopy();
        copy.setStatus(lost ? BookCopy.Status.LOST : BookCopy.Status.AVAILABLE);
        if (issue.getConditionIn() != null) {
            copy.setConditionStatus(issue.getConditionIn());
        }
        copies.save(copy);

        BigDecimal fineAmount = fineFor(issue, request, lost);
        if (fineAmount != null && fineAmount.signum() > 0) {
            LibraryFine fine = new LibraryFine();
            fine.setMember(saved.getMember());
            fine.setIssue(saved);
            fine.setReason(request.fineReason() != null && !request.fineReason().isBlank()
                    ? request.fineReason().trim()
                    : (lost ? "Copy reported lost" : "Returned late"));
            fine.setAmount(fineAmount);
            fine.setStatus(LibraryFine.Status.OUTSTANDING);
            if (request.waiveFine()) {
                if (request.waiverReason() == null || request.waiverReason().isBlank()) {
                    throw AppException.rule("Say why the fine is being waived.");
                }
                fine.setStatus(LibraryFine.Status.WAIVED);
                fine.setWaivedAt(now);
                fine.setWaiverReason(request.waiverReason().trim());
            }
            saved.setFine(fines.save(fine));
            issues.save(saved);
        }
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("LibraryIssue")
                .entityId(saved.getId().toString())
                .entityLabel(copy.getBarcode())
                .summary(saved.getStatus() + " loan of " + copy.getBook().getTitle())
                .module("LIBRARY")
                .succeeded(true)
                .build());
        return loanSummary(saved);
    }

    /** Extends a loan, keeping the old date so the history of the extension is readable. */
    @Transactional
    public LibraryDtos.LoanSummary renew(UUID issueId, LibraryDtos.RenewRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        // The caller's own membership is resolved before the loan, so somebody without the
        // permission cannot probe which loan ids exist.
        Optional<LibraryMember> mine = membershipFor(auth.requireUser().userId());
        if (mine.isEmpty()) {
            auth.requirePermission("LIBRARY_CIRCULATE");
        }
        LibraryIssue issue = requireIssue(issueId);
        if (mine.isPresent() && !mine.get().getId().equals(issue.getMember().getId())) {
            throw AppException.denied("You can only extend your own loans.");
        }
        if (issue.getReturnedAt() != null) {
            throw AppException.rule("This loan is closed; the copy cannot be extended.");
        }
        if (issue.getRenewalCount() >= MAX_RENEWALS) {
            throw AppException.rule("This loan has already been extended " + MAX_RENEWALS
                    + " times. Ask for it back.");
        }
        if (reservations.findFirstByBookIdAndMemberIdAndStatus(issue.getCopy().getBook().getId(),
                issue.getMember().getId(), LibraryReservation.Status.WAITING).isPresent()) {
            throw AppException.rule("Somebody is waiting for this book. Renewals are not allowed.");
        }
        int extra = request.extraDays() == null || request.extraDays() <= 0
                ? DEFAULT_LOAN_DAYS : request.extraDays();
        LibraryRenewal renewal = new LibraryRenewal();
        renewal.setIssue(issue);
        renewal.setPreviousDueAt(issue.getDueAt());
        renewal.setNewDueAt(issue.getDueAt().plus(Duration.ofDays(extra)));
        renewals.save(renewal);
        issue.setDueAt(renewal.getNewDueAt());
        issue.setRenewalCount(issue.getRenewalCount() + 1);
        LibraryIssue saved = issues.save(issue);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("LibraryIssue")
                .entityId(saved.getId().toString())
                .entityLabel(issue.getCopy().getBarcode())
                .summary("Extended the loan to " + saved.getDueAt().atZone(ZoneOffset.UTC).toLocalDate())
                .module("LIBRARY")
                .succeeded(true)
                .build());
        return loanSummary(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<LibraryDtos.LoanSummary> loanHistory(UUID memberId, Pageable pageable) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_READ");
        requireMember(memberId);
        return PageResponse.from(issues.findHistoryForMember(memberId, pageable),
                this::loanSummary);
    }

    /** Everything out past its date, oldest first. */
    @Transactional(readOnly = true)
    public List<LibraryDtos.LoanSummary> overdueLoans() {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_READ");
        return issues.findOverdueBefore(Instant.now()).stream().map(this::loanSummary).toList();
    }

    // ------------------------------------------------------------- reservations

    @Transactional
    public LibraryDtos.ReservationResponse reserve(LibraryDtos.ReservationRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_CIRCULATE");
        Book book = requireBook(request.bookId());
        LibraryMember member = memberFor(request.memberId());
        reservations.findFirstByBookIdAndMemberIdAndStatus(book.getId(), member.getId(),
                        LibraryReservation.Status.WAITING)
                .ifPresent(existing -> {
                    throw AppException.duplicate(member.getMemberCode() + " is already waiting "
                            + "for this book.");
                });
        int ahead = reservations.findQueueForBook(book.getId(),
                LibraryReservation.Status.WAITING).size() + 1;
        LibraryReservation reservation = new LibraryReservation();
        reservation.setBook(book);
        reservation.setMember(member);
        reservation.setReservedAt(Instant.now());
        reservation.setExpiresAt(Instant.now()
                .plus(Duration.ofDays(request.holdDays() == null || request.holdDays() <= 0
                        ? DEFAULT_HOLD_DAYS : request.holdDays())));
        reservation.setQueuePosition(ahead);
        reservation.setStatus(LibraryReservation.Status.WAITING);
        LibraryReservation saved = reservations.save(reservation);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("LibraryReservation")
                .entityId(saved.getId().toString())
                .entityLabel(book.getTitle())
                .summary(member.getMemberCode() + " took position " + ahead + " in the queue")
                .module("LIBRARY")
                .succeeded(true)
                .build());
        return reservationResponse(saved);
    }

    @Transactional
    public LibraryDtos.ReservationResponse cancelReservation(UUID reservationId) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_CIRCULATE");
        LibraryReservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> AppException.notFound("Reservation"));
        if (reservation.getStatus() != LibraryReservation.Status.WAITING) {
            throw AppException.rule("This reservation is already " + reservation.getStatus() + ".");
        }
        reservation.setStatus(LibraryReservation.Status.CANCELLED);
        return reservationResponse(reservations.save(reservation));
    }

    @Transactional(readOnly = true)
    public List<LibraryDtos.ReservationResponse> queueForBook(UUID bookId) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_READ");
        requireBook(bookId);
        return reservations.findQueueForBook(bookId, LibraryReservation.Status.WAITING)
                .stream().map(this::reservationResponse).toList();
    }

    // -------------------------------------------------------------------- fines

    @Transactional
    public LibraryDtos.FineResponse assessFine(LibraryDtos.FineRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_MANAGE");
        LibraryFine fine = new LibraryFine();
        fine.setMember(requireMember(request.memberId()));
        if (request.issueId() != null) {
            fine.setIssue(requireIssue(request.issueId()));
        }
        fine.setReason(request.reason().trim());
        fine.setAmount(request.amount());
        if (request.currency() != null && !request.currency().isBlank()) {
            fine.setCurrency(request.currency().trim().toUpperCase(Locale.ROOT));
        }
        fine.setStatus(LibraryFine.Status.OUTSTANDING);
        LibraryFine saved = fines.save(fine);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("LibraryFine")
                .entityId(saved.getId().toString())
                .entityLabel(saved.getMember().getMemberCode())
                .summary("Assessed a fine of " + saved.getAmount() + " " + saved.getCurrency())
                .module("LIBRARY")
                .succeeded(true)
                .build());
        return fineResponse(saved);
    }

    @Transactional
    public LibraryDtos.FineResponse settleFine(UUID fineId, boolean waived, String reason) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_MANAGE");
        LibraryFine fine = fines.findById(fineId).orElseThrow(() -> AppException.notFound("Fine"));
        if (fine.isSettled()) {
            throw AppException.rule("This fine is already " + fine.getStatus().name()
                    .toLowerCase(Locale.ROOT) + ".");
        }
        if (waived) {
            if (reason == null || reason.isBlank()) {
                throw AppException.rule("Say why the fine is being waived.");
            }
            fine.setStatus(LibraryFine.Status.WAIVED);
            fine.setWaivedAt(Instant.now());
            fine.setWaiverReason(reason.trim());
        } else {
            fine.setStatus(LibraryFine.Status.PAID);
            fine.setPaidAt(Instant.now());
        }
        return fineResponse(fines.save(fine));
    }

    @Transactional(readOnly = true)
    public PageResponse<LibraryDtos.FineResponse> listFines(LibraryFine.Status status,
                                                           Pageable pageable) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_READ");
        return PageResponse.from(fines.findByStatusOrderByAssessedOnAsc(status, pageable),
                this::fineResponse);
    }

    // ------------------------------------------------------------------ reports

    /** The morning screen: what is out, what is late, what is owing. */
    @Transactional(readOnly = true)
    public LibraryDtos.LibraryOverview overview() {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_READ");
        long titles = books.count();
        long copiesHeld = copies.count();
        long available = copies.countByStatus(BookCopy.Status.AVAILABLE);
        long memberCount = members.count();
        long out = issues.countByReturnedAtIsNull();
        long overdue = issues.findOverdueBefore(Instant.now()).size();
        long waiting = reservations.countByStatus(LibraryReservation.Status.WAITING);
        BigDecimal owing = fines.sumByStatus(LibraryFine.Status.OUTSTANDING);
        return new LibraryDtos.LibraryOverview(titles, copiesHeld, available, memberCount, out,
                overdue, waiting, owing);
    }

    // ------------------------------------------------------------- catalogue refs

    /**
     * The three small reference lists a book is assembled from.
     *
     * <p>Categories, publishers and authors are not deleted when a book still points at them,
     * because the book's own record would then name something that no longer exists. They are
     * retired instead, which hides them from the pickers while leaving the history readable.
     */

    public LibraryDtos.CategoryResponse createCategory(LibraryDtos.CategoryRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_MANAGE");
        if (categories.findByCodeIgnoreCase(request.code().trim()).isPresent()) {
            throw AppException.duplicate("A category with code " + request.code().trim()
                    + " already exists.");
        }
        LibraryCategory category = new LibraryCategory();
        category.setCode(request.code().trim());
        category.setName(request.name().trim());
        category.setDescription(request.description());
        return categoryResponse(categories.save(category));
    }

    public LibraryDtos.CategoryResponse updateCategory(UUID id,
                                                       LibraryDtos.CategoryRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_MANAGE");
        LibraryCategory category = category(id);
        categories.findByCodeIgnoreCase(request.code().trim()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw AppException.duplicate("A category with code " + request.code().trim()
                        + " already exists.");
            }
        });
        category.setCode(request.code().trim());
        category.setName(request.name().trim());
        category.setDescription(request.description());
        return categoryResponse(category);
    }

    public List<LibraryDtos.CategoryResponse> listCategories() {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_READ");
        return categories.findAll().stream().map(this::categoryResponse).toList();
    }

    public LibraryDtos.PublisherResponse createPublisher(LibraryDtos.PublisherRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_MANAGE");
        if (publishers.findByNameIgnoreCase(request.name().trim()).isPresent()) {
            throw AppException.duplicate("A publisher called " + request.name().trim()
                    + " is already on file.");
        }
        Publisher publisher = new Publisher();
        publisher.setName(request.name().trim());
        publisher.setAddress(request.address());
        publisher.setContactEmail(request.email());
        publisher.setContactPhone(request.phone());
        return publisherResponse(publishers.save(publisher));
    }

    public LibraryDtos.PublisherResponse updatePublisher(UUID id,
                                                         LibraryDtos.PublisherRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_MANAGE");
        Publisher publisher = publisher(id);
        publisher.setName(request.name().trim());
        publisher.setAddress(request.address());
        publisher.setContactEmail(request.email());
        publisher.setContactPhone(request.phone());
        return publisherResponse(publisher);
    }

    public List<LibraryDtos.PublisherResponse> listPublishers() {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_READ");
        return publishers.findAll().stream().map(this::publisherResponse).toList();
    }

    public LibraryDtos.AuthorResponse createAuthor(LibraryDtos.AuthorRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_MANAGE");
        if (authors.findByNameIgnoreCase(request.name().trim()).isPresent()) {
            throw AppException.duplicate("An author called " + request.name().trim()
                    + " is already on file.");
        }
        Author author = new Author();
        author.setName(request.name().trim());
        author.setBiography(request.biography());
        return authorResponse(authors.save(author));
    }

    public LibraryDtos.AuthorResponse updateAuthor(UUID id, LibraryDtos.AuthorRequest request) {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_MANAGE");
        Author author = author(id);
        author.setName(request.name().trim());
        author.setBiography(request.biography());
        return authorResponse(author);
    }

    public List<LibraryDtos.AuthorResponse> listAuthors() {
        institutions.requireModuleEnabled(ModuleKey.LIBRARY);
        auth.requirePermission("LIBRARY_READ");
        return authors.findAll().stream().map(this::authorResponse).toList();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * If the copy is going to the person at the head of the queue, that reservation is done.
     *
     * <p>The queue then moves up rather than being rebuilt, so positions stay stable: somebody
     * told "you are second" keeps that meaning while the person ahead is reading the book.
     */
    private void fulfilReservationIfHolder(BookCopy copy, LibraryMember member,
                                           LibraryIssue issue) {
        List<LibraryReservation> queue = reservations.findQueueForBook(copy.getBook().getId(),
                LibraryReservation.Status.WAITING);
        if (queue.isEmpty() || !queue.get(0).getMember().getId().equals(member.getId())) {
            return;
        }
        LibraryReservation next = queue.get(0);
        next.setStatus(LibraryReservation.Status.FULFILLED);
        next.setFulfilledIssue(issue);
        reservations.save(next);
        queue.stream().skip(1).forEach(waiting -> {
            waiting.setQueuePosition(waiting.getQueuePosition() - 1);
            reservations.save(waiting);
        });
    }

    /**
     * What this return is worth.
     *
     * <p>Computed the same way whether or not the fine is being waived: a waived fine is still a
     * fine, and writing the row is the only way the decision to forgive it is on record. If the
     * waiver short-circuited here, waiving an overdue fine would leave no trace at all.
     */
    private BigDecimal fineFor(LibraryIssue issue, LibraryDtos.ReturnRequest request, boolean lost) {
        if (request.fineAmount() != null) {
            return request.fineAmount();
        }
        if (lost) {
            return LOST_FINE;
        }
        long daysLate = issue.daysOverdue(LocalDate.now());
        if (daysLate <= 0) {
            return null;
        }
        return FINE_PER_DAY.multiply(BigDecimal.valueOf(daysLate));
    }

    /**
     * A person may hold one membership.
     *
     * <p>Two cards for the same human would mean two limits, and a member could simply borrow
     * twice as much as the policy allows.
     */
    private void checkMemberNotAlreadyLinked(LibraryMember member) {
        Optional<LibraryMember> clash = Optional.empty();
        if (member.getUserId() != null) {
            clash = members.findByUserId(member.getUserId());
        } else if (member.getStudentId() != null) {
            clash = members.findByStudentId(member.getStudentId());
        } else if (member.getEmployeeId() != null) {
            clash = members.findByEmployeeId(member.getEmployeeId());
        }
        clash.filter(existing -> !existing.getId().equals(member.getId()))
                .ifPresent(existing -> {
                    throw AppException.duplicate("That person already holds membership "
                            + existing.getMemberCode() + ".");
                });
    }

    /**
     * Who the copy is going to.
     *
     * <p>A librarian names the member on the request; anybody borrowing for themselves may
     * leave it out, and then the copy goes to the membership their own account holds.
     */
    /**
     * Who may take this copy out.
     *
     * <p>A member serving themselves needs nothing but their membership -- otherwise the desk
     * has to issue every book on a student's behalf, which defeats having cards at all.
     * Naming somebody else is a counter transaction and still needs the permission.
     */
    private LibraryMember memberFor(UUID memberId) {
        if (memberId != null) {
            return requireMember(memberId);
        }
        return membershipFor(auth.requireUser().userId())
                .orElseThrow(() -> AppException.denied(
                        "Only a registered member may borrow. Issue this person's card first."));
    }

    /** The membership belonging to a sign-in account, however that account is linked. */
    private Optional<LibraryMember> membershipFor(UUID userId) {
        Optional<LibraryMember> byAccount = members.findByUserId(userId);
        if (byAccount.isPresent()) {
            return byAccount;
        }
        return students.findByUserId(userId).flatMap(student -> members.findByStudentId(student.getId()))
                .or(() -> employees.findByUserId(userId)
                        .flatMap(employee -> members.findByEmployeeId(employee.getId())));
    }

    private void apply(Book book, LibraryDtos.BookRequest request) {
        book.setIsbn(trimToNull(request.isbn()));
        book.setTitle(request.title().trim());
        book.setEdition(trimToNull(request.edition()));
        book.setPublicationYear(request.publicationYear());
        book.setLanguage(trimToNull(request.language()));
        book.setPublisher(request.publisherId() == null ? null
                : publishers.findById(request.publisherId())
                        .orElseThrow(() -> AppException.notFound("Publisher")));
        book.setCategory(request.categoryId() == null ? null
                : categories.findById(request.categoryId())
                        .orElseThrow(() -> AppException.notFound("Library category")));
        book.setCallNumber(trimToNull(request.callNumber()));
        book.setShelfLocation(trimToNull(request.shelfLocation()));
        book.setCoverUrl(trimToNull(request.coverUrl()));
        book.setDescription(trimToNull(request.description()));
        book.setReference(request.reference());
        book.getAuthors().clear();
        if (request.authorIds() != null) {
            request.authorIds().forEach(id -> book.getAuthors().add(
                    authors.findById(id).orElseThrow(() -> AppException.notFound("Author"))));
        }
    }

    private void applyCopy(BookCopy copy, LibraryDtos.CopyRequest request) {
        copy.setAcquisitionType(parseEnum(BookCopy.AcquisitionType.class,
                request.acquisitionType(), BookCopy.AcquisitionType.PURCHASE,
                "acquisition type"));
        copy.setAcquiredOn(request.acquiredOn() == null ? LocalDate.now() : request.acquiredOn());
        copy.setPrice(request.price());
        copy.setConditionStatus(parseEnum(BookCopy.ConditionStatus.class,
                request.conditionStatus(), BookCopy.ConditionStatus.GOOD, "condition"));
        copy.setNotes(trimToNull(request.notes()));
    }

    private void applyMember(LibraryMember member, LibraryDtos.MemberRequest request) {
        member.setMemberCode(trimToNull(request.memberCode()));
        member.setUserId(request.userId());
        member.setStudentId(request.studentId());
        member.setEmployeeId(request.employeeId());
        member.setExternalName(trimToNull(request.externalName()));
        member.setExternalPhone(trimToNull(request.externalPhone()));
        member.setExternalEmail(trimToNull(request.externalEmail()));
        if (request.maxBooks() != null) {
            member.setMaxBooks(request.maxBooks());
        }
        member.setMembershipStart(request.membershipStart() == null
                ? LocalDate.now() : request.membershipStart());
        member.setMembershipEnd(request.membershipEnd());
        member.setStatus(parseEnum(LibraryMember.Status.class, request.status(),
                LibraryMember.Status.ACTIVE, "membership status"));
        int named = 0;
        if (request.userId() != null) named++;
        if (request.studentId() != null) named++;
        if (request.employeeId() != null) named++;
        if (request.externalName() != null && !request.externalName().isBlank()) named++;
        if (named == 0) {
            throw AppException.rule("Name the member by sign-in account, student, staff record "
                    + "or their own details. A membership has to belong to somebody.");
        }
        if (request.membershipEnd() != null
                && request.membershipEnd().isBefore(member.getMembershipStart())) {
            throw AppException.rule("The membership cannot end before it starts.");
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback,
                                                    String what) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw AppException.rule("Unknown " + what + ": " + value);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Book requireBook(UUID id) {
        return books.findById(id).orElseThrow(() -> AppException.notFound("Book"));
    }

    private BookCopy requireCopy(UUID id) {
        return copies.findById(id).orElseThrow(() -> AppException.notFound("Book copy"));
    }

    private LibraryMember requireMember(UUID id) {
        return members.findById(id).orElseThrow(() -> AppException.notFound("Library member"));
    }

    private LibraryIssue requireIssue(UUID id) {
        return issues.findById(id).orElseThrow(() -> AppException.notFound("Loan"));
    }

    private LibraryDtos.CategoryResponse categoryResponse(LibraryCategory category) {
        return new LibraryDtos.CategoryResponse(category.getId(), category.getCode(),
                category.getName(), category.getDescription());
    }

    private LibraryDtos.PublisherResponse publisherResponse(Publisher publisher) {
        return new LibraryDtos.PublisherResponse(publisher.getId(), publisher.getName(),
                publisher.getAddress(), publisher.getContactEmail(), publisher.getContactPhone());
    }

    private LibraryDtos.AuthorResponse authorResponse(Author author) {
        return new LibraryDtos.AuthorResponse(author.getId(), author.getName(),
                author.getBiography());
    }

    private LibraryCategory category(UUID id) {
        return categories.findById(id).orElseThrow(() -> AppException.notFound("Category"));
    }

    private Publisher publisher(UUID id) {
        return publishers.findById(id).orElseThrow(() -> AppException.notFound("Publisher"));
    }

    private Author author(UUID id) {
        return authors.findById(id).orElseThrow(() -> AppException.notFound("Author"));
    }

    private LibraryDtos.BookResponse bookResponse(Book book) {
        List<String> authorNames = book.getAuthors().stream().map(Author::getName).sorted().toList();
        return new LibraryDtos.BookResponse(
                book.getId(),
                book.getIsbn(),
                book.getTitle(),
                book.getEdition(),
                book.getPublicationYear(),
                book.getLanguage(),
                book.getPublisher() == null ? null : new LibraryDtos.PublisherResponse(
                        book.getPublisher().getId(), book.getPublisher().getName(),
                        book.getPublisher().getAddress(), book.getPublisher().getContactEmail(),
                        book.getPublisher().getContactPhone()),
                book.getCategory() == null ? null : new LibraryDtos.CategoryResponse(
                        book.getCategory().getId(), book.getCategory().getCode(),
                        book.getCategory().getName(), book.getCategory().getDescription()),
                authorNames,
                book.getCallNumber(),
                book.getShelfLocation(),
                book.getCoverUrl(),
                book.getDescription(),
                book.isReference(),
                book.getId() == null ? 0 : books.countCopies(book.getId()),
                book.getId() == null ? 0 : books.countAvailableCopies(book.getId(),
                        BookCopy.Status.AVAILABLE));
    }

    private LibraryDtos.CopyResponse copyResponse(BookCopy copy) {
        return new LibraryDtos.CopyResponse(
                copy.getId(),
                copy.getBook().getId(),
                copy.getBook().getTitle(),
                copy.getBarcode(),
                copy.getAcquisitionType().name(),
                copy.getAcquiredOn(),
                copy.getPrice(),
                copy.getConditionStatus().name(),
                copy.getStatus().name(),
                copy.getNotes());
    }

    private LibraryDtos.MemberResponse memberResponse(LibraryMember member) {
        List<LibraryIssue> open = issues.findByMemberIdAndReturnedAtIsNullOrderByDueAtAsc(
                member.getId());
        BigDecimal owing = fines.findByMemberIdAndStatusOrderByAssessedOnDesc(member.getId(),
                        LibraryFine.Status.OUTSTANDING)
                .stream().map(LibraryFine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new LibraryDtos.MemberResponse(
                member.getId(),
                member.getMemberCode(),
                memberName(member),
                member.getUserId(),
                member.getStudentId(),
                member.getEmployeeId(),
                member.getExternalName(),
                member.getExternalPhone(),
                member.getExternalEmail(),
                member.getMaxBooks(),
                open.size(),
                member.getMembershipStart(),
                member.getMembershipEnd(),
                member.getStatus().name(),
                open.stream().map(this::loanSummary).toList(),
                owing);
    }

    private String memberName(LibraryMember member) {
        String studentName = member.getStudentId() == null ? null
                : students.findById(member.getStudentId()).map(Student::displayName).orElse(null);
        String employeeName = member.getEmployeeId() == null ? null
                : employees.findById(member.getEmployeeId()).map(Employee::fullName).orElse(null);
        return member.displayName(studentName, employeeName, null);
    }

    private LibraryDtos.LoanSummary loanSummary(LibraryIssue issue) {
        LocalDate today = LocalDate.now();
        Book book = issue.getCopy().getBook();
        return new LibraryDtos.LoanSummary(
                issue.getId(),
                issue.getCopy().getId(),
                issue.getCopy().getBarcode(),
                book.getId(),
                book.getTitle(),
                book.getAuthors().isEmpty() ? ""
                        : book.getAuthors().stream().map(Author::getName)
                                .sorted().reduce((a, b) -> a + ", " + b).orElse(""),
                issue.getMember().getMemberCode(),
                memberName(issue.getMember()),
                issue.getIssuedAt(),
                issue.getDueAt(),
                issue.getReturnedAt(),
                issue.getRenewalCount(),
                issue.getStatus().name(),
                issue.isOverdue(today),
                issue.daysOverdue(today),
                issue.daysLoaned(),
                issue.getFine() == null ? null : issue.getFine().getAmount());
    }

    private LibraryDtos.ReservationResponse reservationResponse(LibraryReservation reservation) {
        return new LibraryDtos.ReservationResponse(
                reservation.getId(),
                reservation.getBook().getId(),
                reservation.getBook().getTitle(),
                reservation.getMember().getId(),
                memberName(reservation.getMember()),
                reservation.getReservedAt(),
                reservation.getExpiresAt(),
                reservation.getQueuePosition(),
                reservation.getStatus().name());
    }

    private LibraryDtos.FineResponse fineResponse(LibraryFine fine) {
        return new LibraryDtos.FineResponse(
                fine.getId(),
                fine.getMember().getId(),
                memberName(fine.getMember()),
                fine.getIssue() == null ? null : fine.getIssue().getId(),
                fine.getReason(),
                fine.getAmount(),
                fine.getCurrency(),
                fine.getAssessedOn(),
                fine.getPaidAt(),
                fine.getWaivedAt(),
                fine.getWaiverReason(),
                fine.getStatus().name());
    }
}