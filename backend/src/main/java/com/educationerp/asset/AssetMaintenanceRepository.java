package com.educationerp.asset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssetMaintenanceRepository extends JpaRepository<AssetMaintenance, UUID> {

    Page<AssetMaintenance> findAllByOrderByScheduledForDesc(Pageable pageable);

    Page<AssetMaintenance> findByStatusOrderByScheduledForDesc(AssetMaintenance.Status status,
                                                              Pageable pageable);

    List<AssetMaintenance> findByAssetIdOrderByScheduledForDesc(UUID assetId);
}
