package com.educationerp.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    Optional<Course> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("""
            select c from Course c
            where (:term is null or lower(c.name) like :term or lower(c.code) like :term)
              and (:departmentId is null or c.department.id = :departmentId)
              and (:programId is null or c.program.id = :programId)
              and (:active is null or c.active = :active)
            """)
    Page<Course> search(@Param("term") String term,
                        @Param("departmentId") UUID departmentId,
                        @Param("programId") UUID programId,
                        @Param("active") Boolean active,
                        Pageable pageable);
}
