package com.educationerp.portal;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.api.PageResponse;
import com.educationerp.student.dto.StudentDtos;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A teacher's view of their own work.
 *
 * <p>Nothing here is permission-checked because nothing here should be: a teacher sees the
 * classes they are assigned to and nothing else, which is narrower than any permission set
 * would be. Anyone else who wants to look at those records uses the module endpoints.
 */
@RestController
@RequestMapping("/api/v1/portal/teacher")
@Tag(name = "Teacher portal")
@RequiredArgsConstructor
public class TeacherPortalController {

    private final PortalService service;

    @GetMapping("/dashboard")
    public ApiResponse<PortalDtos.TeacherDashboard> dashboard() {
        return ApiResponse.ok(service.teacherDashboard());
    }

    @GetMapping("/timetable")
    public ApiResponse<List<PortalDtos.TimetableRow>> timetable(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        return ApiResponse.ok(service.teacherTimetable(on == null ? LocalDate.now() : on));
    }

    @GetMapping("/classes")
    public ApiResponse<List<PortalDtos.AssignedClass>> classes() {
        return ApiResponse.ok(service.teacherClasses());
    }

    /** The students of one of the teacher's own classes. */
    @GetMapping("/classes/{courseOfferingId}/students")
    public ApiResponse<PageResponse<StudentDtos.StudentResponse>> students(
            @PathVariable UUID courseOfferingId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ApiResponse.ok(service.teacherStudents(courseOfferingId, pageable));
    }
}
