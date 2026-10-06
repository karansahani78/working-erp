package com.educationerp.academic;

import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The weekly timetable.
 *
 * <p>Three things cannot be in two places at once, and the blueprint names all three: a
 * teacher, a room and a section or class. Every write is checked against the others in the
 * same day and time slot, so a clash is refused at the point someone makes it rather than
 * discovered on the printed timetable.
 */
@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetableEntryRepository entries;
    private final TimeSlotRepository timeSlots;
    private final CourseOfferingRepository offerings;
    private final SectionRepository sections;
    private final SchoolClassRepository classes;
    private final RoomRepository rooms;
    private final AuthorizationChecker auth;
    private final AuditService audit;

    /**
     * The grid for one scope. Everything is resolved from the offering, so a teacher or a
     * section that changed rooms mid-week is reported as it actually stands.
     */
    @Transactional(readOnly = true)
    public AcademicStructureDto.TimetableGrid grid(UUID sectionId, UUID classId, UUID teacherId,
                                                  DayOfWeek day) {
        auth.requirePermission("ACADEMIC_READ");
        List<AcademicStructureDto.TimetableEntryResponse> found = entries
                .findForView(sectionId, classId, teacherId, day).stream()
                .map(this::toResponse)
                .toList();
        List<TimeSlot> slots = timeSlots.findAll().stream()
                .filter(TimeSlot::isActive)
                .sorted(Comparator.comparing(TimeSlot::getOrdinal,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(slot -> slot.getStartTime()))
                .toList();
        List<AcademicStructureDto.TimetableSlot> grid = new ArrayList<>();
        for (DayOfWeek weekday : DayOfWeek.values()) {
            if (day != null && weekday != day) {
                continue;
            }
            for (TimeSlot slot : slots) {
                List<AcademicStructureDto.TimetableEntryResponse> inSlot = found.stream()
                        .filter(entry -> entry.dayOfWeek() == weekday
                                && entry.timeSlotId().equals(slot.getId()))
                        .toList();
                grid.add(new AcademicStructureDto.TimetableSlot(weekday, slot.getId(), slot.getName(),
                        slot.getStartTime().toString(), slot.getEndTime().toString(), inSlot));
            }
        }
        String scope = sectionId != null ? "SECTION" : classId != null ? "CLASS" : "TEACHER";
        UUID scopeId = sectionId != null ? sectionId : classId != null ? classId : teacherId;
        return new AcademicStructureDto.TimetableGrid(scope, scopeId, grid);
    }

    @Transactional
    public AcademicStructureDto.TimetableEntryResponse create(AcademicStructureDto.CreateTimetableEntry request) {
        auth.requirePermission("ACADEMIC_CREATE");
        TimeSlot slot = timeSlots.findById(request.timeSlotId())
                .orElseThrow(() -> AppException.notFound("Time slot"));
        if (!slot.isActive()) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Time slot " + slot.getName() + " is inactive.");
        }
        CourseOffering offering = offerings.findById(request.courseOfferingId())
                .orElseThrow(() -> AppException.notFound("Course offering"));
        if (!offering.isActive()) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "A closed offering cannot be timetabled.");
        }
        if (request.effectiveFrom() != null && request.effectiveTo() != null
                && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "A timetable entry cannot end before it begins.");
        }
        if (entries.findFirstByOfferingIdAndDayOfWeekAndTimeSlotId(
                offering.getId(), request.dayOfWeek(), slot.getId()).isPresent()) {
            throw AppException.duplicate("That offering is already timetabled on "
                    + request.dayOfWeek() + " in " + slot.getName() + ".");
        }

        Section section = resolveSection(request, offering);
        SchoolClass schoolClass = resolveClass(request, offering);
        Room room = request.roomId() == null ? null
                : rooms.findById(request.roomId()).orElseThrow(() -> AppException.notFound("Room"));
        UUID teacherId = request.teacherId() != null ? request.teacherId() : offering.getTeacherId();

        requireNoClash(request.dayOfWeek(), slot, teacherId, room, section, schoolClass, null);

        TimetableEntry entry = new TimetableEntry();
        entry.setDayOfWeek(request.dayOfWeek());
        entry.setTimeSlot(slot);
        entry.setOffering(offering);
        entry.setSection(section);
        entry.setSchoolClass(schoolClass);
        entry.setRoom(room);
        entry.setTeacherId(teacherId);
        entry.setEffectiveFrom(request.effectiveFrom());
        entry.setEffectiveTo(request.effectiveTo());
        entry.setActive(true);
        TimetableEntry saved = entries.save(entry);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("TimetableEntry")
                .entityId(saved.getId().toString())
                .entityLabel(request.dayOfWeek() + " " + slot.getName())
                .summary("Timetabled " + offering.getCourse().getCode() + " on " + request.dayOfWeek()
                        + " in " + slot.getName())
                .module("ACADEMIC")
                .succeeded(true)
                .build());
        return toResponse(saved);
    }

    /**
     * Replaces a slot in the timetable. The clash check excludes the entry being replaced, so
     * a room or teacher swap is not blocked by the row that is about to move.
     */
    @Transactional
    public AcademicStructureDto.TimetableEntryResponse update(
            UUID id, AcademicStructureDto.CreateTimetableEntry request) {
        auth.requirePermission("ACADEMIC_UPDATE");
        TimetableEntry entry = entries.findById(id).orElseThrow(() -> AppException.notFound("Timetable entry"));
        TimeSlot slot = timeSlots.findById(request.timeSlotId())
                .orElseThrow(() -> AppException.notFound("Time slot"));
        Room room = request.roomId() == null ? null
                : rooms.findById(request.roomId()).orElseThrow(() -> AppException.notFound("Room"));
        Section section = request.sectionId() == null ? entry.getSection()
                : sections.findById(request.sectionId()).orElseThrow(() -> AppException.notFound("Section"));
        SchoolClass schoolClass = request.schoolClassId() == null ? entry.getSchoolClass()
                : classes.findById(request.schoolClassId())
                        .orElseThrow(() -> AppException.notFound("School class"));
        UUID teacherId = request.teacherId() != null ? request.teacherId()
                : entry.getOffering().getTeacherId();
        requireNoClash(request.dayOfWeek(), slot, teacherId, room, section, schoolClass, entry.getId());

        entry.setDayOfWeek(request.dayOfWeek());
        entry.setTimeSlot(slot);
        entry.setRoom(room);
        entry.setTeacherId(teacherId);
        entry.setEffectiveFrom(request.effectiveFrom());
        entry.setEffectiveTo(request.effectiveTo());
        return toResponse(entries.save(entry));
    }

    @Transactional
    public void delete(UUID id) {
        auth.requirePermission("ACADEMIC_DELETE");
        TimetableEntry entry = entries.findById(id).orElseThrow(() -> AppException.notFound("Timetable entry"));
        entries.delete(entry);
        audit.record(AuditEvent.builder()
                .action(AuditAction.DELETE)
                .entityType("TimetableEntry")
                .entityId(id.toString())
                .summary("Removed a timetable slot")
                .module("ACADEMIC")
                .succeeded(true)
                .build());
    }

    private void requireNoClash(DayOfWeek day, TimeSlot slot, UUID teacherId, Room room,
                                Section section, SchoolClass schoolClass, UUID excludeEntryId) {
        if (teacherId != null && clash(entries.findTeacherConflicts(day, slot.getId(), teacherId), excludeEntryId)) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "That teacher is already booked on " + day + " in " + slot.getName() + ".");
        }
        if (room != null && clash(entries.findRoomConflicts(day, slot.getId(), room.getId()), excludeEntryId)) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    room.getName() + " is already booked on " + day + " in " + slot.getName() + ".");
        }
        if ((section != null || schoolClass != null) && clash(entries.findGroupConflicts(day, slot.getId(),
                section == null ? null : section.getId(),
                schoolClass == null ? null : schoolClass.getId()), excludeEntryId)) {
            String who = section != null ? section.getName() : schoolClass.getName();
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    who + " already has a lesson on " + day + " in " + slot.getName() + ".");
        }
    }

    private boolean clash(List<TimetableEntry> candidates, UUID excludeEntryId) {
        return candidates.stream().anyMatch(candidate -> !candidate.getId().equals(excludeEntryId));
    }

    /** The offering's own group wins; a request may only fill in a group it left blank. */
    private Section resolveSection(AcademicStructureDto.CreateTimetableEntry request, CourseOffering offering) {
        if (request.sectionId() == null) {
            return offering.getSection();
        }
        if (offering.getSection() != null
                && !offering.getSection().getId().equals(request.sectionId())) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "That section is not the one this offering is taught to.");
        }
        return sections.findById(request.sectionId()).orElseThrow(() -> AppException.notFound("Section"));
    }

    private SchoolClass resolveClass(AcademicStructureDto.CreateTimetableEntry request, CourseOffering offering) {
        if (request.schoolClassId() == null) {
            return offering.getSchoolClass();
        }
        if (offering.getSchoolClass() != null
                && !offering.getSchoolClass().getId().equals(request.schoolClassId())) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "That class is not the one this offering is taught to.");
        }
        return classes.findById(request.schoolClassId())
                .orElseThrow(() -> AppException.notFound("School class"));
    }

    /**
     * The offering's teacher name, but only when the entry has not overridden the teacher. An
     * overridden teacher id belongs to a person this service cannot name, and printing a raw
     * uuid as a name would be worse than printing nothing.
     */
    private String teacherNameOf(TimetableEntry entry, CourseOffering offering) {
        if (entry.getTeacherId() == null || entry.getTeacherId().equals(offering.getTeacherId())) {
            return offering.getTeacherName();
        }
        return null;
    }

    private AcademicStructureDto.TimetableEntryResponse toResponse(TimetableEntry entry) {
        CourseOffering offering = entry.getOffering();
        TimeSlot slot = entry.getTimeSlot();
        return new AcademicStructureDto.TimetableEntryResponse(entry.getId(), entry.getDayOfWeek(),
                slot.getId(), slot.getName(),
                new AcademicStructureDto.LocalTimeRange(slot.getStartTime().toString(),
                        slot.getEndTime().toString()),
                offering.getId(), offering.getCourse().getCode(), offering.getCourse().getName(),
                OfferingCodes.of(offering),
                entry.getSection() == null ? null : entry.getSection().getId(),
                entry.getSection() == null ? null : entry.getSection().getName(),
                entry.getSchoolClass() == null ? null : entry.getSchoolClass().getId(),
                entry.getSchoolClass() == null ? null : entry.getSchoolClass().getName(),
                entry.getRoom() == null ? null : entry.getRoom().getId(),
                entry.getRoom() == null ? null : entry.getRoom().getName(),
                entry.getTeacherId(), teacherNameOf(entry, offering),
                entry.getEffectiveFrom(), entry.getEffectiveTo(), entry.isActive());
    }

}
