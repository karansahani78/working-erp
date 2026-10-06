package com.educationerp.academic;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Sections of a class. A section is the group a timetable and an enrolment are booked against. */
@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sections;
    private final SchoolClassRepository classes;
    private final CourseOfferingRepository offerings;
    private final AuthorizationChecker auth;

    @Transactional(readOnly = true)
    public List<AcademicStructureDto.SectionResponse> list(UUID schoolClassId) {
        auth.requirePermission("ACADEMIC_READ");
        List<Section> found = schoolClassId == null
                ? sections.findAll()
                : sections.findBySchoolClassIdOrderByCodeAsc(schoolClassId);
        return found.stream().map(this::toResponse).toList();
    }

    @Transactional
    public AcademicStructureDto.SectionResponse create(AcademicStructureDto.CreateSection request) {
        auth.requirePermission("ACADEMIC_CREATE");
        SchoolClass schoolClass = classes.findById(request.schoolClassId())
                .orElseThrow(() -> AppException.notFound("School class"));
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (sections.findBySchoolClassIdAndCodeIgnoreCase(schoolClass.getId(), code).isPresent()) {
            throw AppException.duplicate(schoolClass.getName() + " already has a section " + code + ".");
        }
        Section section = new Section();
        section.setSchoolClass(schoolClass);
        section.setName(request.name().trim());
        section.setCode(code);
        section.setCapacity(request.capacity());
        section.setRoom(request.room() == null || request.room().isBlank() ? null : request.room().trim());
        section.setActive(true);
        return toResponse(sections.save(section));
    }

    @Transactional
    public AcademicStructureDto.SectionResponse update(UUID id, AcademicStructureDto.CreateSection request) {
        auth.requirePermission("ACADEMIC_UPDATE");
        Section section = sections.findById(id).orElseThrow(() -> AppException.notFound("Section"));
        if (!section.getSchoolClass().getId().equals(request.schoolClassId())) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "A section cannot be moved to another class.");
        }
        section.setName(request.name().trim());
        section.setCapacity(request.capacity());
        section.setRoom(request.room() == null || request.room().isBlank() ? null : request.room().trim());
        // The code is not changed: it is the section's identity in timetables and reports.
        return toResponse(sections.save(section));
    }

    /**
     * A section is deactivated rather than deleted, because enrolments, timetables and
     * results are all booked against it.
     */
    @Transactional
    public AcademicStructureDto.SectionResponse deactivate(UUID id) {
        auth.requirePermission("ACADEMIC_UPDATE");
        Section section = sections.findById(id).orElseThrow(() -> AppException.notFound("Section"));
        long scheduled = offerings
                .findByAcademicYearIdAndSchoolClassIdAndSectionIdAndActiveTrue(
                        section.getSchoolClass().getAcademicYear().getId(),
                        section.getSchoolClass().getId(), section.getId()).size();
        if (scheduled > 0) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This section still has " + scheduled + " course offering(s) scheduled against it.");
        }
        section.setActive(false);
        return toResponse(sections.save(section));
    }

    private AcademicStructureDto.SectionResponse toResponse(Section section) {
        return new AcademicStructureDto.SectionResponse(section.getId(),
                section.getSchoolClass().getId(), section.getSchoolClass().getName(),
                section.getName(), section.getCode(), section.getCapacity(),
                section.getRoom(), section.isActive());
    }
}
