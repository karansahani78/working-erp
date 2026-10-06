package com.educationerp.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgramRepository extends JpaRepository<Program, UUID> {

    Optional<Program> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Program> findByDepartmentIdOrderByNameAsc(UUID departmentId);

    List<Program> findByStatusOrderByNameAsc(Program.Status status);

    @Query("""
            select p from Program p left join fetch p.department d
            where (:term is null or lower(p.name) like :term or lower(p.code) like :term)
              and (:departmentId is null or p.department.id = :departmentId)
              and (:status is null or p.status = :status)
            """)
    Page<Program> search(@Param("term") String term,
                         @Param("departmentId") UUID departmentId,
                         @Param("status") Program.Status status,
                         Pageable pageable);
}
