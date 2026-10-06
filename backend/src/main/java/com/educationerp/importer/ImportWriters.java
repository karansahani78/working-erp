package com.educationerp.importer;

import com.educationerp.academic.AcademicYear;
import com.educationerp.academic.AcademicYearRepository;
import com.educationerp.academic.CourseDto;
import com.educationerp.academic.CourseRepository;
import com.educationerp.academic.CourseService;
import com.educationerp.academic.Department;
import com.educationerp.academic.DepartmentRepository;
import com.educationerp.common.error.AppException;
import com.educationerp.common.numbering.DocumentSequence;
import com.educationerp.common.numbering.SequenceNumberGenerator;
import com.educationerp.finance.FeeComponent;
import com.educationerp.finance.FeeStructureRepository;
import com.educationerp.finance.dto.FinanceDtos;
import com.educationerp.finance.service.FeeStructureService;
import com.educationerp.hr.Designation;
import com.educationerp.hr.DesignationRepository;
import com.educationerp.hr.EmployeeRepository;
import com.educationerp.hr.EmployeeService;
import com.educationerp.hr.HrDtos;
import com.educationerp.inventory.InventoryDtos;
import com.educationerp.inventory.InventoryService;
import com.educationerp.inventory.ItemCategory;
import com.educationerp.inventory.ItemCategoryRepository;
import com.educationerp.inventory.ItemRepository;
import com.educationerp.library.AuthorRepository;
import com.educationerp.library.BookRepository;
import com.educationerp.library.LibraryCategory;
import com.educationerp.library.LibraryCategoryRepository;
import com.educationerp.library.LibraryDtos;
import com.educationerp.library.LibraryService;
import com.educationerp.library.PublisherRepository;
import com.educationerp.student.Student;
import com.educationerp.student.GuardianRepository;
import com.educationerp.student.StudentRepository;
import com.educationerp.student.dto.StudentDtos;
import com.educationerp.student.service.GuardianService;
import com.educationerp.student.service.StudentCreationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Turns validated rows into records.
 *
 * <p>Every record type is written through the service that normally owns it, rather than by
 * inserting rows here. That is deliberate: an imported student gets the same number, the same
 * defaults and the same checks as one entered by hand, and there is no second copy of those rules
 * to drift out of step.
 *
 * <p>Duplicate detection sits here too, beside the lookups that make it possible, because a
 * duplicate is found by asking the question the write would ask — does this email or code already
 * exist. Keeping both together is what stops the preview and the write disagreeing.
 *
 * <p>Nothing is held between calls. Two imports running at once must not see each other's rows.
 */
@Component
@RequiredArgsConstructor
public class ImportWriters {

    private final StudentCreationService students;
    private final GuardianService guardians;
    private final EmployeeService employees;
    private final CourseService courses;
    private final FeeStructureService fees;
    private final InventoryService inventory;
    private final LibraryService library;

    private final StudentRepository studentRepository;
    private final GuardianRepository guardianRepository;
    private final EmployeeRepository employeeRepository;
    private final ItemRepository itemRepository;
    private final ItemCategoryRepository itemCategories;
    private final BookRepository books;
    private final AuthorRepository authors;
    private final PublisherRepository publishers;
    private final LibraryCategoryRepository libraryCategories;
    private final CourseRepository courseRepository;
    private final FeeStructureRepository feeStructures;
    private final DepartmentRepository departments;
    private final DesignationRepository designations;
    private final AcademicYearRepository academicYears;

    private final SequenceNumberGenerator numbers;

    /** What one import did, and what it left behind. */
    public record Outcome(int imported, List<String> notes) {

        public ImportDtos.ImportReport report(int total, int skipped) {
            return new ImportDtos.ImportReport(total, imported, skipped, notes);
        }
    }

    /** A clash, named by the thing that clashes. */
    public record Match(int row, String field, String existing, String incoming) {
    }

    // ------------------------------------------------------------------ writing

