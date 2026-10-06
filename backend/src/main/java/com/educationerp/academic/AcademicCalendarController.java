package com.educationerp.academic;

import com.educationerp.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/academic/calendar")
@Tag(name="AcademicCalendar")
@RequiredArgsConstructor
public class AcademicCalendarController {
    private final AcademicCalendarService service;

    @GetMapping
    public ApiResponse<List<AcademicCalendarDto>> list(@RequestParam UUID yearId) {
        return ApiResponse.ok(service.list(yearId));
    }

    @PostMapping
    public ApiResponse<AcademicCalendarDto> create(@Valid @RequestBody AcademicCalendarDto req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<AcademicCalendarDto> update(@PathVariable UUID id,@Valid @RequestBody AcademicCalendarDto req) {
        return ApiResponse.ok(service.update(id,req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
