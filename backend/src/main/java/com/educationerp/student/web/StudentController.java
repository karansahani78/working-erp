package com.educationerp.student.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.api.PageResponse;
import com.educationerp.student.dto.StudentDtos;
import com.educationerp.student.service.Student360Service;
import com.educationerp.student.service.StudentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Student endpoints matching the blueprint's API standards:
 * {@code GET/POST /api/v1/students}, {@code GET/PUT/DELETE /api/v1/students/{id}}.
 */
@RestController
@RequestMapping("/api/v1/students")
@Tag(name = "Students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService service;
    private final Student360Service student360Service;

    @GetMapping
    public ApiResponse<PageResponse<StudentDtos.StudentResponse>> list(
            @RequestParam(required = false) String term,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.search(term, status, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<StudentDtos.StudentResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/number/{studentNumber}")
    public ApiResponse<StudentDtos.StudentResponse> getByNumber(@PathVariable String studentNumber) {
        return ApiResponse.ok(service.getByNumber(studentNumber));
    }

    /** Student 360: aggregated profile view. */
    @GetMapping("/{id}/360")
    public ApiResponse<StudentDtos.Student360> profile(@PathVariable UUID id) {
        return ApiResponse.ok(student360Service.get(id));
    }

    @GetMapping("/number/{studentNumber}/360")
    public ApiResponse<StudentDtos.Student360> profileByNumber(@PathVariable String studentNumber) {
        return ApiResponse.ok(student360Service.getByNumber(studentNumber));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudentDtos.StudentResponse>> create(
            @Valid @RequestBody StudentDtos.StudentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(request), "Student created"));
    }

    @PutMapping("/{id}")
    public ApiResponse<StudentDtos.StudentResponse> update(
            @PathVariable UUID id, @Valid @RequestBody StudentDtos.StudentRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<StudentDtos.StudentResponse> changeStatus(
            @PathVariable UUID id, @RequestParam String status) {
        return ApiResponse.ok(service.changeStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/number-format")
    public ApiResponse<StudentService.StudentNumberFormat> numberFormat() {
        return ApiResponse.ok(service.numberFormat());
    }

    @PutMapping("/number-format")
    public ApiResponse<StudentService.StudentNumberFormat> updateNumberFormat(@RequestParam String format) {
        return ApiResponse.ok(service.updateNumberFormat(format));
    }
}