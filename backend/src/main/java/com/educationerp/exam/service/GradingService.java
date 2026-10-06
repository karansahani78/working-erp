package com.educationerp.exam.service;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.exam.GradeBoundary;
import com.educationerp.exam.GradeBoundaryRepository;
import com.educationerp.exam.GradingScale;
import com.educationerp.exam.GradingScaleRepository;
import com.educationerp.exam.dto.ExamDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Institution-defined grading scales.
 *
 * <p>The blueprint is explicit that grading must not be hardcoded: letter grades, grade
 * points and pass marks all come from {@link GradeBoundary} rows owned by the
 * institution, so a school can run a 4.0 GPA scale, a 10-point scale or a plain
 * pass/fail scheme without a code change.
 */
@Service
@RequiredArgsConstructor
public class GradingService {

    private final GradingScaleRepository scales;
    private final GradeBoundaryRepository boundaries;
    private final AuthorizationChecker auth;

    private static final BigDecimal MAX_PERCENT = new BigDecimal("100.00");
    private static final BigDecimal SMALLEST_STEP = new BigDecimal("0.01");

    @Transactional(readOnly = true)
    public List<ExamDtos.GradingScaleResponse> list() {
        auth.requirePermission("GRADING_READ");
        return scales.findByActiveTrueOrderByNameAsc().stream()
                .map(scale -> ExamDtos.GradingScaleResponse.from(scale, boundariesOf(scale.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ExamDtos.GradingScaleResponse get(UUID id) {
        auth.requirePermission("GRADING_READ");
        return ExamDtos.GradingScaleResponse.from(requireScale(id), boundariesOf(id));
    }

    @Transactional
    public ExamDtos.GradingScaleResponse create(ExamDtos.GradingScaleRequest request) {
        auth.requirePermission("GRADING_MANAGE");
        if (scales.existsByCodeIgnoreCase(request.code().trim())) {
            throw AppException.duplicate("A grading scale with this code already exists.");
        }
        List<GradeBoundary> prepared = validateBoundaries(request.boundaries());

        GradingScale scale = new GradingScale();
        scale.setName(request.name().trim());
        scale.setCode(request.code().trim());
        scale.setDescription(request.description());
        scale.setActive(request.active() == null || request.active());
        scales.save(scale);

        prepared.forEach(boundary -> boundary.setGradingScaleId(scale.getId()));
        boundaries.saveAll(prepared);
        return ExamDtos.GradingScaleResponse.from(scale, prepared);
    }

    @Transactional
    public ExamDtos.GradingScaleResponse update(UUID id, ExamDtos.GradingScaleRequest request) {
        auth.requirePermission("GRADING_MANAGE");
        GradingScale scale = requireScale(id);
        scales.findByCodeIgnoreCase(request.code().trim())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw AppException.duplicate("A grading scale with this code already exists.");
                });

        List<GradeBoundary> prepared = validateBoundaries(request.boundaries());

        scale.setName(request.name().trim());
        scale.setCode(request.code().trim());
        scale.setDescription(request.description());
        if (request.active() != null) {
            scale.setActive(request.active());
        }
        scales.save(scale);

        boundaries.deleteAll(boundaries.findByGradingScaleIdOrderByMinPercentageDesc(id));
        prepared.forEach(boundary -> boundary.setGradingScaleId(id));
        boundaries.saveAll(prepared);
        return ExamDtos.GradingScaleResponse.from(scale, prepared);
    }

    /**
     * Resolves a percentage against a scale's boundaries.
     *
     * @return the matching band, or {@code null} when the scale does not cover the value
     */
    @Transactional(readOnly = true)
    public GradeBoundary resolve(UUID gradingScaleId, BigDecimal percentage) {
        if (gradingScaleId == null || percentage == null) {
            return null;
        }
        for (GradeBoundary boundary : boundaries.findByGradingScaleIdOrderByMinPercentageDesc(gradingScaleId)) {
            if (boundary.matches(percentage)) {
                return boundary;
            }
        }
        return null;
    }

    // ---------- helpers ----------

    /**
     * Validates that the bands are ordered, non-overlapping and cover 0-100 with no gaps,
     * so every percentage a student can earn resolves to exactly one grade.
     */
    private List<GradeBoundary> validateBoundaries(List<ExamDtos.GradeBoundaryRequest> requests) {
        List<ExamDtos.GradeBoundaryRequest> ordered = requests.stream()
                .sorted(Comparator.comparing(ExamDtos.GradeBoundaryRequest::minPercentage).reversed())
                .toList();

        for (ExamDtos.GradeBoundaryRequest request : ordered) {
            if (request.maxPercentage() != null
                    && request.maxPercentage().compareTo(request.minPercentage()) < 0) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Grade " + request.letterGrade() + " has a maximum below its minimum.");
            }
            if (request.maxPercentage() != null
                    && request.maxPercentage().compareTo(MAX_PERCENT) > 0) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Grade " + request.letterGrade() + " cannot end above 100%.");
            }
        }
        long distinctGrades = ordered.stream()
                .map(r -> r.letterGrade().trim().toUpperCase())
                .distinct()
                .count();
        if (distinctGrades != ordered.size()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Each letter grade may only be defined once.");
        }

        // Bands run downwards, so each band must start exactly one step above where the
        // band below it ends. Anything else either overlaps or leaves an ungraded range.
        for (int i = 0; i < ordered.size() - 1; i++) {
            ExamDtos.GradeBoundaryRequest upper = ordered.get(i);
            ExamDtos.GradeBoundaryRequest lower = ordered.get(i + 1);
            if (lower.maxPercentage() == null) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Grade " + lower.letterGrade() + " needs a maximum because a lower grade follows it.");
            }
            BigDecimal expectedMin = lower.maxPercentage().add(SMALLEST_STEP).setScale(2, RoundingMode.HALF_UP);
            if (upper.minPercentage().compareTo(expectedMin) < 0) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Grades " + lower.letterGrade() + " and " + upper.letterGrade()
                                + " overlap; " + upper.letterGrade() + " must start at "
                                + expectedMin.toPlainString() + " or above.");
            }
            if (upper.minPercentage().compareTo(expectedMin) > 0) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Grade boundaries leave a gap between " + lower.letterGrade() + " and "
                                + upper.letterGrade() + ".");
            }
        }

        ExamDtos.GradeBoundaryRequest highest = ordered.get(0);
        if (highest.maxPercentage() != null
                && highest.maxPercentage().compareTo(MAX_PERCENT) < 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "The highest grade " + highest.letterGrade() + " must reach 100%.");
        }

        ExamDtos.GradeBoundaryRequest lowest = ordered.get(ordered.size() - 1);
        if (lowest.minPercentage().compareTo(BigDecimal.ZERO) > 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Grade boundaries must reach 0%; the lowest starts at "
                            + lowest.minPercentage().toPlainString() + ".");
        }

        return ordered.stream().map(this::toEntity).toList();
    }

    private GradeBoundary toEntity(ExamDtos.GradeBoundaryRequest request) {
        GradeBoundary boundary = new GradeBoundary();
        boundary.setLetterGrade(request.letterGrade().trim().toUpperCase());
        boundary.setGradePoint(request.gradePoint());
        boundary.setMinPercentage(request.minPercentage());
        boundary.setMaxPercentage(request.maxPercentage());
        boundary.setPass(request.pass() == null || request.pass());
        return boundary;
    }

    private List<GradeBoundary> boundariesOf(UUID scaleId) {
        return boundaries.findByGradingScaleIdOrderByMinPercentageDesc(scaleId);
    }

    private GradingScale requireScale(UUID id) {
        return scales.findById(id).orElseThrow(() -> AppException.notFound("Grading scale"));
    }
}