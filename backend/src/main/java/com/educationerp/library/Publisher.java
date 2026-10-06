package com.educationerp.library;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Who published a book. Kept separate so a catalogue can be maintained without a book. */
@Entity
@Table(name = "publishers",
        uniqueConstraints = @UniqueConstraint(name = "uk_publishers_name", columnNames = "name"))
@Getter
@Setter
public class Publisher extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "address", length = 400)
    private String address;

    @Column(name = "contact_email", length = 180)
    private String contactEmail;

    @Column(name = "contact_phone", length = 60)
    private String contactPhone;
}
