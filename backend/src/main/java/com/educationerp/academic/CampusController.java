package com.educationerp.academic;

import com.educationerp.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/academic/campuses")
@Tag(name = "Campuses")
@RequiredArgsConstructor
public class CampusController {
    private final CampusService service;

    @GetMapping
    public ApiResponse<List<CampusDto>> list() {
        return ApiResponse.ok(service.listActive());
    }

    @PostMapping
    public ApiResponse<CampusDto> create(@Valid @RequestBody CampusDto req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<CampusDto> update(@PathVariable UUID id, @Valid @RequestBody CampusDto req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
