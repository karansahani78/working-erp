package com.educationerp.academic;

import com.educationerp.common.api.ApiResponse;
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
@RequestMapping("/api/v1/academic/rooms")
@Tag(name = "Rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService service;

    @GetMapping
    public ApiResponse<List<AcademicStructureDto.RoomResponse>> list(
            @RequestParam(required = false) UUID campusId) {
        return ApiResponse.ok(service.list(campusId));
    }

    @PostMapping
    public ApiResponse<AcademicStructureDto.RoomResponse> create(
            @Valid @RequestBody AcademicStructureDto.CreateRoom request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AcademicStructureDto.RoomResponse> update(
            @PathVariable UUID id, @Valid @RequestBody AcademicStructureDto.CreateRoom request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<AcademicStructureDto.RoomResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.ok(service.deactivate(id));
    }
}
