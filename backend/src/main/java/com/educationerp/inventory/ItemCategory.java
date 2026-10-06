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

/** A family of stock, so that "all stationery" can be asked for at once. */
@Entity
@Table(name = "item_categories",
        uniqueConstraints = @UniqueConstraint(name = "uk_item_categories_code", columnNames = "code"))
@Getter
@Setter
public class ItemCategory extends BaseEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ItemCategory parent;

    @Column(name = "description", length = 500)
    private String description;
}
