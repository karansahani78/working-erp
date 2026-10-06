package com.educationerp.library;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookCopyRepository extends JpaRepository<BookCopy, UUID> {

    Optional<BookCopy> findByBarcodeIgnoreCase(String barcode);

    long countByBookIdAndStatus(UUID bookId, BookCopy.Status status);

    long countByStatus(BookCopy.Status status);

    List<BookCopy> findByBookIdOrderByBarcodeAsc(UUID bookId);

    @Query("""
            select c from BookCopy c join fetch c.book b
            where c.status = :status
            order by b.title asc, c.barcode asc
            """)
    Page<BookCopy> byStatus(@Param("status") BookCopy.Status status, Pageable pageable);
}
