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
@RequestMapping("/api/v1/academic/departments")
@Tag(name="Departments")
@RequiredArgsConstructor
public class DepartmentController {
    private final DepartmentService service;

    @GetMapping
    public ApiResponse<PageResponse<DepartmentDto>> list(@RequestParam(required=false) String term,
                                                        @RequestParam(required=false) UUID facultyId,
                                                        @PageableDefault(size=20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(term,facultyId,pageable)));
    }

    @PostMapping
    public ApiResponse<DepartmentDto> create(@Valid @RequestBody DepartmentDto req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<DepartmentDto> update(@PathVariable UUID id,@Valid @RequestBody DepartmentDto req) {
        return ApiResponse.ok(service.update(id,req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
