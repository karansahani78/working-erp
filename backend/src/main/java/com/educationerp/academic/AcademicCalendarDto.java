package com.educationerp.academic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record AcademicCalendarDto(UUID id, @NotBlank String title, @NotBlank String eventType,
                                 @NotNull UUID academicYearId, UUID semesterId,
                                 @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                 boolean workingDay, String description) {
    public static AcademicCalendarDto from(AcademicCalendarEvent e) {
        return new AcademicCalendarDto(e.getId(), e.getTitle(), e.getEventType().name(), e.getAcademicYear().getId(),
                e.getSemester()==null?null:e.getSemester().getId(), e.getStartDate(), e.getEndDate(), e.isWorkingDay(), e.getDescription());
    }
}
