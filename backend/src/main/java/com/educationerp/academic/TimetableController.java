package com.educationerp.academic;

import com.educationerp.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/academic/timetable")
@Tag(name = "Timetable")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService service;

    @GetMapping
    public ApiResponse<AcademicStructureDto.TimetableGrid> grid(
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) DayOfWeek day) {
        return ApiResponse.ok(service.grid(sectionId, classId, teacherId, day));
    }

    @PostMapping
    public ApiResponse<AcademicStructureDto.TimetableEntryResponse> create(
            @Valid @RequestBody AcademicStructureDto.CreateTimetableEntry request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AcademicStructureDto.TimetableEntryResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody AcademicStructureDto.CreateTimetableEntry request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
