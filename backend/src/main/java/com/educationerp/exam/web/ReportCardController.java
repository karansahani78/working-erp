package com.educationerp.exam.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.exam.dto.ExamDtos;
import com.educationerp.exam.service.ReportCardService;
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
@RequestMapping("/api/v1/exams")
@Tag(name = "ReportCards")
@RequiredArgsConstructor
public class ReportCardController {

    private final ReportCardService service;

    @PostMapping("/report-cards/generate")
    public ApiResponse<ExamDtos.ReportCardResponse> generate(@RequestParam UUID studentId,
                                                             @RequestParam(required = false) UUID academicYearId,
                                                             @RequestParam(required = false) UUID semesterId) {
        return ApiResponse.ok(service.generate(studentId, academicYearId, semesterId));
    }

    @GetMapping("/report-cards/{id}")
    public ApiResponse<ExamDtos.ReportCardResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PutMapping("/report-cards/{id}/status")
    public ApiResponse<ExamDtos.ReportCardResponse> transition(@PathVariable UUID id,
                                                                @Valid @RequestBody ExamDtos.StatusRequest request) {
        return ApiResponse.ok(service.transition(id, request));
    }

    @GetMapping("/report-cards/students/{studentId}")
    public ApiResponse<List<ExamDtos.ReportCardResponse>> forStudent(@PathVariable UUID studentId) {
        return ApiResponse.ok(service.forStudent(studentId));
    }

    @PostMapping("/transcripts/generate")
    public ApiResponse<ExamDtos.TranscriptResponse> generateTranscript(@RequestParam UUID studentId) {
        return ApiResponse.ok(service.generateTranscript(studentId));
    }

    @GetMapping("/transcripts/{id}")
    public ApiResponse<ExamDtos.TranscriptResponse> getTranscript(@PathVariable UUID id) {
        return ApiResponse.ok(service.getTranscript(id));
    }

    @PutMapping("/transcripts/{id}/finalise")
    public ApiResponse<ExamDtos.TranscriptResponse> finalise(@PathVariable UUID id) {
        return ApiResponse.ok(service.finaliseTranscript(id));
    }
}