    /**
     * Write the rows that are fit to write.
     *
     * <p>A row that turns out to clash with the database is recorded and the import carries on
     * rather than abandoning the other three hundred rows. The caller is told what was skipped,
     * so nothing fails silently.
     */
    public Outcome write(ImportBatch.ImportType type, List<ImportValidator.ValidatedRow> rows) {
        if (type == ImportBatch.ImportType.FEES) {
            return writeFeeStructures(rows);
        }
        List<String> notes = new ArrayList<>();
        int written = 0;
        for (ImportValidator.ValidatedRow row : rows) {
            try {
                writeOne(type, row.values());
                written++;
            } catch (AppException e) {
                notes.add("Row " + row.rowNumber() + ": " + e.getMessage());
            }
        }
        return new Outcome(written, notes);
    }

    private void writeOne(ImportBatch.ImportType type, Map<String, String> v) {
        switch (type) {
            case STUDENTS -> students.create(new StudentDtos.StudentRequest(
                    text(v, "firstName"), text(v, "middleName"), text(v, "lastName"),
                    date(v, "dateOfBirth"), text(v, "gender"), text(v, "nationality"),
                    text(v, "phone"), text(v, "email"), text(v, "address"), null, null));
            case GUARDIANS -> guardians.create(new StudentDtos.GuardianRequest(
                    text(v, "firstName"), text(v, "middleName"), text(v, "lastName"),
                    text(v, "phone"), text(v, "email"), text(v, "occupation"), text(v, "address")));
            case EMPLOYEES -> employees.create(new HrDtos.CreateEmployee(
                    // The staff screen requires a code and imports have no business inventing one,
                    // so it is issued here from the same counter the rest of the system uses.
                    numbers.next(DocumentSequence.Kind.EMPLOYEE),
                    text(v, "firstName"), text(v, "middleName"), text(v, "lastName"),
                    date(v, "dateOfBirth"), text(v, "gender"), text(v, "nationality"),
                    text(v, "phone"), text(v, "email"), null, null, null,
                    date(v, "joinDate"), departmentId(v), designationId(v), null, null,
                    null, null, null));
            case COURSES -> courses.create(new CourseDto(null, text(v, "code"), text(v, "name"),
                    text(v, "description"), defaultText(v, "courseType", "SUBJECT"),
                    departmentId(v), null, integer(v, "creditHours"), true));
            case INVENTORY -> inventory.createItem(new InventoryDtos.ItemRequest(
                    text(v, "code"), text(v, "name"), text(v, "description"), itemCategoryId(v),
                    defaultText(v, "unit", "EACH"), decimal(v, "reorderLevel", "0"),
                    decimal(v, "reorderQuantity", "0"), false, false, true));
            case BOOKS -> writeBook(v);
            case FEES -> throw new IllegalStateException(
                    "Fee structures are written as a group, not a row at a time.");
        }
    }

