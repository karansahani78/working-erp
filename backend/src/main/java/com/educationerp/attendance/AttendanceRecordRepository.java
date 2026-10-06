package com.educationerp.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    Optional<AttendanceRecord> findByStudentIdAndCourseOfferingIdAndAttendanceDateAndTimeSlotId(
            UUID studentId, UUID courseOfferingId, LocalDate attendanceDate, UUID timeSlotId);

    List<AttendanceRecord> findByStudentIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
            UUID studentId, LocalDate from, LocalDate to);

    List<AttendanceRecord> findByCourseOfferingIdAndAttendanceDateOrderByStudentIdAsc(
            UUID courseOfferingId, LocalDate attendanceDate);

    List<AttendanceRecord> findByCourseOfferingIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
            UUID courseOfferingId, LocalDate from, LocalDate to);

    /**
     * Counts records per status over a date range, grouped in the database so the
     * attendance percentage report never has to load every row into memory.
     */
    @Query("""
            select a.status as status, count(a) as total
            from AttendanceRecord a
            where a.studentId = :studentId
              and (cast(:from as date) is null or a.attendanceDate >= :from)
              and (cast(:to as date) is null or a.attendanceDate <= :to)
            group by a.status
            """)
    List<StatusCount> countByStatus(@Param("studentId") UUID studentId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    /** One row of {@link #countByStatus}: how many periods had this status. */
    interface StatusCount {
        AttendanceRecord.Status getStatus();

        long getTotal();
    }
}
