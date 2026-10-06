package com.educationerp.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface StockIssueRepository extends JpaRepository<StockIssue, UUID> {

    Page<StockIssue> findByStatusOrderByIssuedAtDesc(StockIssue.Status status, Pageable pageable);

    Page<StockIssue> findAllByOrderByIssuedAtDesc(Pageable pageable);

    /** What one recipient has taken, for a department's running bill. */
    @Query("""
            select i from StockIssue i
            where i.issuedToType = :type and i.issuedToId = :id and i.status = :status
            order by i.issuedAt desc
            """)
    Page<StockIssue> findForRecipient(@Param("type") StockIssue.RecipientType type,
                                      @Param("id") UUID id,
                                      @Param("status") StockIssue.Status status,
                                      Pageable pageable);

    long countByStatus(StockIssue.Status status);
}
