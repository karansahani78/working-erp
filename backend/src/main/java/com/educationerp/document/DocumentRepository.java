package com.educationerp.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByDocumentNumberIgnoreCase(String documentNumber);

    /**
     * The library search, narrowed to what this caller may actually open.
     *
     * <p>Excludes soft-deleted documents: a deleted row is kept for the audit trail, not shown
     * to people looking for paperwork.
     *
     * <p>The access rule is part of the query rather than a filter applied afterwards. Filtering
     * the page once it had been fetched would leave the totals counting documents the caller
     * cannot see, which is both wrong and a quiet way of telling them how much paperwork exists.
     *
     * <p>Visibility is the same set {@code DocumentService.levelFor} decides: the owner or the
     * person who uploaded it sees everything, a live grant to the person or to one of their roles
     * lets them read it, and whoever may delete any document administers the whole library.
     */
    @Query("""
            select d from Document d
            where d.deletedAt is null
              and (:documentType is null or d.documentType = :documentType)
              and (:status is null or d.status = :status)
              and (:verificationStatus is null
                   or d.verificationStatus = :verificationStatus)
              and (:ownerUserId is null or d.owner.id = :ownerUserId)
              and (:relatedType is null or d.relatedType = :relatedType)
              and (:relatedId is null or d.relatedId = :relatedId)
              and (:term is null or lower(d.title) like :term
                   or lower(d.documentNumber) like :term
                   or lower(coalesce(d.description, '')) like :term)
              and (:administrator = true
                   or d.createdBy = :callerId
                   or d.owner.id = :callerId
                   or exists (select a from DocumentAccess a
                              where a.document = d
                                and (a.expiresAt is null or a.expiresAt > :now)
                                and ((a.principalUser is not null
                                      and a.principalUser.id = :callerId)
                                     or (a.principalRole is not null
                                         and a.principalRole in :callerRoles))))
            """)
    Page<Document> search(@Param("documentType") String documentType,
                          @Param("status") Document.Status status,
                          @Param("verificationStatus") Document.VerificationStatus verificationStatus,
                          @Param("ownerUserId") UUID ownerUserId,
                          @Param("relatedType") String relatedType,
                          @Param("relatedId") UUID relatedId,
                          @Param("term") String term,
                          @Param("callerId") UUID callerId,
                          @Param("callerRoles") java.util.Collection<com.educationerp.auth.role.Role> callerRoles,
                          @Param("administrator") boolean administrator,
                          @Param("now") java.time.Instant now,
                          Pageable pageable);

    /** Everything a record links to, for the panel beside a student or an employee. */
    @Query("""
            select d from Document d
            where d.deletedAt is null and d.relatedType = :relatedType and d.relatedId = :relatedId
            order by d.issuedOn desc nulls last, d.createdAt desc
            """)
    List<Document> findRelated(@Param("relatedType") String relatedType,
                               @Param("relatedId") UUID relatedId);

    /** What needs attention before it goes out of date. */
    @Query("""
            select d from Document d
            where d.deletedAt is null and d.expiresOn is not null
              and d.expiresOn between :from and :to
            order by d.expiresOn asc
            """)
    List<Document> findExpiringBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByVerificationStatus(Document.VerificationStatus status);

    long countByDeletedAtIsNull();

    /**
     * The document row locked for update.
     *
     * <p>Appending a version reads the current number and writes number plus one; without the
     * lock two uploads landing together would claim the same version and one file would be
     * orphaned.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :id")
    java.util.Optional<Document> findByIdForUpdate(@Param("id") java.util.UUID id);
}
