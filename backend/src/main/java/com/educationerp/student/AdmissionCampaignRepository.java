package com.educationerp.student;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AdmissionCampaignRepository extends JpaRepository<AdmissionCampaign, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    @Query("""
            select c from AdmissionCampaign c
            where (:status is null or c.status = :status)
              and (:term is null or lower(c.name) like :term or lower(c.code) like :term)
            """)
    Page<AdmissionCampaign> search(@Param("status") AdmissionCampaign.Status status,
                                   @Param("term") String term,
                                   Pageable pageable);
}