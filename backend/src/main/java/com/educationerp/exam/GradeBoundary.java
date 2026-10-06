package com.educationerp.exam;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One band within a grading scale: a percentage range mapped to a letter grade, grade
 * point and pass flag. A null {@code maxPercentage} means the band is open-ended (F).
 */
@Entity
@Table(name = "grade_boundaries")
public class GradeBoundary extends BaseEntity {

    @Column(name = "grading_scale_id", nullable = false)
    private UUID gradingScaleId;

    @Column(name = "letter_grade", nullable = false, length = 10)
    private String letterGrade;

    @Column(name = "grade_point", nullable = false, precision = 4, scale = 2)
    private BigDecimal gradePoint;

    @Column(name = "min_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal minPercentage;

    @Column(name = "max_percentage", precision = 5, scale = 2)
    private BigDecimal maxPercentage;

    @Column(name = "pass_flag", nullable = false)
    private boolean pass = true;

    public UUID getGradingScaleId() {
        return gradingScaleId;
    }

    public void setGradingScaleId(UUID gradingScaleId) {
        this.gradingScaleId = gradingScaleId;
    }

    public String getLetterGrade() {
        return letterGrade;
    }

    public void setLetterGrade(String letterGrade) {
        this.letterGrade = letterGrade;
    }

    public BigDecimal getGradePoint() {
        return gradePoint;
    }

    public void setGradePoint(BigDecimal gradePoint) {
        this.gradePoint = gradePoint;
    }

    public BigDecimal getMinPercentage() {
        return minPercentage;
    }

    public void setMinPercentage(BigDecimal minPercentage) {
        this.minPercentage = minPercentage;
    }

    public BigDecimal getMaxPercentage() {
        return maxPercentage;
    }

    public void setMaxPercentage(BigDecimal maxPercentage) {
        this.maxPercentage = maxPercentage;
    }

    public boolean isPass() {
        return pass;
    }

    public void setPass(boolean pass) {
        this.pass = pass;
    }

    /** True when the supplied percentage falls inside this band. */
    public boolean matches(BigDecimal percentage) {
        if (percentage.compareTo(minPercentage) < 0) {
            return false;
        }
        return maxPercentage == null || percentage.compareTo(maxPercentage) <= 0;
    }
}
