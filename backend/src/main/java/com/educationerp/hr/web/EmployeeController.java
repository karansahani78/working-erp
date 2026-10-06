package com.educationerp.hr.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.hr.Employee;
import com.educationerp.hr.EmployeeService;
import com.educationerp.hr.HrDtos;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hr/employees")
@Tag(name = "HR - Employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService service;

    @GetMapping
    public ApiResponse<List<HrDtos.EmployeeResponse>> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) Employee.Status status) {
        return ApiResponse.ok(service.list(departmentId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<HrDtos.EmployeeResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/{id}/profile")
    public ApiResponse<HrDtos.EmployeeProfile> profile(@PathVariable UUID id) {
        return ApiResponse.ok(service.profile(id));
    }

    @PostMapping
    public ApiResponse<HrDtos.EmployeeResponse> create(@Valid @RequestBody HrDtos.CreateEmployee request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<HrDtos.EmployeeResponse> update(@PathVariable UUID id,
                                                       @Valid @RequestBody HrDtos.UpdateEmployee request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/terminate")
    public ApiResponse<HrDtos.EmployeeResponse> terminate(@PathVariable UUID id,
                                                           @Valid @RequestBody HrDtos.TerminateEmployee request) {
        return ApiResponse.ok(service.terminate(id, request));
    }

    @PostMapping("/{id}/employments")
    public ApiResponse<HrDtos.EmploymentResponse> addEmployment(
            @PathVariable UUID id, @Valid @RequestBody HrDtos.CreateEmployment request) {
        return ApiResponse.ok(service.addEmployment(id, request));
    }

    @GetMapping("/{id}/qualifications")
    public ApiResponse<List<HrDtos.EmployeeQualificationResponse>> qualifications(@PathVariable UUID id) {
        return ApiResponse.ok(service.listEmployeeQualifications(id));
    }

    @PostMapping("/{id}/qualifications")
    public ApiResponse<HrDtos.EmployeeQualificationResponse> award(
            @PathVariable UUID id, @Valid @RequestBody HrDtos.AwardQualification request) {
        return ApiResponse.ok(service.awardQualification(id, request));
    }

    @GetMapping("/{id}/documents")
    public ApiResponse<List<HrDtos.EmployeeDocumentResponse>> documents(@PathVariable UUID id) {
        return ApiResponse.ok(service.listDocuments(id));
    }

    @PostMapping("/{id}/documents")
    public ApiResponse<HrDtos.EmployeeDocumentResponse> registerDocument(
            @PathVariable UUID id, @Valid @RequestBody HrDtos.RegisterDocument request) {
        return ApiResponse.ok(service.registerDocument(id, request));
    }

    @PutMapping("/{id}/documents/{documentId}")
    public ApiResponse<HrDtos.EmployeeDocumentResponse> reviewDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @Valid @RequestBody HrDtos.ReviewDocument request) {
        return ApiResponse.ok(service.reviewDocument(id, documentId, request));
    }
}