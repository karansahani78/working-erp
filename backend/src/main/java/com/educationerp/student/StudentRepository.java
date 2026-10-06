package com.educationerp.student;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findByStudentNumber(String studentNumber);

    Optional<Student> findByEmailIgnoreCase(String email);

    boolean existsByStudentNumber(String studentNumber);

    Optional<Student> findFirstByEmailIgnoreCase(String email);

    /** The student record a sign-in account belongs to. */
    Optional<Student> findByUserId(UUID userId);

    Page<Student> findByStatusOrderByStudentNumberAsc(Student.Status status, Pageable pageable);

    @Query("""
            select s from Student s
            where (:status is null or s.status = :status)
              and (:term is null or lower(s.firstName) like :term
                                 or lower(s.lastName) like :term
                                 or lower(s.studentNumber) like :term
                                 or lower(s.email) like :term)
            """)
    Page<Student> search(@Param("status") Student.Status status,
                         @Param("term") String term,
                         Pageable pageable);
}