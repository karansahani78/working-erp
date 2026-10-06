package com.educationerp.common.numbering;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSequence s where s.documentKind = :kind and s.period = :period")
    Optional<DocumentSequence> lockByKindAndPeriod(@Param("kind") DocumentSequence.Kind kind,
                                                   @Param("period") String period);
}
