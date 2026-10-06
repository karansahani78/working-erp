package com.educationerp.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    java.util.Optional<Purchase> findByPurchaseNumberIgnoreCase(String purchaseNumber);

    Page<Purchase> findByStatusOrderByOrderedOnDesc(Purchase.Status status, Pageable pageable);

    Page<Purchase> findAllByOrderByOrderedOnDesc(Pageable pageable);
}
