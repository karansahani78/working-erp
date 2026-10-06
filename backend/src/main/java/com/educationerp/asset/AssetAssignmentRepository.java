package com.educationerp.asset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetAssignmentRepository extends JpaRepository<AssetAssignment, UUID> {

    /** An asset can only be out to one holder at a time, so there is at most one open row. */
    Optional<AssetAssignment> findByAssetIdAndReturnedAtIsNull(UUID assetId);

    @Query("""
            select h from AssetAssignment h
            where h.asset.id = :assetId
            order by h.assignedAt desc
            """)
    List<AssetAssignment> historyFor(@Param("assetId") UUID assetId);

    /**
     * Everybody of one kind is holding something.
     *
     * <p>This is deliberately a separate query rather than a null-guarded form of the ones
     * below: listing three different id columns in one {@code in} clause cannot be typed by
     * the database once any of them is empty.
     */
    @Query("""
            select h from AssetAssignment h
            where h.holderType = :type and h.returnedAt is null
            """)
    Page<AssetAssignment> findOpenOfType(@Param("type") AssetAssignment.HolderType type,
                                         Pageable pageable);

    @Query("""
            select h from AssetAssignment h
            where h.holderType = :type
              and h.holderUser.id = :holderId
              and h.returnedAt is null
            """)
    Page<AssetAssignment> findOpenHeldByHolderUser(@Param("type") AssetAssignment.HolderType type,
                                                   @Param("holderId") UUID holderId,
                                                   Pageable pageable);

    @Query("""
            select h from AssetAssignment h
            where h.holderType = :type
              and h.holderEmployee.id = :holderId
              and h.returnedAt is null
            """)
    Page<AssetAssignment> findOpenHeldByHolderEmployee(@Param("type") AssetAssignment.HolderType type,
                                                       @Param("holderId") UUID holderId,
                                                       Pageable pageable);

    @Query("""
            select h from AssetAssignment h
            where h.holderType = :type
              and h.holderStudent.id = :holderId
              and h.returnedAt is null
            """)
    Page<AssetAssignment> findOpenHeldByHolderStudent(@Param("type") AssetAssignment.HolderType type,
                                                      @Param("holderId") UUID holderId,
                                                      Pageable pageable);
}
