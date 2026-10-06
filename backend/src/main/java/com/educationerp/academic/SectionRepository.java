package com.educationerp.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SectionRepository extends JpaRepository<Section, UUID> {

    List<Section> findBySchoolClassIdAndActiveTrueOrderByCodeAsc(UUID classId);

    List<Section> findBySchoolClassIdOrderByCodeAsc(UUID classId);

    Optional<Section> findBySchoolClassIdAndCodeIgnoreCase(UUID classId, String code);

    long countBySchoolClassId(UUID classId);

    @Query("""
            select s from Section s join fetch s.schoolClass c
            where (:classId is null or c.id = :classId)
              and (:term is null or lower(s.name) like :term or lower(s.code) like :term)
            """)
    Page<Section> search(@Param("classId") UUID classId, @Param("term") String term, Pageable pageable);
}
