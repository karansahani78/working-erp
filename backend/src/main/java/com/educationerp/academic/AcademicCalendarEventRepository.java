package com.educationerp.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AcademicCalendarEventRepository extends JpaRepository<AcademicCalendarEvent, UUID> {

    List<AcademicCalendarEvent> findByAcademicYearIdOrderByStartDateAsc(UUID academicYearId);

    @Query("""
            select e from AcademicCalendarEvent e
            where e.academicYear.id = :yearId and e.startDate <= :date and e.endDate >= :date
            """)
    List<AcademicCalendarEvent> findActiveOn(@Param("yearId") UUID yearId, @Param("date") LocalDate date);

    @Query("""
            select e from AcademicCalendarEvent e
            where e.eventType = :type and e.endDate >= :from and e.startDate <= :to
            order by e.startDate asc
            """)
    List<AcademicCalendarEvent> findByTypeInWindow(@Param("type") AcademicCalendarEvent.EventType type,
                                                   @Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);
}
