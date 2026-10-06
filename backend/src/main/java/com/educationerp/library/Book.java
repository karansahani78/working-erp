package com.educationerp.library;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A catalogue entry: the work, not the physical object.
 *
 * <p>Copies are counted from {@code book_copies}, never held as a number here, so the
 * availability a member is promised is read off the shelf rather than believed.
 */
@Entity
@Table(name = "books")
@Getter
@Setter
public class Book extends BaseEntity {

    @Column(name = "isbn", length = 20)
    private String isbn;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "edition", length = 60)
    private String edition;

    @Column(name = "publication_year")
    private Integer publicationYear;

    @Column(name = "language", length = 60)
    private String language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id")
    private Publisher publisher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private LibraryCategory category;

    /** What a librarian types in to find the shelf, usually a Dewey or local call number. */
    @Column(name = "call_number", length = 60)
    private String callNumber;

    @Column(name = "shelf_location", length = 80)
    private String shelfLocation;

    @Column(name = "cover_url", length = 400)
    private String coverUrl;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** A reference book is kept in the library and not lent out. */
    @Column(name = "is_reference", nullable = false)
    private boolean reference = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @jakarta.persistence.JoinTable(name = "books_authors",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "author_id"))
    private Set<Author> authors = new LinkedHashSet<>();
}
