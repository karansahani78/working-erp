package com.educationerp.academic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record FacultyDto(
        UUID id,
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        UUID campusId,
        boolean active
) {
    public static FacultyDto from(Faculty f) {
        return new FacultyDto(f.getId(), f.getCode(), f.getName(), f.getDescription(),
                f.getCampus()==null?null:f.getCampus().getId(), f.isActive());
    }
}
