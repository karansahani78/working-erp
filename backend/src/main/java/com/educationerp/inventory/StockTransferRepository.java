package com.educationerp.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    java.util.Optional<StockTransfer> findByTransferNumberIgnoreCase(String transferNumber);

    Page<StockTransfer> findByStatusOrderByRequestedAtDesc(StockTransfer.Status status,
                                                           Pageable pageable);

    Page<StockTransfer> findAllByOrderByRequestedAtDesc(Pageable pageable);

    long countByStatus(StockTransfer.Status status);
}
