package com.educationerp.academic;

import com.educationerp.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/academic/programs")
@Tag(name = "Programmes and curricula")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService service;

    @GetMapping
    public ApiResponse<List<AcademicStructureDto.ProgramResponse>> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) Program.Status status) {
        return ApiResponse.ok(service.listPrograms(departmentId, status));
    }

    @PostMapping
    public ApiResponse<AcademicStructureDto.ProgramResponse> create(
            @Valid @RequestBody AcademicStructureDto.CreateProgram request) {
        return ApiResponse.ok(service.createProgram(request));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<AcademicStructureDto.ProgramResponse> archive(@PathVariable UUID id) {
        return ApiResponse.ok(service.archiveProgram(id));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<AcademicStructureDto.ProgramVersionResponse>> versions(@PathVariable UUID id) {
        return ApiResponse.ok(service.listVersions(id));
    }

    @PostMapping("/versions")
    public ApiResponse<AcademicStructureDto.ProgramVersionResponse> createVersion(
            @Valid @RequestBody AcademicStructureDto.CreateProgramVersion request) {
        return ApiResponse.ok(service.createVersion(request));
    }

    @PostMapping("/versions/{id}/activate")
    public ApiResponse<AcademicStructureDto.ProgramVersionResponse> activateVersion(@PathVariable UUID id) {
        return ApiResponse.ok(service.activateVersion(id));
    }

    @GetMapping("/versions/{id}/curricula")
    public ApiResponse<List<AcademicStructureDto.CurriculumResponse>> curricula(@PathVariable UUID id) {
        return ApiResponse.ok(service.listCurricula(id));
    }

    @PostMapping("/curricula")
    public ApiResponse<AcademicStructureDto.CurriculumResponse> createCurriculum(
            @Valid @RequestBody AcademicStructureDto.CreateCurriculum request) {
        return ApiResponse.ok(service.createCurriculum(request));
    }

    @GetMapping("/curricula/{id}/courses")
    public ApiResponse<List<AcademicStructureDto.CurriculumCourseResponse>> curriculumCourses(
            @PathVariable UUID id) {
        return ApiResponse.ok(service.listCurriculumCourses(id));
    }

    @PostMapping("/curricula/{id}/courses")
    public ApiResponse<AcademicStructureDto.CurriculumCourseResponse> addCurriculumCourse(
            @PathVariable UUID id,
            @Valid @RequestBody AcademicStructureDto.CurriculumCourseRequest request) {
        return ApiResponse.ok(service.addCurriculumCourse(id, request));
    }

    @DeleteMapping("/curricula/{id}/courses/{courseId}")
    public ApiResponse<Void> removeCurriculumCourse(@PathVariable UUID id, @PathVariable UUID courseId) {
        service.removeCurriculumCourse(id, courseId);
        return ApiResponse.ok();
    }
}
