package com.educationerp.library;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LibraryIssueRepository extends JpaRepository<LibraryIssue, UUID> {

    Optional<LibraryIssue> findFirstByCopyIdAndReturnedAtIsNull(UUID copyId);

    List<LibraryIssue> findByMemberIdAndReturnedAtIsNullOrderByDueAtAsc(UUID memberId);

    long countByMemberIdAndReturnedAtIsNull(UUID memberId);

    long countByReturnedAtIsNull();

    /** Everything out past its date, oldest first: the overdue list the librarian works down. */
    @Query("""
            select i from LibraryIssue i
            join fetch i.copy c
            join fetch c.book
            join fetch i.member
            where i.returnedAt is null and i.dueAt < :cutoff
            order by i.dueAt asc
            """)
    List<LibraryIssue> findOverdueBefore(@Param("cutoff") Instant cutoff);

    @Query("""
            select i from LibraryIssue i
            join fetch i.copy c
            join fetch c.book
            where i.member.id = :memberId
            order by case when i.returnedAt is null then 0 else 1 end, i.dueAt, i.issuedAt desc
            """)
    Page<LibraryIssue> findHistoryForMember(@Param("memberId") UUID memberId, Pageable pageable);
}
