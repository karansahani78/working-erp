package com.educationerp.exam;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** A single examinable subject within an examination. */
@Entity
@Table(name = "exam_subjects")
public class ExamSubject extends BaseEntity {

    @Column(name = "examination_id", nullable = false)
    private UUID examinationId;

    @Column(name = "course_offering_id")
    private UUID courseOfferingId;

    @Column(name = "subject_name", nullable = false, length = 150)
    private String subjectName;

    @Column(name = "subject_code", length = 40)
    private String subjectCode;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "max_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxMarks = new BigDecimal("100");

    @Column(name = "pass_marks", precision = 8, scale = 2)
    private BigDecimal passMarks;

    @Column(name = "room_id")
    private UUID roomId;

    public UUID getExaminationId() {
        return examinationId;
    }

    public void setExaminationId(UUID examinationId) {
        this.examinationId = examinationId;
    }

    public UUID getCourseOfferingId() {
        return courseOfferingId;
    }

    public void setCourseOfferingId(UUID courseOfferingId) {
        this.courseOfferingId = courseOfferingId;
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

    public LocalDate getExamDate() {
        return examDate;
    }

    public void setExamDate(LocalDate examDate) {
        this.examDate = examDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public BigDecimal getMaxMarks() {
        return maxMarks;
    }

    public void setMaxMarks(BigDecimal maxMarks) {
        this.maxMarks = maxMarks;
    }

    public BigDecimal getPassMarks() {
        return passMarks;
    }

    public void setPassMarks(BigDecimal passMarks) {
        this.passMarks = passMarks;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public void setRoomId(UUID roomId) {
        this.roomId = roomId;
    }
}
