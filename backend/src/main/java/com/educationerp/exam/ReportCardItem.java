package com.educationerp.exam;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** One subject line on a report card. Snapshot data, so later edits cannot rewrite history. */
@Entity
@Table(name = "report_card_items")
public class ReportCardItem extends BaseEntity {

    @Column(name = "report_card_id", nullable = false)
    private UUID reportCardId;

    @Column(name = "exam_subject_id")
    private UUID examSubjectId;

    @Column(name = "subject_name", nullable = false, length = 150)
    private String subjectName;

    @Column(name = "subject_code", length = 40)
    private String subjectCode;

    @Column(name = "marks_obtained", precision = 8, scale = 2)
    private BigDecimal marksObtained;

    @Column(name = "max_marks", precision = 8, scale = 2)
    private BigDecimal maxMarks;

    @Column(name = "credits", precision = 5, scale = 2)
    private BigDecimal credits;

    @Column(name = "letter_grade", length = 10)
    private String letterGrade;

    @Column(name = "grade_point", precision = 4, scale = 2)
    private BigDecimal gradePoint;

    @Column(name = "is_pass")
    private Boolean pass;

    public UUID getReportCardId() {
        return reportCardId;
    }

    public void setReportCardId(UUID reportCardId) {
        this.reportCardId = reportCardId;
    }

    public UUID getExamSubjectId() {
        return examSubjectId;
    }

    public void setExamSubjectId(UUID examSubjectId) {
        this.examSubjectId = examSubjectId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
    }

    public BigDecimal getMarksObtained() {
        return marksObtained;
    }

    public void setMarksObtained(BigDecimal marksObtained) {
        this.marksObtained = marksObtained;
    }

    public BigDecimal getMaxMarks() {
        return maxMarks;
    }

    public void setMaxMarks(BigDecimal maxMarks) {
        this.maxMarks = maxMarks;
    }

    public BigDecimal getCredits() {
        return credits;
    }

    public void setCredits(BigDecimal credits) {
        this.credits = credits;
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

    public Boolean getPass() {
        return pass;
    }

    public void setPass(Boolean pass) {
        this.pass = pass;
    }
}
