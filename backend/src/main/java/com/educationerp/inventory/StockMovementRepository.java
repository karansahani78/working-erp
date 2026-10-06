package com.educationerp.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    @Query("""
            select m from StockMovement m join fetch m.item
            where m.store.id = :storeId
            order by m.movedAt desc, m.id desc
            """)
    Page<StockMovement> findForStore(@Param("storeId") UUID storeId, Pageable pageable);

    /** Every movement behind one document, which is what makes a receipt reconstructable. */
    @Query("""
            select m from StockMovement m join fetch m.item
            where m.referenceType = :type and m.referenceId = :id
            order by m.movedAt asc
            """)
    List<StockMovement> findByReference(@Param("type") String type, @Param("id") UUID id);

    @Query("""
            select m from StockMovement m
            where m.item.id = :itemId
            order by m.movedAt desc, m.id desc
            """)
    Page<StockMovement> findForItem(@Param("itemId") UUID itemId, Pageable pageable);
}
