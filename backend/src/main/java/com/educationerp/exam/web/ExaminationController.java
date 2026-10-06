package com.educationerp.exam.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.exam.dto.ExamDtos;
import com.educationerp.exam.service.ExaminationService;
import com.educationerp.exam.service.ResultService;
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
@Tag(name = "Examinations")
@RequiredArgsConstructor
public class ExaminationController {

    private final ExaminationService examinations;
    private final ResultService results;

    @GetMapping
    public ApiResponse<List<ExamDtos.ExaminationResponse>> list() {
        return ApiResponse.ok(examinations.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<ExamDtos.ExaminationResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(examinations.get(id));
    }

    @PostMapping
    public ApiResponse<ExamDtos.ExaminationResponse> create(@Valid @RequestBody ExamDtos.ExaminationRequest request) {
        return ApiResponse.ok(examinations.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExamDtos.ExaminationResponse> update(@PathVariable UUID id,
                                                           @Valid @RequestBody ExamDtos.ExaminationRequest request) {
        return ApiResponse.ok(examinations.update(id, request));
    }

    /** Status workflow: PLANNED → SCHEDULED → IN_PROGRESS → MARKS_ENTERED → … → PUBLISHED. */
    @PutMapping("/{id}/status")
    public ApiResponse<ExamDtos.ExaminationResponse> transition(@PathVariable UUID id,
                                                                @Valid @RequestBody ExamDtos.StatusRequest request) {
        return ApiResponse.ok(examinations.transition(id, request));
    }

    @GetMapping("/{id}/schedule")
    public ApiResponse<List<ExamDtos.ExamSubjectResponse>> schedule(@PathVariable UUID id) {
        return ApiResponse.ok(examinations.schedule(id));
    }

    @PutMapping("/{id}/schedule")
    public ApiResponse<List<ExamDtos.ExamSubjectResponse>> replaceSchedule(
            @PathVariable UUID id,
            @Valid @RequestBody ExamDtos.ExamScheduleRequest request) {
        return ApiResponse.ok(examinations.replaceSchedule(id, request));
    }

    @GetMapping("/{id}/results")
    public ApiResponse<List<ExamDtos.ResultResponse>> results(@PathVariable UUID id) {
        return ApiResponse.ok(results.forExamination(id));
    }

    /** Bulk transition across every result of an examination. */
    @PutMapping("/{id}/results/status")
    public ApiResponse<List<ExamDtos.ResultResponse>> bulkTransition(
            @PathVariable UUID id,
            @Valid @RequestBody ExamDtos.StatusRequest request) {
        return ApiResponse.ok(results.transitionExam(id, request));
    }

    @PostMapping("/subjects/{examSubjectId}/marks")
    public ApiResponse<List<ExamDtos.ResultResponse>> enterMarks(
            @PathVariable UUID examSubjectId,
            @Valid @RequestBody ExamDtos.MarkEntryRequest request) {
        return ApiResponse.ok(results.enterMarks(examSubjectId, request));
    }

    @PutMapping("/results/{resultId}/status")
    public ApiResponse<ExamDtos.ResultResponse> transitionResult(@PathVariable UUID resultId,
                                                                  @Valid @RequestBody ExamDtos.StatusRequest request) {
        return ApiResponse.ok(results.transition(resultId, request));
    }

    @GetMapping("/results/students/{studentId}")
    public ApiResponse<List<ExamDtos.ResultResponse>> resultsForStudent(@PathVariable UUID studentId) {
        return ApiResponse.ok(results.forStudent(studentId, null));
    }

    @GetMapping("/results/students/{studentId}/status")
    public ApiResponse<List<ExamDtos.ResultResponse>> resultsForStudent(@PathVariable UUID studentId,
                                                                       @RequestParam String status) {
        return ApiResponse.ok(results.forStudent(studentId,
                com.educationerp.exam.Result.ResultStatus.valueOf(status.toUpperCase())));
    }

    @PostMapping("/results/{resultId}/corrections")
    public ApiResponse<ExamDtos.ResultCorrectionResponse> requestCorrection(
            @PathVariable UUID resultId,
            @Valid @RequestBody ExamDtos.ResultCorrectionRequest request) {
        return ApiResponse.ok(results.requestCorrection(resultId, request));
    }

    @GetMapping("/results/{resultId}/corrections")
    public ApiResponse<List<ExamDtos.ResultCorrectionResponse>> correctionsFor(@PathVariable UUID resultId) {
        return ApiResponse.ok(results.correctionsFor(resultId));
    }

    @PutMapping("/corrections/{correctionId}/decision")
    public ApiResponse<ExamDtos.ResultCorrectionResponse> decideCorrection(
            @PathVariable UUID correctionId,
            @Valid @RequestBody ExamDtos.CorrectionDecision decision) {
        return ApiResponse.ok(results.decideCorrection(correctionId, decision));
    }
}