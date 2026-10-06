package com.educationerp.auth.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.status = 'REVOKED' where t.userId = :userId and t.status = 'ACTIVE'")
    int revokeAllForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("""
            update RefreshToken t set t.status = 'REVOKED'
            where t.userId = :userId and t.status = 'ACTIVE' and t.tokenHash <> :keepTokenHash
            """)
    int revokeAllForUserExcept(@Param("userId") UUID userId, @Param("keepTokenHash") String keepTokenHash);

    @Modifying
    @Query("update RefreshToken t set t.status = 'REVOKED' where t.userId = :userId and t.status = 'ACTIVE'")
    int revokeAllForUserIssuedBefore(@Param("userId") UUID userId, @Param("cutoff") Instant cutoff);

    @Modifying
    @Query("update RefreshToken t set t.status = 'EXPIRED' where t.expiresAt < :now and t.status = 'ACTIVE'")
    int expireStale(@Param("now") Instant now);

    List<RefreshToken> findByUserIdAndStatus(UUID userId, RefreshTokenStatus status);

    long countByUserIdAndStatus(UUID userId, RefreshTokenStatus status);
}
