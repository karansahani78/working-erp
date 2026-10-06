package com.educationerp.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository extends JpaRepository<Stock, UUID> {

    Optional<Stock> findByItemIdAndStoreId(UUID itemId, UUID storeId);

    /**
     * Creates the empty stock row for an item in a store if it is not there yet, without
     * disturbing the transaction that asked: the first movement of an item is the one case where
     * two callers can be creating the same row at once, and the unique constraint on
     * (item_id, store_id) would otherwise hand one of them an avoidable failure. Doing the
     * insert through SQL rather than the entity keeps it out of the persistence context, so a
     * conflict costs nothing.
     */
    @Modifying
    @Query(value = """
            insert into stock (id, item_id, store_id)
            values (gen_random_uuid(), :itemId, :storeId)
            on conflict on constraint uk_stock_item_store do nothing
            """, nativeQuery = true)
    void createIfAbsent(@Param("itemId") UUID itemId, @Param("storeId") UUID storeId);

    /**
     * The same lookup, taken with a row lock.
     *
     * <p>Every path that reads a stock quantity does so in order to write one back, and the
     * availability check that guards the write has to see the value that will be overwritten.
     * Without this the check and the decrement are two separate reads and two concurrent
     * checkouts can both pass against the same figure, each of them issuing stock the other has
     * already accounted for.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.item.id = :itemId and s.store.id = :storeId")
    Optional<Stock> lockedByItemIdAndStoreId(@Param("itemId") UUID itemId, @Param("storeId") UUID storeId);

    List<Stock> findByStoreIdOrderByItemNameAsc(UUID storeId);

    @Query("select s from Stock s join fetch s.item where s.store.id = :storeId order by s.item.name asc")
    List<Stock> forStore(@Param("storeId") UUID storeId);
}
