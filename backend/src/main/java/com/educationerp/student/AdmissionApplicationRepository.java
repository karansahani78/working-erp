package com.educationerp.student;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AdmissionApplicationRepository extends JpaRepository<AdmissionApplication, UUID> {

    boolean existsByReferenceCode(String referenceCode);

    long countByCampaignIdAndStatusIn(UUID campaignId, java.util.Collection<AdmissionApplication.Status> statuses);

    Page<AdmissionApplication> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId, Pageable pageable);

    List<AdmissionApplication> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    @Query("""
            select a from AdmissionApplication a
            where (:campaignId is null or a.campaignId = :campaignId)
              and (:status is null or a.status = :status)
              and (:term is null or lower(a.firstName) like :term
                                 or lower(a.lastName) like :term
                                 or lower(a.email) like :term
                                 or lower(a.referenceCode) like :term)
            """)
    Page<AdmissionApplication> search(@Param("campaignId") UUID campaignId,
                                      @Param("status") AdmissionApplication.Status status,
                                      @Param("term") String term,
                                      Pageable pageable);
}