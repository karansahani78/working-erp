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
@RequestMapping("/api/v1/academic/classes")
@Tag(name="Classes")
@RequiredArgsConstructor
public class SchoolClassController {
    private final SchoolClassService service;

    @GetMapping
    public ApiResponse<PageResponse<SchoolClassDto>> list(@RequestParam UUID yearId,
                                                          @RequestParam(required=false) String term,
                                                          @PageableDefault(size=20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.search(yearId,term,pageable)));
    }

    @PostMapping
    public ApiResponse<SchoolClassDto> create(@Valid @RequestBody SchoolClassDto req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<SchoolClassDto> update(@PathVariable UUID id,@Valid @RequestBody SchoolClassDto req) {
        return ApiResponse.ok(service.update(id,req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
