package com.educationerp.asset;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Servicing an asset: what was done, by whom, and what it cost.
 *
 * <p>An asset in maintenance is off the floor, so the service moves the asset's status when a
 * job starts and puts it back when the job closes. The cost lands here rather than on the asset
 * because one asset can have many jobs over its life, and the total is their sum.
 */
@Entity
@Table(name = "asset_maintenance")
@Getter
@Setter
public class AssetMaintenance extends BaseEntity {

    public enum Type { PREVENTIVE, REPAIR, CALIBRATION, INSPECTION, UPGRADE }

    public enum Status { SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_type", nullable = false, length = 20)
    private Type type = Type.PREVENTIVE;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "vendor", length = 200)
    private String vendor;

    @Column(name = "cost", precision = 14, scale = 2)
    private BigDecimal cost;

    @Column(name = "performed_by", length = 200)
    private String performedBy;

    @Column(name = "scheduled_for")
    private LocalDate scheduledFor;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.SCHEDULED;
}
