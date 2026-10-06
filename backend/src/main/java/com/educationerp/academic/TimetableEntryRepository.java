package com.educationerp.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, UUID> {

    @Query("""
            select t from TimetableEntry t
            join fetch t.timeSlot ts
            join fetch t.offering o
            join fetch o.course c
            left join fetch t.room r
            where t.active = true
              and (:sectionId is null or t.section.id = :sectionId)
              and (:classId is null or t.schoolClass.id = :classId)
              and (:teacherId is null or t.teacherId = :teacherId)
              and (:day is null or t.dayOfWeek = :day)
            """)
    List<TimetableEntry> findForView(@Param("sectionId") UUID sectionId,
                                     @Param("classId") UUID classId,
                                     @Param("teacherId") UUID teacherId,
                                     @Param("day") DayOfWeek day);

    @Query("""
            select t from TimetableEntry t
            join fetch t.timeSlot ts
            where t.active = true and t.dayOfWeek = :day
              and (t.timeSlot.id = :timeSlotId)
              and (t.teacherId is not null and t.teacherId = :teacherId)
            """)
    List<TimetableEntry> findTeacherConflicts(@Param("day") DayOfWeek day,
                                              @Param("timeSlotId") UUID timeSlotId,
                                              @Param("teacherId") UUID teacherId);

    @Query("""
            select t from TimetableEntry t
            join fetch t.timeSlot ts
            where t.active = true and t.dayOfWeek = :day
              and t.timeSlot.id = :timeSlotId
              and t.room is not null and t.room.id = :roomId
            """)
    List<TimetableEntry> findRoomConflicts(@Param("day") DayOfWeek day,
                                           @Param("timeSlotId") UUID timeSlotId,
                                           @Param("roomId") UUID roomId);

    @Query("""
            select t from TimetableEntry t
            join fetch t.timeSlot ts
            where t.active = true and t.dayOfWeek = :day
              and t.timeSlot.id = :timeSlotId
              and ((:sectionId is not null and t.section.id = :sectionId)
                   or (:classId is not null and t.schoolClass.id = :classId))
            """)
    List<TimetableEntry> findGroupConflicts(@Param("day") DayOfWeek day,
                                            @Param("timeSlotId") UUID timeSlotId,
                                            @Param("sectionId") UUID sectionId,
                                            @Param("classId") UUID classId);

    @Query("""
            select t from TimetableEntry t join fetch t.timeSlot ts join fetch t.offering o join fetch o.course c
            where t.active = true and t.offering.id = :offeringId and t.dayOfWeek = :day
            """)
    List<TimetableEntry> findByOffering(@Param("offeringId") UUID offeringId, @Param("day") DayOfWeek day);

    Optional<TimetableEntry> findFirstByOfferingIdAndDayOfWeekAndTimeSlotId(UUID offeringId, DayOfWeek day, UUID timeSlotId);

    long countBySectionIdAndActiveTrue(UUID sectionId);
}