    /**
     * A fee spreadsheet is one component per row, repeated for the structure it belongs to.
     *
     * <p>Each structure is priced as the sum of its own rows rather than as whatever the first row
     * claimed, and its components are attached once every row for it has been read. Creating a
     * structure per row would produce four duplicate structures where the file meant one.
     */
    private Outcome writeFeeStructures(List<ImportValidator.ValidatedRow> rows) {
        List<String> notes = new ArrayList<>();
        Map<String, List<ImportValidator.ValidatedRow>> groups = new LinkedHashMap<>();
        for (ImportValidator.ValidatedRow row : rows) {
            groups.computeIfAbsent(text(row.values(), "structureCode").toUpperCase(Locale.ROOT),
                    ignored -> new ArrayList<>()).add(row);
        }

        int written = 0;
        for (Map.Entry<String, List<ImportValidator.ValidatedRow>> group : groups.entrySet()) {
            List<ImportValidator.ValidatedRow> components = group.getValue();
            Map<String, String> first = components.get(0).values();
            try {
                BigDecimal total = components.stream()
                        .map(row -> decimal(row.values(), "amount", "0"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                String currency = components.stream()
                        .map(row -> text(row.values(), "currency"))
                        .filter(value -> !value.isBlank()).findFirst().orElse("NPR");

                UUID structureId = fees.create(new FinanceDtos.CreateFeeStructure(
                        text(first, "structureName"), group.getKey(), null, null,
                        currentAcademicYear(), total, currency, null)).id();

                fees.setComponents(structureId, new FinanceDtos.ComponentList(
                        components.stream()
                                .map(row -> new FinanceDtos.FeeComponentRequest(
                                        FeeComponent.ComponentType.OTHER,
                                        text(row.values(), "componentName"),
                                        decimal(row.values(), "amount", "0"), Boolean.TRUE, null))
                                .toList()));
                written += components.size();
            } catch (AppException e) {
                components.forEach(row ->
                        notes.add("Row " + row.rowNumber() + ": " + e.getMessage()));
            }
        }
        return new Outcome(written, notes);
    }

    private void writeBook(Map<String, String> v) {
        List<UUID> authorIds = new ArrayList<>();
        String author = text(v, "author");
        if (!author.isBlank()) {
            // A catalogue is worth more with its authors on it, and a spreadsheet naming an author
            // the library has never heard of is asking for that author to be added.
            authorIds.add(authors.findByNameIgnoreCase(author.trim())
                    .map(com.educationerp.library.Author::getId)
                    .orElseGet(() -> library.createAuthor(
                            new LibraryDtos.AuthorRequest(author.trim(), null)).id()));
        }
        library.createBook(new LibraryDtos.BookRequest(
                text(v, "isbn"), text(v, "title"), null, integer(v, "publicationYear"),
                defaultText(v, "language", "EN"), publisherId(v), libraryCategoryId(v),
                authorIds.isEmpty() ? null : authorIds, text(v, "callNumber"), null, null, null,
                false));
    }

    // ------------------------------------------------------------------ duplicates

    /**
     * Find the rows that match something already in the system.
     *
     * <p>Only rows that are otherwise clean are considered. A row with a broken date is already
     * reported as broken, and also being told it is a duplicate tells nobody anything they can act
     * on.
     */
    public List<Match> duplicates(ImportBatch.ImportType type,
                                  List<ImportValidator.ValidatedRow> rows) {
        List<Match> found = new ArrayList<>();
        for (ImportValidator.ValidatedRow row : rows) {
            if (!row.ok()) {
                continue;
            }
            Map<String, String> v = row.values();
            switch (type) {
                case STUDENTS -> {
                    // Two students may legitimately share a phone, but never an email, so a
                    // blank one says nothing and must not be looked up: several blank rows
                    // would otherwise match each other.
                    String email = text(v, "email");
                    if (!email.isBlank()) {
                        studentRepository.findFirstByEmailIgnoreCase(email).ifPresent(existing ->
                                found.add(new Match(row.rowNumber(), "email", existing.getEmail(),
                                        email)));
                    }
                }
                case GUARDIANS -> {
                    // A guardian can be on more than one child's record, so a name is not a
                    // duplicate; the email is the only thing that can say so.
                    String email = text(v, "email");
                    if (!email.isBlank() && guardianRepository.existsByEmailIgnoreCase(email)) {
                        found.add(new Match(row.rowNumber(), "email", email, email));
                    }
                }
                case EMPLOYEES -> {
                    // Employee email is not unique, so take the first rather than insisting
                    // on exactly one row.
                    String email = text(v, "email");
                    if (!email.isBlank()) {
                        employeeRepository.findFirstByEmailIgnoreCase(email).ifPresent(existing ->
                                found.add(new Match(row.rowNumber(), "email", existing.getEmail(),
                                        email)));
                    }
                }
                case COURSES -> courseRepository.findByCodeIgnoreCase(text(v, "code"))
                        .ifPresent(existing -> found.add(new Match(row.rowNumber(), "code",
                                existing.getCode(), text(v, "code"))));
                case INVENTORY -> itemRepository.findByCodeIgnoreCase(text(v, "code"))
                        .ifPresent(existing -> found.add(new Match(row.rowNumber(), "code",
                                existing.getCode(), text(v, "code"))));
                case BOOKS -> {
                    String isbn = text(v, "isbn");
                    if (!isbn.isBlank()) {
                        books.findByIsbn(isbn).ifPresent(existing -> found.add(
                                new Match(row.rowNumber(), "isbn", existing.getIsbn(), isbn)));
                    } else {
                        // Two editions of one title are ordinary in a library, so a title is
                        // a hint of a duplicate rather than proof, and only the first match
                        // is reported.
                        books.findFirstByTitleIgnoreCase(text(v, "title")).ifPresent(existing ->
                                found.add(new Match(row.rowNumber(), "title", existing.getTitle(),
                                        text(v, "title"))));
                    }
                }
                case FEES -> feeStructures.findByCodeIgnoreCase(text(v, "structureCode"))
                        .ifPresent(existing -> found.add(new Match(row.rowNumber(), "structureCode",
                                existing.getCode(), text(v, "structureCode"))));
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ lookups

    private UUID departmentId(Map<String, String> v) {
        String code = text(v, "departmentCode");
        if (code.isBlank()) {
            return null;
        }
        return departments.findByCodeIgnoreCase(code)
                .or(() -> departments.findByNameIgnoreCase(code))
                .map(Department::getId)
                .orElseThrow(() -> AppException.rule("There is no department called \"" + code
                        + "\". Create it first, or leave the column out."));
    }

    private UUID designationId(Map<String, String> v) {
        String code = text(v, "designationCode");
        if (code.isBlank()) {
            return null;
        }
        return designations.findByCodeIgnoreCase(code)
                .or(() -> designations.findByNameIgnoreCase(code))
                .map(Designation::getId)
                .orElseThrow(() -> AppException.rule("There is no designation called \"" + code
                        + "\". Create it first, or leave the column out."));
    }

    private UUID itemCategoryId(Map<String, String> v) {
        String code = text(v, "categoryCode");
        if (code.isBlank()) {
            return null;
        }
        return itemCategories.findByCodeIgnoreCase(code)
                .or(() -> itemCategories.findByNameIgnoreCase(code))
                .map(ItemCategory::getId)
                .orElseThrow(() -> AppException.rule("There is no inventory category called \""
                        + code + "\". Create it first, or leave the column out."));
    }

    private UUID publisherId(Map<String, String> v) {
        String name = text(v, "publisher");
        if (name.isBlank()) {
            return null;
        }
        return publishers.findByNameIgnoreCase(name)
                .map(com.educationerp.library.Publisher::getId)
                .orElseGet(() -> library.createPublisher(
                        new LibraryDtos.PublisherRequest(name, null, null, null)).id());
    }

    private UUID libraryCategoryId(Map<String, String> v) {
        String name = text(v, "category");
        if (name.isBlank()) {
            return null;
        }
        return libraryCategories.findByNameIgnoreCase(name)
                .or(() -> libraryCategories.findByCodeIgnoreCase(name))
                .map(LibraryCategory::getId)
                .orElseGet(() -> library.createCategory(new LibraryDtos.CategoryRequest(
                        code(name), name, null)).id());
    }

    /** A library category needs a code as well as a name, so one is derived from the name. */
    private String code(String name) {
        String cleaned = name.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        String candidate = cleaned.isBlank() ? "CATEGORY" : cleaned;
        return candidate.length() > 40 ? candidate.substring(0, 40) : candidate;
    }

    private UUID currentAcademicYear() {
        return academicYears.findFirstByCurrentTrue()
                .or(() -> academicYears.findFirstByStatusOrderByStartDateDesc(
                        AcademicYear.Status.ACTIVE))
                .map(AcademicYear::getId)
                .orElseThrow(() -> AppException.rule(
                        "There is no current academic year, so fees cannot be imported."));
    }

    // ------------------------------------------------------------------ cell access

    private String text(Map<String, String> v, String field) {
        String value = v.get(field);
        return value == null ? "" : value;
    }

    private String defaultText(Map<String, String> v, String field, String fallback) {
        String value = text(v, field);
        return value.isBlank() ? fallback : value;
    }

    private LocalDate date(Map<String, String> v, String field) {
        String value = text(v, field);
        return value.isBlank() ? null : LocalDate.parse(value);
    }

    private BigDecimal decimal(Map<String, String> v, String field, String fallback) {
        String value = text(v, field);
        return value.isBlank() ? new BigDecimal(fallback) : new BigDecimal(value);
    }

    private Integer integer(Map<String, String> v, String field) {
        String value = text(v, field);
        return value.isBlank() ? null : Integer.valueOf(value);
    }
}