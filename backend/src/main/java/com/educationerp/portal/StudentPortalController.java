package com.educationerp.portal;

import com.educationerp.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * A student's own view of their record.
 *
 * <p>No student id appears in any path here. The only person who can read this controller is
 * whoever the token belongs to, and the record read is that person's own, which is what makes
 * it impossible to ask for somebody else's.
 */
@RestController
@RequestMapping("/api/v1/portal/student")
@Tag(name = "Student portal")
@RequiredArgsConstructor
public class StudentPortalController {

    private final PortalService service;

    @GetMapping("/dashboard")
    public ApiResponse<PortalDtos.StudentDashboard> dashboard() {
        return ApiResponse.ok(service.studentDashboard());
    }

    @GetMapping("/profile")
    public ApiResponse<PortalDtos.StudentProfile> profile() {
        return ApiResponse.ok(service.studentProfile());
    }

    @GetMapping("/courses")
    public ApiResponse<List<PortalDtos.CourseRow>> courses() {
        return ApiResponse.ok(service.studentCourses());
    }

    @GetMapping("/timetable")
    public ApiResponse<List<PortalDtos.TimetableRow>> timetable() {
        return ApiResponse.ok(service.studentTimetable());
    }

    /** Defaults to the whole record, which is what the portal's own summary shows. */
    @GetMapping("/attendance")
    public ApiResponse<PortalDtos.AttendanceSummary> attendance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.studentAttendance(from, to));
    }

    @GetMapping("/fees")
    public ApiResponse<PortalDtos.FeeSummary> fees() {
        return ApiResponse.ok(service.studentFees());
    }

    @GetMapping("/results")
    public ApiResponse<List<PortalDtos.PublishedResult>> results() {
        return ApiResponse.ok(service.studentResults());
    }
}
