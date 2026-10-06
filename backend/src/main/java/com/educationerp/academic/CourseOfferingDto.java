package com.educationerp.academic;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CourseOfferingDto(UUID id, @NotNull UUID courseId, @NotNull UUID academicYearId,
                                UUID semesterId, UUID programId, UUID curriculumId,
                                UUID schoolClassId, UUID sectionId, UUID teacherId,
                                UUID roomId, boolean active) {
    public static CourseOfferingDto from(CourseOffering o) {
        return new CourseOfferingDto(o.getId(), o.getCourse().getId(), o.getAcademicYear().getId(),
                o.getSemester()==null?null:o.getSemester().getId(), o.getProgram()==null?null:o.getProgram().getId(),
                o.getCurriculum()==null?null:o.getCurriculum().getId(), o.getSchoolClass()==null?null:o.getSchoolClass().getId(),
                o.getSection()==null?null:o.getSection().getId(), o.getTeacherId(),
                o.getRoom()==null?null:o.getRoom().getId(), o.isActive());
    }
}
