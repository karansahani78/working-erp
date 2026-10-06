package com.educationerp.academic;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.util.UUID;

public record TimetableEntryDto(UUID id, @NotNull DayOfWeek dayOfWeek, @NotNull UUID timeSlotId,
                                @NotNull UUID courseOfferingId, UUID sectionId, UUID schoolClassId,
                                UUID roomId, UUID teacherId, boolean active) {
    public static TimetableEntryDto from(TimetableEntry t) {
        return new TimetableEntryDto(t.getId(), t.getDayOfWeek(), t.getTimeSlot().getId(), t.getOffering().getId(),
                t.getSection()==null?null:t.getSection().getId(), t.getSchoolClass()==null?null:t.getSchoolClass().getId(),
                t.getRoom()==null?null:t.getRoom().getId(), t.getTeacherId(), t.isActive());
    }
}
