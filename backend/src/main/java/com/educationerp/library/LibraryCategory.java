package com.educationerp.library;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A shelf classification: fiction, reference, textbooks, journals and so on. */
@Entity
@Table(name = "library_categories",
        uniqueConstraints = @UniqueConstraint(name = "uk_library_categories_code", columnNames = "code"))
@Getter
@Setter
public class LibraryCategory extends BaseEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;
}
