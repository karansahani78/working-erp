package com.educationerp.library;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** An author. A book may have several and an author several books. */
@Entity
@Table(name = "authors")
@Getter
@Setter
public class Author extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "biography", columnDefinition = "text")
    private String biography;
}
