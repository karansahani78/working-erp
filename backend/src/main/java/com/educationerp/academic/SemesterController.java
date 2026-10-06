package com.educationerp.academic;

import com.educationerp.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/academic/semesters")
@Tag(name = "Semesters and terms")
@RequiredArgsConstructor
public class SemesterController {

    private final SemesterService service;

    @GetMapping
    public ApiResponse<List<AcademicStructureDto.SemesterResponse>> list(
            @RequestParam(required = false) UUID academicYearId) {
        return ApiResponse.ok(service.list(academicYearId));
    }

    @PostMapping
    public ApiResponse<AcademicStructureDto.SemesterResponse> create(
            @Valid @RequestBody AcademicStructureDto.CreateSemester request) {
        return ApiResponse.ok(service.create(request));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<AcademicStructureDto.SemesterResponse> activate(@PathVariable UUID id) {
        return ApiResponse.ok(service.changeStatus(id, Semester.Status.ACTIVE));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<AcademicStructureDto.SemesterResponse> complete(@PathVariable UUID id) {
        return ApiResponse.ok(service.changeStatus(id, Semester.Status.COMPLETED));
    }
}
