package com.educationerp.asset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    Optional<Asset> findByAssetNumberIgnoreCase(String assetNumber);

    @Query("""
            select a from Asset a
            where (:categoryId is null or a.category.id = :categoryId)
              and (:status is null or a.status = :status)
              and (:departmentId is null or a.department.id = :departmentId)
              and (:term is null or lower(a.name) like :term or lower(a.assetNumber) like :term
                   or lower(coalesce(a.serialNumber, '')) like :term)
            """)
    Page<Asset> search(@Param("categoryId") UUID categoryId,
                       @Param("status") Asset.Status status,
                       @Param("departmentId") UUID departmentId,
                       @Param("term") String term,
                       Pageable pageable);

    /** Everything out on loan, for the "who has what" report and the handover sheet. */
    List<Asset> findByStatusOrderByNameAsc(Asset.Status status);

    @Query("""
            select a from Asset a
            where a.warrantyExpiry is not null and a.warrantyExpiry between :from and :to
            order by a.warrantyExpiry asc
            """)
    List<Asset> findWithWarrantyExpiringBetween(@Param("from") java.time.LocalDate from,
                                                 @Param("to") java.time.LocalDate to);
}
