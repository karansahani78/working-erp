package com.educationerp.inventory;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Somewhere stock is kept: the main store, a department cupboard, a lab cabinet. */
@Entity
@Table(name = "stores",
        uniqueConstraints = @UniqueConstraint(name = "uk_stores_code", columnNames = "code"))
@Getter
@Setter
public class Store extends BaseEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campus_id")
    private com.educationerp.academic.Campus campus;

    /** Who is accountable for what is on these shelves. */
    @Column(name = "keeper_user_id")
    private UUID keeperUserId;

    @Column(name = "address", length = 400)
    private String address;

    @Column(name = "phone", length = 60)
    private String phone;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
