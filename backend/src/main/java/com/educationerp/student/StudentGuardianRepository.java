package com.educationerp.student;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentGuardianRepository extends JpaRepository<StudentGuardian, UUID> {

    List<StudentGuardian> findByStudentIdOrderByCreatedAtAsc(UUID studentId);

    List<StudentGuardian> findByGuardianId(UUID guardianId);

    Optional<StudentGuardian> findByStudentIdAndGuardianIdAndRelationship(UUID studentId,
                                                                          UUID guardianId,
                                                                          StudentGuardian.Relationship relationship);

    Optional<StudentGuardian> findFirstByStudentIdAndPrimaryContactTrue(UUID studentId);
}