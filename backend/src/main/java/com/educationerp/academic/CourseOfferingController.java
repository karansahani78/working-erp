package com.educationerp.academic;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.api.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/academic/offerings")
@Tag(name = "Course offerings")
@RequiredArgsConstructor
public class CourseOfferingController {

    private final CourseOfferingService service;

    @GetMapping
    public ApiResponse<PageResponse<AcademicStructureDto.CourseOfferingResponse>> list(
            @RequestParam(required = false) UUID yearId,
            @RequestParam(required = false) UUID semesterId,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) UUID programId,
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) String term,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                service.search(yearId, semesterId, classId, sectionId, programId, teacherId,
                        term, active, pageable)));
    }

    @PostMapping
    public ApiResponse<AcademicStructureDto.CourseOfferingResponse> create(
            @Valid @RequestBody AcademicStructureDto.CreateCourseOffering request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AcademicStructureDto.CourseOfferingResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody AcademicStructureDto.UpdateCourseOffering request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<AcademicStructureDto.CourseOfferingResponse> close(@PathVariable UUID id) {
        return ApiResponse.ok(service.close(id));
    }
}
