package com.educationerp.asset;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The requests and responses the asset register accepts and returns. */
public final class AssetDtos {

    private AssetDtos() {
    }

    public record CategoryRequest(
            @NotBlank String code,
            @NotBlank String name,
            @DecimalMin(value = "0.0", inclusive = false) BigDecimal depreciationRate,
            @PositiveOrZero Integer usefulLifeYears) {
    }

    public record CategoryRow(UUID id, String code, String name, BigDecimal depreciationRate,
                              Integer usefulLifeYears) {
    }

    public record AssetRequest(
            @NotBlank String name,
            String description,
            UUID categoryId,
            String serialNumber,
            String brand,
            String model,
            LocalDate purchaseDate,
            @DecimalMin("0") BigDecimal purchaseCost,
            LocalDate warrantyExpiry,
            String location,
            UUID departmentId,
            Asset.Condition conditionStatus,
            String notes) {
    }

    public record AssetRow(UUID id, String assetNumber, String name, String description,
                           UUID categoryId, String categoryName, String serialNumber, String brand,
                           String model, LocalDate purchaseDate, BigDecimal purchaseCost,
                           BigDecimal depreciationRate, BigDecimal currentValue,
                           LocalDate warrantyExpiry, String location, UUID departmentId,
                           String departmentName, Asset.Status status, Asset.Condition conditionStatus,
                           UUID holderUserId, UUID holderEmployeeId, UUID holderStudentId,
                           String holderName, Instant assignedAt,
                           Instant lostAt, String lostReason, Instant disposedAt,
                           Asset.Disposal disposalMethod, String disposalNotes,
                           BigDecimal disposalValue, String notes) {
    }

    public record AssignmentRequest(
            @NotNull AssetAssignment.HolderType holderType,
            UUID holderUserId,
            UUID holderEmployeeId,
            UUID holderStudentId,
            @NotBlank String holderName,
            String notes) {
    }

    public record ReturnRequest(
            Asset.Condition conditionIn,
            String notes) {
    }

    /**
     * Reporting an asset missing. The reason is required because "it is gone" without a word
     * leaves whoever searches for it with nothing to go on.
     */
    public record MarkLostRequest(
            @NotBlank String reason) {
    }

    /** Finding a lost asset again: where it turned up, and what condition it is in. */
    public record FoundRequest(
            String location,
            Asset.Condition conditionStatus) {
    }

    /** Writing an asset off the register, with how it was let go of and for how much. */
    public record DisposeRequest(
            @NotNull Asset.Disposal method,
            @DecimalMin("0") BigDecimal value,
            String notes) {
    }

    public record AssignmentRow(UUID id, UUID assetId, String assetNumber, String assetName,
                                AssetAssignment.HolderType holderType, UUID holderUserId,
                                UUID holderEmployeeId, UUID holderStudentId, String holderName,
                                Instant assignedAt, UUID assignedBy, Instant returnedAt,
                                Asset.Condition conditionOut, Asset.Condition conditionIn,
                                String notes, boolean open) {
    }

    public record MaintenanceRequest(
            @NotNull AssetMaintenance.Type type,
            String description,
            String vendor,
            @DecimalMin("0") BigDecimal cost,
            String performedBy,
            LocalDate scheduledFor) {
    }

    public record StartMaintenanceRequest(String performedBy) {
    }

    public record CompleteMaintenanceRequest(
            @PositiveOrZero BigDecimal cost,
            @NotBlank String performedBy,
            String notes) {
    }

    public record MaintenanceRow(UUID id, UUID assetId, String assetNumber, String assetName,
                                 AssetMaintenance.Type type, String description, String vendor,
                                 BigDecimal cost, String performedBy, LocalDate scheduledFor,
                                 Instant startedAt, Instant completedAt,
                                 AssetMaintenance.Status status) {
    }

    /** Value lost to age so far, straight-line, and the year the thing is due for attention. */
    public record DepreciationRow(UUID assetId, String assetNumber, String assetName,
                                   BigDecimal purchaseCost, BigDecimal depreciationRate,
                                   Integer usefulLifeYears, BigDecimal annualDepreciation,
                                   BigDecimal currentValue, LocalDate warrantyExpiry,
                                   Asset.Status status) {
    }

    public record AssetOverview(long totalAssets, long available, long assigned, long inMaintenance,
                                long lost, long disposed, BigDecimal purchaseCost,
                                BigDecimal bookValue, long warrantyExpiringSoon) {
    }

    public record AssetDetail(AssetRow asset, List<AssignmentRow> history,
                              List<MaintenanceRow> maintenance) {
    }
}
