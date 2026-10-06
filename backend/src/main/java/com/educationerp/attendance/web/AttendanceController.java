package com.educationerp.attendance.web;

import com.educationerp.attendance.dto.AttendanceDtos;
import com.educationerp.attendance.service.AttendanceService;
import com.educationerp.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance")
@Tag(name = "Attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService service;

    /** Bulk register: creates or updates every entry for the date and period. */
    @PostMapping("/registers")
    public ApiResponse<AttendanceDtos.RegisterResponse> recordRegister(
            @Valid @RequestBody AttendanceDtos.RegisterRequest request) {
        return ApiResponse.ok(service.recordRegister(request));
    }

    @PostMapping("/registers/{courseOfferingId}/{attendanceDate}/submit")
    public ApiResponse<AttendanceDtos.RegisterResponse> submit(
            @PathVariable UUID courseOfferingId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate attendanceDate,
            @RequestParam(required = false) UUID timeSlotId,
            @Valid @RequestBody(required = false) AttendanceDtos.SubmitRequest request) {
        return ApiResponse.ok(service.submit(request, courseOfferingId, attendanceDate, timeSlotId));
    }

    @PostMapping("/registers/{courseOfferingId}/{attendanceDate}/approve")
    public ApiResponse<AttendanceDtos.RegisterResponse> approve(
            @PathVariable UUID courseOfferingId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate attendanceDate,
            @RequestParam(required = false) UUID timeSlotId,
            @Valid @RequestBody(required = false) AttendanceDtos.ApproveRequest request) {
        return ApiResponse.ok(service.approve(request, courseOfferingId, attendanceDate, timeSlotId));
    }

    @GetMapping("/registers/{courseOfferingId}/{attendanceDate}")
    public ApiResponse<AttendanceDtos.RegisterResponse> register(
            @PathVariable UUID courseOfferingId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate attendanceDate,
            @RequestParam(required = false) UUID timeSlotId) {
        return ApiResponse.ok(service.register(courseOfferingId, attendanceDate, timeSlotId));
    }

    @GetMapping("/course-offering/{courseOfferingId}")
    public ApiResponse<List<AttendanceDtos.Response>> forOffering(
            @PathVariable UUID courseOfferingId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.forOffering(courseOfferingId, from, to));
    }

    @GetMapping("/students/{studentId}")
    public ApiResponse<List<AttendanceDtos.Response>> forStudent(
            @PathVariable UUID studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.forStudent(studentId, from, to));
    }

    @GetMapping("/students/{studentId}/summary")
    public ApiResponse<AttendanceDtos.SummaryResponse> summary(
            @PathVariable UUID studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.summary(studentId, from, to));
    }

    /** Corrections are always requested with a reason; they are never applied inline. */
    @PostMapping("/records/{attendanceId}/corrections")
    public ApiResponse<AttendanceDtos.CorrectionResponse> requestCorrection(
            @PathVariable UUID attendanceId,
            @Valid @RequestBody AttendanceDtos.CorrectionRequest request) {
        return ApiResponse.ok(service.requestCorrection(attendanceId, request));
    }

    @GetMapping("/records/{attendanceId}/corrections")
    public ApiResponse<List<AttendanceDtos.CorrectionResponse>> correctionsFor(@PathVariable UUID attendanceId) {
        return ApiResponse.ok(service.correctionsFor(attendanceId));
    }

    @PutMapping("/corrections/{correctionId}/decision")
    public ApiResponse<AttendanceDtos.CorrectionResponse> decideCorrection(
            @PathVariable UUID correctionId,
            @Valid @RequestBody AttendanceDtos.CorrectionDecision decision) {
        return ApiResponse.ok(service.decideCorrection(correctionId, decision));
    }
}