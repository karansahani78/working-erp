package com.educationerp.academic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CourseDto(
        UUID id,
        @NotBlank @Size(max=40) String code,
        @NotBlank @Size(max=180) String name,
        @Size(max=1000) String description,
        String courseType,
        UUID departmentId,
        UUID programId,
        Integer creditHours,
        boolean active
) {
    public static CourseDto from(Course c) {
        return new CourseDto(c.getId(),c.getCode(),c.getName(),c.getDescription(),
                c.getCourseType().name(),
                c.getDepartment()==null?null:c.getDepartment().getId(),
                c.getProgram()==null?null:c.getProgram().getId(),
                c.getCreditHours(), c.isActive());
    }
}
