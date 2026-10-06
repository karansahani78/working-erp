package com.educationerp.academic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DepartmentDto(UUID id, @NotBlank @Size(max=40) String code, @NotBlank @Size(max=150) String name,
                            @Size(max=500) String description, UUID facultyId, boolean active) {
    public static DepartmentDto from(Department d) {
        return new DepartmentDto(d.getId(), d.getCode(), d.getName(), d.getDescription(),
                d.getFaculty()==null?null:d.getFaculty().getId(), d.isActive());
    }
}
