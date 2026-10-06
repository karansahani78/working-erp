package com.educationerp.student.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.api.PageResponse;
import com.educationerp.student.dto.StudentDtos;
import com.educationerp.student.service.AdmissionApplicationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admissions/applications")
@Tag(name = "Admission Applications")
@RequiredArgsConstructor
public class AdmissionApplicationController {

    private final AdmissionApplicationService service;

    @GetMapping
    public ApiResponse<PageResponse<StudentDtos.ApplicationResponse>> list(
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String term,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.search(campaignId, status, term, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<StudentDtos.ApplicationResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudentDtos.ApplicationResponse>> create(
            @Valid @RequestBody StudentDtos.ApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(request), "Application created"));
    }

    /** Applicant-facing edits; only drafts are editable. */
    @PutMapping("/{id}/draft")
    public ApiResponse<StudentDtos.ApplicationResponse> updateDraft(
            @PathVariable UUID id, @Valid @RequestBody StudentDtos.ApplicationRequest request) {
        return ApiResponse.ok(service.updateDraft(id, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<StudentDtos.ApplicationResponse> update(
            @PathVariable UUID id, @RequestBody StudentDtos.ApplicationUpdate request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<StudentDtos.ApplicationResponse> submit(@PathVariable UUID id) {
        return ApiResponse.ok(service.submit(id), "Application submitted");
    }

    @PostMapping("/{id}/review")
    public ApiResponse<StudentDtos.ApplicationResponse> startReview(@PathVariable UUID id) {
        return ApiResponse.ok(service.startReview(id));
    }

    @GetMapping("/{id}/documents")
    public ApiResponse<List<StudentDtos.DocumentResponse>> documents(@PathVariable UUID id) {
        return ApiResponse.ok(service.documents(id));
    }

    @PostMapping("/{id}/documents")
    public ResponseEntity<ApiResponse<StudentDtos.DocumentResponse>> attachDocument(
            @PathVariable UUID id, @Valid @RequestBody StudentDtos.DocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.attachDocument(id, request), "Document attached"));
    }

    /**
     * Verifies attached documents. The response reports any outstanding mandatory
     * documents rather than failing outright, so review staff can see what is missing.
     */
    @PostMapping("/{id}/documents/verify")
    public ApiResponse<Map<String, Object>> verifyDocuments(
            @PathVariable UUID id, @RequestBody List<StudentDtos.DocumentVerification> verifications) {
        var result = service.verifyDocuments(id, verifications);
        return ApiResponse.ok(Map.of(
                "application", result.application(),
                "missingDocuments", result.missingDocuments(),
                "rejected", result.rejected()));
    }

    @PostMapping("/{id}/eligibility")
    public ApiResponse<StudentDtos.ApplicationResponse> decideEligibility(
            @PathVariable UUID id, @Valid @RequestBody StudentDtos.DecisionRequest request) {
        return ApiResponse.ok(service.decideEligibility(id, request));
    }

    @PostMapping("/{id}/scores")
    public ApiResponse<StudentDtos.ApplicationResponse> recordScores(
            @PathVariable UUID id, @RequestBody AdmissionApplicationService.BigDecimalScore scores) {
        return ApiResponse.ok(service.recordScores(id, scores));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<StudentDtos.ApplicationResponse> reject(
            @PathVariable UUID id, @RequestParam(required = false) String notes) {
        return ApiResponse.ok(service.reject(id, notes));
    }

    /** Idempotent: a retry returns the student created by the first approval. */
    @PostMapping("/{id}/approve")
    public ApiResponse<Map<String, Object>> approve(
            @PathVariable UUID id, @RequestParam(required = false) String notes) {
        var result = service.approve(id, notes);
        return ApiResponse.ok(Map.of(
                "application", result.application(),
                "student", result.student(),
                "created", result.created()));
    }

    /** The enrolment step of the admission chain: enrols the student, then closes the application. */
    @PostMapping("/{id}/enrol")
    public ApiResponse<Map<String, Object>> enrol(@PathVariable UUID id,
                                                   @RequestParam(required = false) UUID sectionId,
                                                   @RequestParam(required = false) String rollNumber) {
        var result = service.enrol(id, sectionId, rollNumber);
        return ApiResponse.ok(Map.of(
                "application", result.application(),
                "enrollment", result.enrollment(),
                "created", result.created()));
    }

    @GetMapping("/{id}/decisions")
    public ApiResponse<List<StudentDtos.DecisionResponse>> decisions(@PathVariable UUID id) {
        return ApiResponse.ok(service.decisions(id));
    }

    @GetMapping("/student/{studentId}")
    public ApiResponse<List<StudentDtos.ApplicationResponse>> forStudent(@PathVariable UUID studentId) {
        return ApiResponse.ok(service.forStudent(studentId));
    }
}