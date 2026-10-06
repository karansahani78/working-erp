package com.educationerp.student.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.api.PageResponse;
import com.educationerp.student.dto.StudentDtos;
import com.educationerp.student.service.GuardianService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/guardians")
@Tag(name = "Guardians")
@RequiredArgsConstructor
public class GuardianController {

    private final GuardianService service;

    @GetMapping
    public ApiResponse<PageResponse<StudentDtos.GuardianResponse>> list(
            @RequestParam(required = false) String term,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.search(term, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<StudentDtos.GuardianResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudentDtos.GuardianResponse>> create(
            @Valid @RequestBody StudentDtos.GuardianRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(request), "Guardian created"));
    }

    @PutMapping("/{id}")
    public ApiResponse<StudentDtos.GuardianResponse> update(
            @PathVariable UUID id, @Valid @RequestBody StudentDtos.GuardianRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    /**
     * Attaches the guardian to a sign-in account so the parent portal can be used. The
     * account is named by email; it must already exist.
     */
    @PutMapping("/{id}/account")
    public ApiResponse<StudentDtos.GuardianResponse> linkAccount(
            @PathVariable UUID id,
            @Valid @RequestBody StudentDtos.GuardianAccountLinkRequest request) {
        return ApiResponse.ok(service.linkAccount(id, request), "Guardian account linked");
    }

    @DeleteMapping("/{id}/account")
    public ApiResponse<StudentDtos.GuardianResponse> unlinkAccount(@PathVariable UUID id) {
        return ApiResponse.ok(service.unlinkAccount(id), "Guardian account unlinked");
    }

    // ---------- Relationships ----------

    @GetMapping("/student/{studentId}")
    public ApiResponse<List<StudentDtos.GuardianLinkResponse>> guardiansOf(@PathVariable UUID studentId) {
        return ApiResponse.ok(service.guardiansOf(studentId));
    }

    @PostMapping("/student/{studentId}")
    public ResponseEntity<ApiResponse<StudentDtos.GuardianLinkResponse>> link(
            @PathVariable UUID studentId,
            @Valid @RequestBody StudentDtos.GuardianLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.link(studentId, request), "Guardian linked"));
    }

    @DeleteMapping("/student/{studentId}/links/{linkId}")
    public ApiResponse<Void> unlink(@PathVariable UUID studentId, @PathVariable UUID linkId) {
        service.unlink(studentId, linkId);
        return ApiResponse.ok();
    }

    /** One guardian may have several children, so this is a first-class query. */
    @GetMapping("/{id}/students")
    public ApiResponse<PageResponse<StudentDtos.StudentResponse>> children(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.childrenOf(id, pageable));
    }
}