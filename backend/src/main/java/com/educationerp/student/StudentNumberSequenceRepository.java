package com.educationerp.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

public interface StudentNumberSequenceRepository extends JpaRepository<StudentNumberSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudentNumberSequence s where s.prefix = :prefix and s.period = :period")
    Optional<StudentNumberSequence> lockByPrefixAndPeriod(@Param("prefix") String prefix,
                                                          @Param("period") String period);
}