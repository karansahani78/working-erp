package com.educationerp.finance.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.finance.FeeStructure;
import com.educationerp.finance.service.FeeAssessmentService;
import com.educationerp.finance.service.FeeStructureService;
import com.educationerp.finance.dto.FinanceDtos;
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

import java.util.List;
import java.util.UUID;

/** Fee structures, discounts, scholarships and the concessions granted from them. */
@RestController
@RequestMapping("/api/v1/finance")
@Tag(name = "Finance")
@RequiredArgsConstructor
public class FeeStructureController {

    private final FeeStructureService fees;
    private final FeeAssessmentService assessments;

    // ----------------------------------------------------------- fee structures

    @GetMapping("/fee-structures")
    public ApiResponse<List<FinanceDtos.FeeStructureResponse>> list(
            @RequestParam(required = false) FeeStructure.Status status) {
        return ApiResponse.ok(status == null ? fees.list() : fees.listByStatus(status));
    }

    @GetMapping("/fee-structures/{id}")
    public ApiResponse<FinanceDtos.FeeStructureResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(fees.get(id));
    }

    @PostMapping("/fee-structures")
    public ApiResponse<FinanceDtos.FeeStructureResponse> create(
            @Valid @RequestBody FinanceDtos.CreateFeeStructure request) {
        return ApiResponse.ok(fees.create(request));
    }

    @PutMapping("/fee-structures/{id}")
    public ApiResponse<FinanceDtos.FeeStructureResponse> update(@PathVariable UUID id,
                                                                @Valid @RequestBody FinanceDtos.UpdateFeeStructure request) {
        return ApiResponse.ok(fees.update(id, request));
    }

    @PutMapping("/fee-structures/{id}/components")
    public ApiResponse<FinanceDtos.FeeStructureResponse> setComponents(
            @PathVariable UUID id, @Valid @RequestBody FinanceDtos.ComponentList request) {
        return ApiResponse.ok(fees.setComponents(id, request));
    }

    /** Publishing freezes the price: students already billed must keep the price they were given. */
    @PostMapping("/fee-structures/{id}/publish")
    public ApiResponse<FinanceDtos.FeeStructureResponse> publish(@PathVariable UUID id) {
        return ApiResponse.ok(fees.publish(id));
    }

    @PostMapping("/fee-structures/{id}/archive")
    public ApiResponse<FinanceDtos.FeeStructureResponse> archive(@PathVariable UUID id) {
        return ApiResponse.ok(fees.archive(id));
    }

    // ------------------------------------------------- discounts and scholarships

    @GetMapping("/discounts")
    public ApiResponse<List<FinanceDtos.AwardResponse>> listDiscounts() {
        return ApiResponse.ok(fees.listDiscounts());
    }

    @PostMapping("/discounts")
    public ApiResponse<FinanceDtos.AwardResponse> createDiscount(
            @Valid @RequestBody FinanceDtos.CreateDiscount request) {
        return ApiResponse.ok(fees.createDiscount(request));
    }

    @GetMapping("/scholarships")
    public ApiResponse<List<FinanceDtos.AwardResponse>> listScholarships() {
        return ApiResponse.ok(fees.listScholarships());
    }

    @PostMapping("/scholarships")
    public ApiResponse<FinanceDtos.AwardResponse> createScholarship(
            @Valid @RequestBody FinanceDtos.CreateScholarship request) {
        return ApiResponse.ok(fees.createScholarship(request));
    }

    // -------------------------------------------------------------- concessions

    @GetMapping("/students/{studentId}/concessions")
    public ApiResponse<List<FinanceDtos.ConcessionResponse>> listConcessions(
            @PathVariable UUID studentId, @RequestParam UUID academicYearId) {
        return ApiResponse.ok(fees.listConcessions(studentId, academicYearId));
    }

    @PostMapping("/students/{studentId}/concessions")
    public ApiResponse<FinanceDtos.ConcessionResponse> grantConcession(
            @PathVariable UUID studentId, @RequestParam UUID academicYearId,
            @Valid @RequestBody FinanceDtos.GrantConcession request) {
        return ApiResponse.ok(fees.grantConcession(studentId, academicYearId, request));
    }

    @DeleteMapping("/concessions/{id}")
    public ApiResponse<FinanceDtos.ConcessionResponse> revokeConcession(@PathVariable UUID id) {
        return ApiResponse.ok(fees.revokeConcession(id));
    }

    // ---------------------------------------------------------------- assessment

    @GetMapping("/students/{studentId}/fee-assessments")
    public ApiResponse<List<FinanceDtos.FeeAssessmentResponse>> listAssessments(@PathVariable UUID studentId) {
        return ApiResponse.ok(assessments.listAssessments(studentId));
    }

    @GetMapping("/fee-assessments/{id}")
    public ApiResponse<FinanceDtos.FeeAssessmentResponse> getAssessment(@PathVariable UUID id) {
        return ApiResponse.ok(assessments.getAssessment(id));
    }

    @PostMapping("/students/{studentId}/fee-assessments")
    public ApiResponse<FinanceDtos.FeeAssessmentResponse> assess(
            @PathVariable UUID studentId, @Valid @RequestBody FinanceDtos.AssessFees request) {
        return ApiResponse.ok(assessments.assess(studentId, request));
    }

    @DeleteMapping("/fee-assessments/{id}")
    public ApiResponse<FinanceDtos.FeeAssessmentResponse> cancelAssessment(@PathVariable UUID id,
                                                                           @RequestParam(required = false) String reason) {
        return ApiResponse.ok(assessments.cancelAssessment(id, reason));
    }

    // ------------------------------------------------------------------ receivables

    @GetMapping("/reports/receivables")
    public ApiResponse<FinanceDtos.ReceivablesReport> receivables() {
        return ApiResponse.ok(assessments.receivables());
    }
}
