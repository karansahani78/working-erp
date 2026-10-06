package com.educationerp.academic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SectionDto(UUID id, UUID classId, @NotBlank @Size(max=60) String name,
                         @NotBlank @Size(max=20) String code, Integer capacity, boolean active) {
    public static SectionDto from(Section s) {
        return new SectionDto(s.getId(), s.getSchoolClass().getId(), s.getName(), s.getCode(), s.getCapacity(), s.isActive());
    }
}
