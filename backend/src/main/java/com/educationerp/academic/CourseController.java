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
@RequestMapping("/api/v1/academic/courses")
@Tag(name="Courses")
@RequiredArgsConstructor
public class CourseController {
    private final CourseService service;

    @GetMapping
    public ApiResponse<PageResponse<CourseDto>> list(@RequestParam(required=false) String term,
                                                     @PageableDefault(size=20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(term,pageable)));
    }

    @PostMapping
    public ApiResponse<CourseDto> create(@Valid @RequestBody CourseDto req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<CourseDto> update(@PathVariable UUID id,@Valid @RequestBody CourseDto req) {
        return ApiResponse.ok(service.update(id,req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
