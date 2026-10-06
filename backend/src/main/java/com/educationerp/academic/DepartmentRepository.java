package com.educationerp.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    Optional<Department> findByCodeIgnoreCase(String code);

    Optional<Department> findByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCase(String code);

    List<Department> findByFacultyIdOrderByNameAsc(UUID facultyId);

    List<Department> findByActiveTrueOrderByNameAsc();

    @Query("""
            select d from Department d left join fetch d.faculty f
            where (:term is null or lower(d.name) like :term or lower(d.code) like :term)
              and (:facultyId is null or d.faculty.id = :facultyId)
            """)
    Page<Department> search(@Param("term") String term, @Param("facultyId") UUID facultyId, Pageable pageable);
}
