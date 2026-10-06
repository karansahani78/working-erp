package com.educationerp.academic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record AcademicYearDto(
        UUID id,
        @NotBlank @Size(max=100) String name,
        @NotBlank @Size(max=40) String code,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotBlank String calendar,
        String status,
        boolean current
) {
    public static AcademicYearDto from(AcademicYear y) {
        return new AcademicYearDto(y.getId(),y.getName(),y.getCode(),y.getStartDate(),y.getEndDate(),
                y.getCalendar().name(), y.getStatus().name(), y.isCurrent());
    }
}
