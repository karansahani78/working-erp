package com.educationerp.asset;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A family of assets, and the rules that come with the family.
 *
 * <p>The depreciation rate and useful life live on the category rather than on each asset
 * because they are a property of the kind of thing: a fleet of laptops all wear out on the same
 * schedule, and setting it per machine guarantees the register drifts apart.
 */
@Entity
@Table(name = "asset_categories",
        uniqueConstraints = @UniqueConstraint(name = "uk_asset_categories_code", columnNames = "code"))
@Getter
@Setter
public class AssetCategory extends BaseEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** A fraction of the value lost per year: 0.25 for a four-year schedule. */
    @Column(name = "depreciation_rate", precision = 6, scale = 4)
    private BigDecimal depreciationRate;

    @Column(name = "useful_life_years")
    private Integer usefulLifeYears;
}
