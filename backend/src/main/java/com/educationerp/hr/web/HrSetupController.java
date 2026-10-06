package com.educationerp.hr.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.hr.EmployeeService;
import com.educationerp.hr.HrDtos;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hr")
@Tag(name = "HR - Structure")
@RequiredArgsConstructor
public class HrSetupController {

    private final EmployeeService service;

    @GetMapping("/designations")
    public ApiResponse<List<HrDtos.DesignationResponse>> designations() {
        return ApiResponse.ok(service.listDesignations());
    }

    @PostMapping("/designations")
    public ApiResponse<HrDtos.DesignationResponse> createDesignation(
            @Valid @RequestBody HrDtos.CreateDesignation request) {
        return ApiResponse.ok(service.createDesignation(request));
    }

    @GetMapping("/qualifications")
    public ApiResponse<List<HrDtos.QualificationResponse>> qualifications() {
        return ApiResponse.ok(service.listQualifications());
    }

    @PostMapping("/qualifications")
    public ApiResponse<HrDtos.QualificationResponse> createQualification(
            @Valid @RequestBody HrDtos.CreateQualification request) {
        return ApiResponse.ok(service.createQualification(request));
    }
}