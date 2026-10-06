package com.educationerp.library;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BookRepository extends JpaRepository<Book, UUID> {

    Optional<Book> findByIsbn(String isbn);
    Optional<Book> findFirstByTitleIgnoreCase(String title);

    /**
     * The catalogue search: by shelf, by reference-only, and by anything a member would
     * plausibly type -- title, ISBN, call number, or an author's name.
     */
    @Query("""
            select distinct b from Book b
            left join fetch b.category
            left join fetch b.publisher
            left join fetch b.authors
            where (:categoryId is null or b.category.id = :categoryId)
              and (:referenceOnly = false or b.reference = true)
              and (:term is null
                   or lower(b.title) like :term
                   or lower(coalesce(b.isbn, '')) like :term
                   or lower(coalesce(b.callNumber, '')) like :term
                   or exists (select 1 from Author a where a in elements(b.authors)
                              and lower(a.name) like :term))
            """)
    Page<Book> search(@Param("categoryId") UUID categoryId,
                      @Param("referenceOnly") boolean referenceOnly,
                      @Param("term") String term,
                      Pageable pageable);

    /** Copies on the shelf and lendable right now. Counted, not carried as a column. */
    @Query("""
            select count(c) from BookCopy c
            where c.book.id = :bookId and c.status = :available and c.book.reference = false
            """)
    long countAvailableCopies(@Param("bookId") UUID bookId,
                              @Param("available") BookCopy.Status available);

    @Query("select count(c) from BookCopy c where c.book.id = :bookId")
    long countCopies(@Param("bookId") UUID bookId);
}
