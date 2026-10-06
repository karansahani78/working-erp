package com.educationerp.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The requests and responses the inventory module accepts and returns. */
public final class InventoryDtos {

    private InventoryDtos() {
    }

    // ---------------------------------------------------------------- catalogue

    public record CategoryRequest(
            @NotBlank String code,
            @NotBlank String name,
            UUID parentId,
            String description) {
    }

    public record CategoryRow(UUID id, String code, String name, UUID parentId, String parentName,
                              String description) {
    }

    public record StoreRequest(
            @NotBlank String code,
            @NotBlank String name,
            UUID campusId,
            UUID keeperUserId,
            String address,
            String phone) {
    }

    public record StoreRow(UUID id, String code, String name, UUID campusId, String campusName,
                           UUID keeperUserId, String keeperName, String address, String phone,
                           boolean active) {
    }

    public record ItemRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            UUID categoryId,
            @NotBlank String unit,
            @NotNull @DecimalMin("0") BigDecimal reorderLevel,
            @NotNull @DecimalMin("0") BigDecimal reorderQuantity,
            boolean batchTracked,
            boolean expiryTracked,
            boolean active) {
    }

    public record ItemRow(UUID id, String code, String name, String description, UUID categoryId,
                          String categoryName, String unit, BigDecimal reorderLevel,
                          BigDecimal reorderQuantity, boolean batchTracked, boolean expiryTracked,
                          boolean active, BigDecimal totalOnHand, boolean needsReorder) {
    }

    // ------------------------------------------------------------------- stock

    public record StockRow(UUID itemId, String itemCode, String itemName, String unit,
                           UUID storeId, String storeName, BigDecimal quantity,
                           BigDecimal averageCost, BigDecimal stockValue,
                           Instant lastMovementAt) {
    }

    public record MovementRow(UUID id, UUID itemId, String itemCode, String itemName,
                              UUID storeId, String storeName, StockMovement.Type type,
                              BigDecimal quantity, BigDecimal signedQuantity, BigDecimal balanceAfter,
                              BigDecimal unitCost, String referenceType, UUID referenceId,
                              String reason, Instant movedAt, UUID movedBy) {
    }

    /** A stock take: what the count found against what the system believed. */
    public record AdjustmentRequest(
            @NotNull UUID storeId,
            @NotNull UUID itemId,
            @NotNull BigDecimal countedQuantity,
            String reason) {
    }

    public record AdjustmentResult(UUID itemId, String itemCode, String itemName,
                                   BigDecimal previousQuantity, BigDecimal countedQuantity,
                                   BigDecimal difference) {
    }

    // --------------------------------------------------------------- purchases

    public record PurchaseLineRequest(
            @NotNull UUID itemId,
            @NotNull @Positive BigDecimal quantityOrdered,
            @NotNull @DecimalMin("0") BigDecimal unitCost) {
    }

    public record PurchaseRequest(
            @NotBlank String supplierName,
            String supplierContact,
            @NotNull UUID storeId,
            LocalDate expectedOn,
            @NotNull @DecimalMin("0") BigDecimal taxAmount,
            @NotNull @DecimalMin("0") BigDecimal otherCosts,
            String notes,
            @NotNull List<@NotNull PurchaseLineRequest> items) {
    }

    public record PurchaseLineRow(UUID id, UUID itemId, String itemCode, String itemName,
                                  String unit, BigDecimal quantityOrdered,
                                  BigDecimal quantityReceived, BigDecimal outstanding,
                                  BigDecimal unitCost, BigDecimal lineTotal, boolean fullyReceived) {
    }

    public record PurchaseRow(UUID id, String purchaseNumber, String supplierName,
                              String supplierContact, UUID storeId, String storeName,
                              Purchase.Status status, LocalDate orderedOn, LocalDate expectedOn,
                              LocalDate receivedOn, BigDecimal subtotal, BigDecimal taxAmount,
                              BigDecimal otherCosts, BigDecimal totalAmount, String notes,
                              List<PurchaseLineRow> items) {
    }

    /**
     * Taking delivery of some of an order.
     *
     * <p>Quantities are per line and the ones left out stay outstanding, so a supplier who
     * sends two cartons of a hundred does not have to be guessed at.
     */
    public record ReceiveRequest(
            @NotNull UUID storeId,
            LocalDate receivedOn,
            @NotNull List<@NotNull ReceiveLine> lines) {
    }

    public record ReceiveLine(
            @NotNull UUID purchaseItemId,
            @NotNull @Positive BigDecimal quantityReceived) {
    }

    // ------------------------------------------------------------------ issues

    public record IssueRequest(
            @NotNull UUID itemId,
            @NotNull UUID storeId,
            @NotNull @Positive BigDecimal quantity,
            @NotNull StockIssue.RecipientType issuedToType,
            UUID issuedToId,
            @NotBlank String issuedToName,
            String reason) {
    }

    public record IssueRow(UUID id, String issueNumber, UUID itemId, String itemCode,
                           String itemName, String unit, UUID storeId, String storeName,
                           BigDecimal quantity, StockIssue.RecipientType issuedToType,
                           UUID issuedToId, String issuedToName, String reason,
                           StockIssue.Status status, Instant issuedAt, Instant returnedAt) {
    }

    public record ReturnRequest(
            @NotNull UUID issueId,
            BigDecimal quantityReturned,
            String reason) {
    }

    // --------------------------------------------------------------- transfers

    public record TransferRequest(
            @NotNull UUID itemId,
            @NotNull UUID fromStoreId,
            @NotNull UUID toStoreId,
            @NotNull @Positive BigDecimal quantity,
            String reason) {
    }

    public record TransferRow(UUID id, String transferNumber, UUID itemId, String itemCode,
                              String itemName, UUID fromStoreId, String fromStoreName,
                              UUID toStoreId, String toStoreName, BigDecimal quantity,
                              StockTransfer.Status status, String reason, Instant requestedAt,
                              Instant dispatchedAt, Instant receivedAt) {
    }

    // ---------------------------------------------------------------- reporting

    public record LowStockRow(UUID itemId, String itemCode, String itemName, String unit,
                              BigDecimal totalOnHand, BigDecimal reorderLevel,
                              BigDecimal suggestedOrderQuantity) {
    }

    public record InventoryOverview(long itemCount, long categoryCount, long storeCount,
                                    long purchaseCount, long openIssueCount,
                                    long inTransitCount, long lowStockCount,
                                    BigDecimal stockValue) {
    }
}
