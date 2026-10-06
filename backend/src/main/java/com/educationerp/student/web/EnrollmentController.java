package com.educationerp.student.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.student.dto.StudentDtos;
import com.educationerp.student.service.EnrollmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/enrollments")
@Tag(name = "Enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService service;

    @GetMapping
    public ApiResponse<List<StudentDtos.EnrollmentResponse>> list(@RequestParam UUID studentId) {
        return ApiResponse.ok(service.forStudent(studentId));
    }

    @GetMapping("/{id}")
    public ApiResponse<StudentDtos.EnrollmentResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudentDtos.EnrollmentResponse>> enroll(
            @Valid @RequestBody StudentDtos.EnrollmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.enroll(request), "Student enrolled"));
    }

    @PutMapping("/{id}")
    public ApiResponse<StudentDtos.EnrollmentResponse> update(
            @PathVariable UUID id, @Valid @RequestBody StudentDtos.EnrollmentRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<StudentDtos.EnrollmentResponse> changeStatus(
            @PathVariable UUID id, @RequestParam String status) {
        return ApiResponse.ok(service.changeStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}