package com.educationerp.communication;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoticeRepository extends JpaRepository<Notice, UUID> {

    Page<Notice> findByStatusOrderByPublishedAtDesc(Notice.Status status, Pageable pageable);

    Page<Notice> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<Notice> findFirstByStatusOrderByPublishedAtDesc(Notice.Status status);

    /**
     * The published notices one portal audience should see: the ones addressed to everybody
     * plus the ones addressed to that audience, and only while they have not expired. A
     * notice that was unpublished or has run out is not shown at all.
     */
    @Query("""
            select n from Notice n
            where n.status = :status
              and (n.audience = :everyone or n.audience = :audience)
              and (n.expiresAt is null or n.expiresAt > :now)
            order by n.publishedAt desc, n.createdAt desc
            """)
    List<Notice> visibleTo(@Param("status") Notice.Status status,
                           @Param("everyone") Notice.Audience everyone,
                           @Param("audience") Notice.Audience audience,
                           @Param("now") Instant now);
}
