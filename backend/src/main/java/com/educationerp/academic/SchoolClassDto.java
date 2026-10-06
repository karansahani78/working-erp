package com.educationerp.academic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SchoolClassDto(UUID id, UUID academicYearId, @NotBlank @Size(max=60) String name,
                             @NotBlank @Size(max=20) String code, Integer ordinal, boolean active) {
    public static SchoolClassDto from(SchoolClass c) {
        return new SchoolClassDto(c.getId(), c.getAcademicYear().getId(), c.getName(), c.getCode(), c.getOrdinal(), c.isActive());
    }
}
