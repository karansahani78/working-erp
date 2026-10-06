package com.educationerp.importer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    Optional<ImportBatch> findByBatchNumber(String batchNumber);

    Page<ImportBatch> findByImportTypeOrderByCreatedAtDesc(ImportBatch.ImportType type,
                                                           Pageable pageable);

    Page<ImportBatch> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** For the import log. */
    List<ImportBatch> findByStatusOrderByCreatedAtDesc(ImportBatch.Status status);

    @Query("select b from ImportBatch b where b.id = :id")
    Optional<ImportBatch> findDetail(@Param("id") UUID id);
}
