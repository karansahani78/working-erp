package com.educationerp.hr.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.hr.EmployeeAttendanceService;
import com.educationerp.hr.HrDtos;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hr/attendance")
@Tag(name = "HR - Employee Attendance")
@RequiredArgsConstructor
public class EmployeeAttendanceController {

    private final EmployeeAttendanceService service;

    @GetMapping("/employees/{employeeId}")
    public ApiResponse<List<HrDtos.AttendanceResponse>> list(
            @PathVariable UUID employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.list(employeeId, from, to));
    }

    @PostMapping("/employees/{employeeId}")
    public ApiResponse<HrDtos.AttendanceResponse> mark(@PathVariable UUID employeeId,
                                                       @Valid @RequestBody HrDtos.MarkAttendance request) {
        return ApiResponse.ok(service.mark(employeeId, request));
    }

    @PostMapping("/bulk")
    public ApiResponse<List<HrDtos.AttendanceResponse>> markBulk(
            @Valid @RequestBody HrDtos.BulkAttendance request) {
        return ApiResponse.ok(service.markBulk(request));
    }
}