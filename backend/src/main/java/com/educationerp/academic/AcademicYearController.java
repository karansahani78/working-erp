package com.educationerp.academic;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.api.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/academic/academic-years")
@Tag(name="AcademicYears")
@RequiredArgsConstructor
public class AcademicYearController {
    private final AcademicYearService service;

    @GetMapping
    public ApiResponse<PageResponse<AcademicYearDto>> list(@RequestParam(required=false) String term,
                                                           @PageableDefault(size=20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(term,pageable)));
    }

    @PostMapping
    public ApiResponse<AcademicYearDto> create(@Valid @RequestBody AcademicYearDto req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<AcademicYearDto> update(@PathVariable UUID id,@Valid @RequestBody AcademicYearDto req) {
        return ApiResponse.ok(service.update(id,req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
