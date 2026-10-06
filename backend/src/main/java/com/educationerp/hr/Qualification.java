package com.educationerp.hr;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A qualification an employee can hold, defined once and referenced many times. */
@Entity
@Table(name = "qualifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_qualifications_name", columnNames = "name"))
@Getter
@Setter
public class Qualification extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "level", length = 60)
    private String level;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}