package com.educationerp.library;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LibraryReservationRepository extends JpaRepository<LibraryReservation, UUID> {

    Optional<LibraryReservation> findFirstByBookIdAndMemberIdAndStatus(
            UUID bookId, UUID memberId, LibraryReservation.Status status);

    /** The waiting list for a title, in the order people asked. */
    @Query("""
            select r from LibraryReservation r
            join fetch r.member
            where r.book.id = :bookId and r.status = :status
            order by r.queuePosition asc, r.reservedAt asc
            """)
    List<LibraryReservation> findQueueForBook(@Param("bookId") UUID bookId,
                                              @Param("status") LibraryReservation.Status status);

    long countByStatus(LibraryReservation.Status status);
}
