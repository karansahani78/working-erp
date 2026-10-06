package com.educationerp.portal;

import com.educationerp.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
 * A parent's view of their children.
 *
 * <p>Every method here names a child, and every method checks that child against the caller's
 * own guardian links before reading anything. There is no path from this controller to a
 * student the caller is not a guardian of.
 */
@RestController
@RequestMapping("/api/v1/portal/parent")
@Tag(name = "Parent portal")
@RequiredArgsConstructor
public class ParentPortalController {

    private final PortalService service;

    @GetMapping("/dashboard")
    public ApiResponse<PortalDtos.ParentDashboard> dashboard() {
        return ApiResponse.ok(service.parentDashboard());
    }

    /** One child at a time, for the screens below the summary list. */
    @GetMapping("/children/{studentId}")
    public ApiResponse<PortalDtos.ChildSummary> child(@PathVariable UUID studentId) {
        return ApiResponse.ok(service.child(studentId));
    }

    @GetMapping("/children/{studentId}/timetable")
    public ApiResponse<List<PortalDtos.TimetableRow>> timetable(@PathVariable UUID studentId) {
        return ApiResponse.ok(service.childTimetable(studentId));
    }

    @GetMapping("/children/{studentId}/attendance")
    public ApiResponse<PortalDtos.AttendanceSummary> attendance(
            @PathVariable UUID studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.childAttendance(studentId, from, to));
    }

    @GetMapping("/children/{studentId}/fees")
    public ApiResponse<PortalDtos.FeeSummary> fees(@PathVariable UUID studentId) {
        return ApiResponse.ok(service.childFees(studentId));
    }

    @GetMapping("/children/{studentId}/results")
    public ApiResponse<List<PortalDtos.PublishedResult>> results(@PathVariable UUID studentId) {
        return ApiResponse.ok(service.childResults(studentId));
    }
}
