package com.educationerp.library;

import com.educationerp.hr.Employee;
import com.educationerp.student.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LibraryMemberRepository extends JpaRepository<LibraryMember, UUID> {

    Optional<LibraryMember> findByMemberCodeIgnoreCase(String memberCode);

    Optional<LibraryMember> findByUserId(UUID userId);

    Optional<LibraryMember> findByStudentId(UUID studentId);

    Optional<LibraryMember> findByEmployeeId(UUID employeeId);

    Page<LibraryMember> findByStatusOrderByMemberCodeAsc(LibraryMember.Status status,
                                                         Pageable pageable);

    /**
     * Members to pick from at the lending desk, by whatever the librarian was given: a name, a
     * part of a name, or the card number typed off the card itself.
     */
    @Query("""
            select m from LibraryMember m
            left join Student s on s.id = m.studentId
            left join Employee e on e.id = m.employeeId
            where m.status = :status
              and (lower(m.memberCode) like :needle
                   or lower(m.externalName) like :needle
                   or lower(coalesce(s.firstName, '')) like :needle
                   or lower(coalesce(s.lastName, '')) like :needle
                   or lower(coalesce(e.firstName, '')) like :needle
                   or lower(coalesce(e.lastName, '')) like :needle)
            """)
    Page<LibraryMember> search(@Param("status") LibraryMember.Status status,
                               @Param("needle") String needle, Pageable pageable);
}
