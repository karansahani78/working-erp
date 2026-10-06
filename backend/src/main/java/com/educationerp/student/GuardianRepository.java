package com.educationerp.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuardianRepository extends JpaRepository<Guardian, UUID> {

    Optional<Guardian> findFirstByEmailIgnoreCase(String email);

    Optional<Guardian> findFirstByPhone(String phone);

    Optional<Guardian> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * The signed-in accounts of every guardian of one student. Guardians without an account
     * are skipped: there is nobody to notify.
     */
    @Query("""
            select g.userId from StudentGuardian sg
            join Guardian g on g.id = sg.guardianId
            where sg.studentId = :studentId and g.userId is not null
            """)
    List<UUID> accountIdsForStudent(@Param("studentId") UUID studentId);

    @Query("""
            select sg.studentId from StudentGuardian sg
            join Guardian g on g.id = sg.guardianId
            where g.userId = :userId
            """)
    List<UUID> studentIdsForUser(@Param("userId") UUID userId);
}