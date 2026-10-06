package com.educationerp.student.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.api.PageResponse;
import com.educationerp.student.AdmissionCampaign;
import com.educationerp.student.dto.StudentDtos;
import com.educationerp.student.service.AdmissionCampaignService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admissions/campaigns")
@Tag(name = "Admission Campaigns")
@RequiredArgsConstructor
public class AdmissionCampaignController {

    private final AdmissionCampaignService service;

    @GetMapping
    public ApiResponse<PageResponse<StudentDtos.CampaignResponse>> list(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.search(status, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<StudentDtos.CampaignResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudentDtos.CampaignResponse>> create(
            @Valid @RequestBody StudentDtos.CampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(request), "Campaign created"));
    }

    @PutMapping("/{id}")
    public ApiResponse<StudentDtos.CampaignResponse> update(
            @PathVariable UUID id, @Valid @RequestBody StudentDtos.CampaignRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<StudentDtos.CampaignResponse> changeStatus(
            @PathVariable UUID id, @RequestParam String status) {
        return ApiResponse.ok(service.changeStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}