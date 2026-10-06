package com.educationerp.exam.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.exam.dto.ExamDtos;
import com.educationerp.exam.service.GradingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exams/grading-scales")
@Tag(name = "Grading")
@RequiredArgsConstructor
public class GradingController {

    private final GradingService service;

    @GetMapping
    public ApiResponse<List<ExamDtos.GradingScaleResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<ExamDtos.GradingScaleResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<ExamDtos.GradingScaleResponse> create(@Valid @RequestBody ExamDtos.GradingScaleRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExamDtos.GradingScaleResponse> update(@PathVariable UUID id,
                                                             @Valid @RequestBody ExamDtos.GradingScaleRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }
}