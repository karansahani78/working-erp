package com.educationerp.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    Optional<Item> findByCodeIgnoreCase(String code);

    @Query("""
            select i from Item i
            where (:categoryId is null or i.category.id = :categoryId)
              and (:activeOnly = false or i.active = true)
              and (:term is null or lower(i.name) like :term or lower(i.code) like :term)
            """)
    Page<Item> search(@Param("categoryId") UUID categoryId,
                      @Param("activeOnly") boolean activeOnly,
                      @Param("term") String term,
                      Pageable pageable);

    /**
     * What is running low.
     *
     * <p>Totalled across stores: a school with a full lab cupboard and an empty main store
     * still has paper, so the warning is about the item, not about one shelf.
     */
    @Query("""
            select i from Item i
            left join Stock s on s.item.id = i.id
            where i.active = true
            group by i.id
            having coalesce(sum(s.quantity), 0) <= i.reorderLevel
            order by coalesce(sum(s.quantity), 0) asc, i.name asc
            """)
    List<Item> findNeedingReorder();

    /** Total on hand across every store, used by the low-stock check. */
    @Query("select coalesce(sum(s.quantity), 0) from Stock s where s.item.id = :itemId")
    BigDecimal totalOnHand(@Param("itemId") UUID itemId);
}
