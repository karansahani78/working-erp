package com.educationerp.importer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * The rows that matched something already in the system.
 *
 * <p>Kept apart from the errors: a duplicate is not a mistake in the file so much as a decision to
 * make about it, and a person importing a list of existing students needs to see both and decide,
 * not to have them lumped in with a malformed date.
 */
public interface ImportBatchDuplicateRepository extends JpaRepository<ImportBatchDuplicate, UUID> {

    List<ImportBatchDuplicate> findByBatchIdOrderByRowNumber(UUID batchId);

    void deleteByBatchId(UUID batchId);
}
