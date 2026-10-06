package com.educationerp.inventory;

import com.educationerp.academic.Campus;
import com.educationerp.academic.CampusRepository;
import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.api.PageResponse;
import com.educationerp.common.error.AppException;
import com.educationerp.common.numbering.DocumentSequence;
import com.educationerp.common.numbering.SequenceNumberGenerator;
import com.educationerp.institution.InstitutionService;
import com.educationerp.institution.ModuleKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The stockroom.
 *
 * <p>Two rules hold everything here together. First, quantity is never set directly: it moves
 * only through {@link #record}, which writes the ledger row and moves the balance together, so
 * the two can never drift apart. Second, stock may go negative only by saying why -- a
 * correction with a reason is allowed through so that a count which disagrees with the screen
 * can be fixed, while an issue with no stock is refused.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class InventoryService {

    private static final String PURCHASE = "PURCHASE";

    private final ItemCategoryRepository categories;
    private final ItemRepository items;
    private final StoreRepository stores;
    private final StockRepository stock;
    private final StockMovementRepository movements;
    private final PurchaseRepository purchases;
    private final PurchaseItemRepository purchaseItems;
    private final StockIssueRepository issues;
    private final StockTransferRepository transfers;
    private final CampusRepository campuses;
    private final com.educationerp.auth.user.UserRepository users;
    private final SequenceNumberGenerator numbers;
    private final InstitutionService institutions;
    private final AuthorizationChecker auth;
    private final AuditService audit;

    // ---------------------------------------------------------------- catalogue

    public InventoryDtos.CategoryRow createCategory(InventoryDtos.CategoryRequest request) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_MANAGE");
        requireCategoryCodeFree(request.code(), null);
        ItemCategory category = new ItemCategory();
        category.setCode(request.code().trim());
        category.setName(request.name().trim());
        category.setDescription(request.description());
        if (request.parentId() != null) {
            category.setParent(category(request.parentId()));
        }
        return categoryRow(categories.save(category));
    }

    public InventoryDtos.CategoryRow updateCategory(UUID id, InventoryDtos.CategoryRequest request) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_MANAGE");
        ItemCategory category = category(id);
        requireCategoryCodeFree(request.code(), id);
        category.setCode(request.code().trim());
        category.setName(request.name().trim());
        category.setDescription(request.description());
        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw AppException.rule("A category cannot be its own parent.");
            }
            category.setParent(category(request.parentId()));
        } else {
            category.setParent(null);
        }
        return categoryRow(category);
    }

    private void requireCategoryCodeFree(String code, UUID self) {
        categories.findByCodeIgnoreCase(code.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(self)) {
                throw AppException.duplicate("An item category with code " + code.trim() + " already exists.");
            }
        });
    }

    public InventoryDtos.StoreRow createStore(InventoryDtos.StoreRequest request) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_MANAGE");
        stores.findByCodeIgnoreCase(request.code().trim()).ifPresent(existing -> {
            throw AppException.duplicate("A store with code " + request.code().trim() + " already exists.");
        });
        Store store = new Store();
        apply(store, request);
        return storeRow(stores.save(store));
    }

    public InventoryDtos.StoreRow updateStore(UUID id, InventoryDtos.StoreRequest request) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_MANAGE");
        Store store = store(id);
        stores.findByCodeIgnoreCase(request.code().trim()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw AppException.duplicate("A store with code " + request.code().trim() + " already exists.");
            }
        });
        apply(store, request);
        return storeRow(store);
    }

    private void apply(Store store, InventoryDtos.StoreRequest request) {
        store.setCode(request.code().trim());
        store.setName(request.name().trim());
        store.setAddress(request.address());
        store.setPhone(request.phone());
        store.setKeeperUserId(request.keeperUserId());
        store.setCampus(request.campusId() == null ? null
                : campuses.findById(request.campusId())
                .orElseThrow(() -> AppException.notFound("Campus")));
    }

    public InventoryDtos.ItemRow createItem(InventoryDtos.ItemRequest request) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_MANAGE");
        requireItemCodeFree(request.code(), null);
        Item item = new Item();
        apply(item, request);
        return itemRow(items.save(item), items.totalOnHand(item.getId()));
    }

    public InventoryDtos.ItemRow updateItem(UUID id, InventoryDtos.ItemRequest request) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_MANAGE");
        Item item = item(id);
        requireItemCodeFree(request.code(), id);
        apply(item, request);
        return itemRow(item, items.totalOnHand(id));
    }

    private void requireItemCodeFree(String code, UUID self) {
        items.findByCodeIgnoreCase(code.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(self)) {
                throw AppException.duplicate("An item with code " + code.trim() + " already exists.");
            }
        });
    }

    private void apply(Item item, InventoryDtos.ItemRequest request) {
        item.setCode(request.code().trim());
        item.setName(request.name().trim());
        item.setDescription(request.description());
        item.setUnit(request.unit().trim().toUpperCase());
        item.setReorderLevel(request.reorderLevel());
        item.setReorderQuantity(request.reorderQuantity());
        item.setBatchTracked(request.batchTracked());
        item.setExpiryTracked(request.expiryTracked());
        item.setActive(request.active());
        item.setCategory(request.categoryId() == null ? null
                : category(request.categoryId()));
    }

    public PageResponse<InventoryDtos.ItemRow> searchItems(UUID categoryId, Boolean active,
                                                           String term, Pageable pageable) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_READ");
        String like = term == null || term.isBlank() ? null
                : "%" + term.trim().toLowerCase() + "%";
        Page<Item> page = items.search(categoryId, active == null || active, like, pageable);
        return PageResponse.from(page, item -> itemRow(item, items.totalOnHand(item.getId())));
    }

    public List<InventoryDtos.CategoryRow> listCategories() {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_READ");
        return categories.findAll().stream().map(this::categoryRow).toList();
    }

    public PageResponse<InventoryDtos.StoreRow> listStores(Pageable pageable) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_READ");
        return PageResponse.from(stores.findByActiveTrueOrderByNameAsc(pageable), this::storeRow);
    }

    // ------------------------------------------------------------------- stock

    public List<InventoryDtos.StockRow> stockForStore(UUID storeId) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_READ");
        store(storeId);
        return stock.forStore(storeId).stream().map(this::stockRow).toList();
    }

    public PageResponse<InventoryDtos.MovementRow> ledgerForStore(UUID storeId, Pageable pageable) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_READ");
        store(storeId);
        return PageResponse.from(movements.findForStore(storeId, pageable), this::movementRow);
    }

    public PageResponse<InventoryDtos.MovementRow> ledgerForItem(UUID itemId, Pageable pageable) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_READ");
        item(itemId);
        return PageResponse.from(movements.findForItem(itemId, pageable), this::movementRow);
    }

    /**
     * Set the stock to a counted figure.
     *
     * <p>The difference, not the counted figure, is what goes into the ledger, so the movement
     * says "eight short" rather than repeating the number that was already there.
     */
    /**
     * Every operation below reads a quantity in order to write one back. That is serialised by
     * taking the stock row itself under a lock in {@link #stockRow(Item, Store)}, so these run
     * at the default isolation: asking for SERIALIZABLE as well did not close any gap the lock
     * leaves open, it only turned the loser of the lock into a 500 — PostgreSQL reports
     * "could not serialize access due to concurrent update" — where the shelf should be refusing
     * with a sentence about how much is left.
     */
    @Transactional
    public InventoryDtos.AdjustmentResult adjust(InventoryDtos.AdjustmentRequest request) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_TRANSACT");
        Item item = item(request.itemId());
        store(request.storeId());
        Stock row = stock.findByItemIdAndStoreId(request.itemId(), request.storeId())
                .orElseGet(() -> {
                    Stock fresh = new Stock();
                    fresh.setItem(item);
                    fresh.setStore(store(request.storeId()));
                    return fresh;
                });
        BigDecimal previous = row.getQuantity() == null ? BigDecimal.ZERO : row.getQuantity();
        BigDecimal counted = request.countedQuantity();
        BigDecimal difference = counted.subtract(previous);
        if (difference.signum() == 0) {
            return new InventoryDtos.AdjustmentResult(item.getId(), item.getCode(), item.getName(),
                    previous, counted, difference);
        }
        record(item, row, difference, StockMovement.Type.ADJUSTMENT,
                request.reason() == null || request.reason().isBlank()
                        ? "Stock take" : request.reason(), null, null, null);
        return new InventoryDtos.AdjustmentResult(item.getId(), item.getCode(), item.getName(),
                previous, counted, difference);
    }

    // --------------------------------------------------------------- purchases

    public InventoryDtos.PurchaseRow createPurchase(InventoryDtos.PurchaseRequest request) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_MANAGE");
        Purchase purchase = new Purchase();
        purchase.setPurchaseNumber(numbers.next(DocumentSequence.Kind.PURCHASE));
        purchase.setSupplierName(request.supplierName().trim());
        purchase.setSupplierContact(request.supplierContact());
        purchase.setStore(store(request.storeId()));
        purchase.setExpectedOn(request.expectedOn());
        purchase.setTaxAmount(request.taxAmount());
        purchase.setOtherCosts(request.otherCosts());
        purchase.setNotes(request.notes());
        purchase.setStatus(Purchase.Status.DRAFT);
        for (InventoryDtos.PurchaseLineRequest line : request.items()) {
            Item item = item(line.itemId());
            PurchaseItem purchaseItem = new PurchaseItem();
            purchaseItem.setPurchase(purchase);
            purchaseItem.setItem(item);
            purchaseItem.setQuantityOrdered(line.quantityOrdered());
            purchaseItem.priceAt(line.unitCost());
            purchase.getItems().add(purchaseItem);
        }
        purchase.recalculateTotals();
        InventoryDtos.PurchaseRow saved = purchaseRow(purchases.save(purchase));
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .module("INVENTORY")
                .entityType("Purchase")
                .entityId(purchase.getId().toString())
                .summary("Purchase " + purchase.getPurchaseNumber() + " drafted for "
                        + purchase.getSupplierName())
                .build());
        return saved;
    }

    /** Send the order to the supplier. */
    public InventoryDtos.PurchaseRow placeOrder(UUID id) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_MANAGE");
        Purchase purchase = requirePurchase(id);
        if (purchase.getStatus() != Purchase.Status.DRAFT) {
            throw AppException.duplicate("Only a draft purchase can be sent to the supplier.");
        }
        if (purchase.getItems().isEmpty()) {
            throw AppException.rule("Add at least one item before sending the order.");
        }
        purchase.setStatus(Purchase.Status.ORDERED);
        purchase.setOrderedOn(LocalDate.now());
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .module("INVENTORY")
                .entityType("Purchase")
                .entityId(purchase.getId().toString())
                .summary("Purchase " + purchase.getPurchaseNumber() + " sent to "
                        + purchase.getSupplierName())
                .build());
        return purchaseRow(purchase);
    }

    public InventoryDtos.PurchaseRow cancelPurchase(UUID id) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_MANAGE");
        Purchase purchase = requirePurchase(id);
        if (purchase.getStatus() == Purchase.Status.RECEIVED) {
            throw AppException.duplicate("A received purchase cannot be cancelled.");
        }
        purchase.setStatus(Purchase.Status.CANCELLED);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .module("INVENTORY")
                .entityType("Purchase")
                .entityId(purchase.getId().toString())
                .summary("Purchase " + purchase.getPurchaseNumber() + " cancelled")
                .build());
        return purchaseRow(purchase);
    }

    /**
     * Take delivery.
     *
     * <p>Only what actually arrived is added to the shelf and only up to what was outstanding,
     * so receiving twice cannot manufacture stock.
     */
    @Transactional
    public InventoryDtos.PurchaseRow receive(UUID id, InventoryDtos.ReceiveRequest request) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_TRANSACT");
        Purchase purchase = requirePurchase(id);
        if (purchase.getStatus() != Purchase.Status.ORDERED
                && purchase.getStatus() != Purchase.Status.PARTIAL) {
            throw AppException.duplicate("Only an order that has been sent can be received against.");
        }
        LocalDate receivedOn = request.receivedOn() == null ? LocalDate.now() : request.receivedOn();
        Store into = purchase.getStore();
        for (InventoryDtos.ReceiveLine line : request.lines()) {
            PurchaseItem purchaseItem = purchaseItem(line.purchaseItemId(), purchase);
            BigDecimal outstanding = purchaseItem.outstanding();
            BigDecimal accepting = line.quantityReceived().min(outstanding);
            if (accepting.signum() <= 0) {
                continue;
            }
            purchaseItem.setQuantityReceived(purchaseItem.getQuantityReceived().add(accepting));
            purchaseItem.setReceivedAt(Instant.now());
            Item item = purchaseItem.getItem();
            Stock row = stockRow(item, into);
            // A weighted average, so the shelf is valued sensibly after a price change.
            // This has to be read against the quantity on hand *before* the movement
            // lands, otherwise the incoming stock is counted in both halves of the sum
            // and every receipt halves the average cost.
            BigDecimal averageCost = averageCost(row, accepting, purchaseItem.getUnitCost());
            record(item, row, accepting, StockMovement.Type.PURCHASE,
                    "Purchase " + purchase.getPurchaseNumber(), PURCHASE, purchase.getId(),
                    purchaseItem.getUnitCost());
            row.setAverageCost(averageCost);
        }
        boolean complete = purchase.getItems().stream().allMatch(PurchaseItem::isFullyReceived);
        purchase.setStatus(complete ? Purchase.Status.RECEIVED : Purchase.Status.PARTIAL);
        if (complete) {
            purchase.setReceivedOn(receivedOn);
        }
        purchase.recalculateTotals();
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .module("INVENTORY")
                .entityType("Purchase")
                .entityId(purchase.getId().toString())
                .summary("Goods received against purchase " + purchase.getPurchaseNumber())
                .build());
        return purchaseRow(purchase);
    }

    private BigDecimal averageCost(Stock row, BigDecimal received, BigDecimal unitCost) {
        BigDecimal current = row.getQuantity() == null ? BigDecimal.ZERO : row.getQuantity();
        BigDecimal existingCost = row.getAverageCost() == null ? BigDecimal.ZERO : row.getAverageCost();
        BigDecimal totalValue = existingCost.multiply(current).add(unitCost.multiply(received));
        BigDecimal totalQuantity = current.add(received);
        if (totalQuantity.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return totalValue.divide(totalQuantity, 2, java.math.RoundingMode.HALF_UP);
    }

    public InventoryDtos.PurchaseRow purchase(UUID id) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_READ");
        return purchaseRow(requirePurchase(id));
    }

    public PageResponse<InventoryDtos.PurchaseRow> listPurchases(Purchase.Status status,
                                                               Pageable pageable) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_READ");
        Page<Purchase> page = status == null
                ? purchases.findAllByOrderByOrderedOnDesc(pageable)
                : purchases.findByStatusOrderByOrderedOnDesc(status, pageable);
        return PageResponse.from(page, this::purchaseRow);
    }

    // ------------------------------------------------------------------ issues

    @Transactional
    public InventoryDtos.IssueRow issue(InventoryDtos.IssueRequest request) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_TRANSACT");
        Item item = item(request.itemId());
        Store from = store(request.storeId());
        BigDecimal quantity = request.quantity();
        Stock row = stockRow(item, from);
        BigDecimal available = row.getQuantity() == null ? BigDecimal.ZERO : row.getQuantity();
        if (available.compareTo(quantity) < 0) {
            throw AppException.rule("Only " + available.stripTrailingZeros().toPlainString()
                    + " " + item.getUnit() + " of " + item.getName() + " are in " + from.getName() + ".");
        }
        StockIssue issue = new StockIssue();
        issue.setIssueNumber(numbers.next(DocumentSequence.Kind.STOCK_ISSUE));
        issue.setItem(item);
        issue.setStore(from);
        issue.setQuantity(quantity);
        issue.setIssuedToType(request.issuedToType());
        issue.setIssuedToId(request.issuedToId());
        issue.setIssuedToName(request.issuedToName().trim());
        issue.setReason(request.reason());
        issue.setStatus(StockIssue.Status.ISSUED);
        issues.save(issue);
        record(item, row, quantity, StockMovement.Type.ISSUE,
                "Issued to " + issue.getIssuedToName(), "ISSUE", issue.getId(), row.getAverageCost());
        return issueRow(issue);
    }

    @Transactional
    public InventoryDtos.IssueRow giveBack(InventoryDtos.ReturnRequest request) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_TRANSACT");
        StockIssue issue = issue(request.issueId());
        if (issue.getStatus() != StockIssue.Status.ISSUED) {
            throw AppException.duplicate("That issue note is already closed.");
        }
        // A null or full quantity means everything went back.
        BigDecimal returning = request.quantityReturned() == null
                ? issue.getQuantity() : request.quantityReturned();
        BigDecimal outstanding = issue.getQuantity().subtract(returning);
        if (outstanding.signum() < 0) {
            throw AppException.rule("Only " + issue.getQuantity().stripTrailingZeros().toPlainString()
                    + " " + issue.getItem().getUnit() + " were issued on that note.");
        }
        Item item = issue.getItem();
        Stock row = stockRow(item, issue.getStore());
        record(item, row, returning, StockMovement.Type.RETURN,
                request.reason() == null || request.reason().isBlank()
                        ? "Returned by " + issue.getIssuedToName() : request.reason(),
                "ISSUE", issue.getId(), row.getAverageCost());
        issue.setStatus(outstanding.signum() == 0 ? StockIssue.Status.RETURNED : StockIssue.Status.ISSUED);
        if (outstanding.signum() == 0) {
            issue.setReturnedAt(Instant.now());
        }
        return issueRow(issue);
    }

    public PageResponse<InventoryDtos.IssueRow> listIssues(StockIssue.Status status,
                                                           Pageable pageable) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_READ");
        Page<StockIssue> page = status == null
                ? issues.findAllByOrderByIssuedAtDesc(pageable)
                : issues.findByStatusOrderByIssuedAtDesc(status, pageable);
        return PageResponse.from(page, this::issueRow);
    }

    // --------------------------------------------------------------- transfers

    public InventoryDtos.TransferRow requestTransfer(InventoryDtos.TransferRequest request) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_TRANSACT");
        if (request.fromStoreId().equals(request.toStoreId())) {
            throw AppException.rule("Choose two different stores.");
        }
        Item item = item(request.itemId());
        store(request.fromStoreId());
        store(request.toStoreId());
        StockTransfer transfer = new StockTransfer();
        transfer.setTransferNumber(numbers.next(DocumentSequence.Kind.TRANSFER));
        transfer.setItem(item);
        transfer.setFromStore(store(request.fromStoreId()));
        transfer.setToStore(store(request.toStoreId()));
        transfer.setQuantity(request.quantity());
        transfer.setReason(request.reason());
        transfer.setStatus(StockTransfer.Status.REQUESTED);
        return transferRow(transfers.save(transfer));
    }

    /** Goods leave the sending store, and are in transit until they are accepted. */
    @Transactional
    public InventoryDtos.TransferRow dispatchTransfer(UUID id) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_TRANSACT");
        StockTransfer transfer = transfer(id);
        if (transfer.getStatus() != StockTransfer.Status.REQUESTED) {
            throw AppException.duplicate("That transfer has already moved on.");
        }
        Item item = transfer.getItem();
        Stock from = stockRow(item, transfer.getFromStore());
        BigDecimal available = from.getQuantity() == null ? BigDecimal.ZERO : from.getQuantity();
        if (available.compareTo(transfer.getQuantity()) < 0) {
            throw AppException.rule(transfer.getFromStore().getName() + " does not hold "
                    + available.stripTrailingZeros().toPlainString() + " " + item.getUnit()
                    + " of " + item.getName() + ".");
        }
        record(item, from, transfer.getQuantity(), StockMovement.Type.TRANSFER_OUT,
                "Transfer " + transfer.getTransferNumber(), "TRANSFER", transfer.getId(),
                from.getAverageCost());
        transfer.setStatus(StockTransfer.Status.DISPATCHED);
        transfer.setDispatchedAt(Instant.now());
        return transferRow(transfer);
    }

    /** Goods arrive at the far store. */
    @Transactional
    public InventoryDtos.TransferRow completeTransfer(UUID id) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_TRANSACT");
        StockTransfer transfer = transfer(id);
        if (transfer.getStatus() != StockTransfer.Status.DISPATCHED) {
            throw AppException.duplicate("Only a dispatched transfer can be completed.");
        }
        Item item = transfer.getItem();
        Stock to = stockRow(item, transfer.getToStore());
        record(item, to, transfer.getQuantity(), StockMovement.Type.TRANSFER_IN,
                "Transfer " + transfer.getTransferNumber(), "TRANSFER", transfer.getId(),
                to.getAverageCost());
        transfer.setStatus(StockTransfer.Status.COMPLETED);
        transfer.setReceivedAt(Instant.now());
        return transferRow(transfer);
    }

    public InventoryDtos.TransferRow cancelTransfer(UUID id) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_TRANSACT");
        StockTransfer transfer = transfer(id);
        if (transfer.getStatus() == StockTransfer.Status.DISPATCHED) {
            throw AppException.duplicate("Goods have already left; complete the transfer instead.");
        }
        if (transfer.getStatus() == StockTransfer.Status.COMPLETED) {
            throw AppException.duplicate("That transfer is finished.");
        }
        transfer.setStatus(StockTransfer.Status.CANCELLED);
        return transferRow(transfer);
    }

    public PageResponse<InventoryDtos.TransferRow> listTransfers(StockTransfer.Status status,
                                                                 Pageable pageable) {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_READ");
        Page<StockTransfer> page = status == null
                ? transfers.findAllByOrderByRequestedAtDesc(pageable)
                : transfers.findByStatusOrderByRequestedAtDesc(status, pageable);
        return PageResponse.from(page, this::transferRow);
    }

    // --------------------------------------------------------------- reporting

    public List<InventoryDtos.LowStockRow> lowStock() {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_READ");
        return items.findNeedingReorder().stream().map(item -> {
            BigDecimal onHand = items.totalOnHand(item.getId());
            return new InventoryDtos.LowStockRow(item.getId(), item.getCode(), item.getName(),
                    item.getUnit(), onHand, item.getReorderLevel(), item.getReorderQuantity());
        }).toList();
    }

    public InventoryDtos.InventoryOverview overview() {
        institutions.requireModuleEnabled(ModuleKey.INVENTORY);
        auth.requirePermission("INVENTORY_READ");
        BigDecimal value = stock.findAll().stream()
                .map(row -> {
                    BigDecimal quantity = row.getQuantity() == null ? BigDecimal.ZERO : row.getQuantity();
                    return row.getAverageCost() == null ? BigDecimal.ZERO
                            : row.getAverageCost().multiply(quantity);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new InventoryDtos.InventoryOverview(
                items.count(),
                categories.count(),
                stores.count(),
                purchases.count(),
                issues.countByStatus(StockIssue.Status.ISSUED),
                transfers.countByStatus(StockTransfer.Status.DISPATCHED),
                items.findNeedingReorder().size(),
                value);
    }

    // ------------------------------------------------------------------ engine

    /**
     * The one place quantity changes.
     *
     * <p>Writes the movement and moves the balance together, in the caller's transaction, and
     * records the resulting figure on the movement so the ledger can be read on its own.
     */
    private void record(Item item, Stock row, BigDecimal quantity, StockMovement.Type type,
                        String reason, String referenceType, UUID referenceId, BigDecimal unitCost) {
        StockMovement movement = new StockMovement();
        movement.setItem(item);
        movement.setStore(row.getStore());
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setUnitCost(unitCost);
        movement.setReason(reason);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setMovedAt(Instant.now());
        movement.setMovedBy(auth.requireUser().userId());

        BigDecimal previous = row.getQuantity() == null ? BigDecimal.ZERO : row.getQuantity();
        BigDecimal balance = previous.add(movement.signedQuantity());
        if (balance.signum() < 0 && type != StockMovement.Type.ADJUSTMENT) {
            throw AppException.rule("There is not enough " + item.getName() + " in "
                    + row.getStore().getName() + " to do that.");
        }
        row.setQuantity(balance);
        row.setLastMovementAt(movement.getMovedAt());
        movement.setBalanceAfter(balance);
        stock.save(row);
        movements.save(movement);
    }

    /**
     * The stock row for an item in a store, created empty on first use, held under a row lock.
     *
     * <p>Everything this is used for ends in a write of {@code quantity}, so the availability
     * check that precedes it must be answered against the same row the write will land on. The
     * lock is what makes "is there enough?" and "take it" one decision rather than two.
     *
     * <p>The first movement of an item in a store has no row to lock yet, so it is created
     * first with an idempotent insert and then read back under the same lock; the unique
     * constraint on (item, store) means only one row is ever made.
     */
    private Stock stockRow(Item item, Store store) {
        Optional<Stock> existing = stock.lockedByItemIdAndStoreId(item.getId(), store.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        stock.createIfAbsent(item.getId(), store.getId());
        return stock.lockedByItemIdAndStoreId(item.getId(), store.getId())
                .orElseThrow(() -> AppException.rule("There is no shelf for " + item.getName()
                        + " in " + store.getName() + "."));
    }

    // ------------------------------------------------------------------- rows

    private InventoryDtos.CategoryRow categoryRow(ItemCategory category) {
        return new InventoryDtos.CategoryRow(category.getId(), category.getCode(),
                category.getName(), category.getParent() == null ? null : category.getParent().getId(),
                category.getParent() == null ? null : category.getParent().getName(),
                category.getDescription());
    }

    private InventoryDtos.StoreRow storeRow(Store store) {
        return new InventoryDtos.StoreRow(store.getId(), store.getCode(), store.getName(),
                store.getCampus() == null ? null : store.getCampus().getId(),
                store.getCampus() == null ? null : store.getCampus().getName(),
                store.getKeeperUserId(), store.getKeeperUserId() == null ? null
                : users.findById(store.getKeeperUserId())
                .map(com.educationerp.auth.user.User::getDisplayName).orElse(null),
                store.getAddress(), store.getPhone(), store.isActive());
    }

    private InventoryDtos.ItemRow itemRow(Item item, BigDecimal totalOnHand) {
        BigDecimal onHand = totalOnHand == null ? BigDecimal.ZERO : totalOnHand;
        return new InventoryDtos.ItemRow(item.getId(), item.getCode(), item.getName(),
                item.getDescription(), item.getCategory() == null ? null : item.getCategory().getId(),
                item.getCategory() == null ? null : item.getCategory().getName(), item.getUnit(),
                item.getReorderLevel(), item.getReorderQuantity(), item.isBatchTracked(),
                item.isExpiryTracked(), item.isActive(), onHand, item.needsReordering(onHand));
    }

    private InventoryDtos.StockRow stockRow(Stock row) {
        BigDecimal quantity = row.getQuantity() == null ? BigDecimal.ZERO : row.getQuantity();
        BigDecimal value = row.getAverageCost() == null ? BigDecimal.ZERO
                : row.getAverageCost().multiply(quantity);
        return new InventoryDtos.StockRow(row.getItem().getId(), row.getItem().getCode(),
                row.getItem().getName(), row.getItem().getUnit(), row.getStore().getId(),
                row.getStore().getName(), quantity, row.getAverageCost(), value,
                row.getLastMovementAt());
    }

    private InventoryDtos.MovementRow movementRow(StockMovement movement) {
        return new InventoryDtos.MovementRow(movement.getId(),
                movement.getItem().getId(), movement.getItem().getCode(),
                movement.getItem().getName(), movement.getStore().getId(),
                movement.getStore().getName(), movement.getType(), movement.getQuantity(),
                movement.signedQuantity(), movement.getBalanceAfter(), movement.getUnitCost(),
                movement.getReferenceType(), movement.getReferenceId(), movement.getReason(),
                movement.getMovedAt(), movement.getMovedBy());
    }

    private InventoryDtos.PurchaseRow purchaseRow(Purchase purchase) {
        List<InventoryDtos.PurchaseLineRow> lines = purchaseItems.findByPurchaseId(purchase.getId())
                .stream()
                .map(line -> new InventoryDtos.PurchaseLineRow(line.getId(), line.getItem().getId(),
                        line.getItem().getCode(), line.getItem().getName(), line.getItem().getUnit(),
                        line.getQuantityOrdered(), line.getQuantityReceived(), line.outstanding(),
                        line.getUnitCost(), line.getLineTotal(), line.isFullyReceived()))
                .toList();
        return new InventoryDtos.PurchaseRow(purchase.getId(), purchase.getPurchaseNumber(),
                purchase.getSupplierName(), purchase.getSupplierContact(),
                purchase.getStore().getId(), purchase.getStore().getName(), purchase.getStatus(),
                purchase.getOrderedOn(), purchase.getExpectedOn(), purchase.getReceivedOn(),
                purchase.getSubtotal(), purchase.getTaxAmount(), purchase.getOtherCosts(),
                purchase.getTotalAmount(), purchase.getNotes(), lines);
    }

    private InventoryDtos.IssueRow issueRow(StockIssue issue) {
        return new InventoryDtos.IssueRow(issue.getId(), issue.getIssueNumber(),
                issue.getItem().getId(), issue.getItem().getCode(), issue.getItem().getName(),
                issue.getItem().getUnit(), issue.getStore().getId(), issue.getStore().getName(),
                issue.getQuantity(), issue.getIssuedToType(), issue.getIssuedToId(),
                issue.getIssuedToName(), issue.getReason(), issue.getStatus(), issue.getIssuedAt(),
                issue.getReturnedAt());
    }

    private InventoryDtos.TransferRow transferRow(StockTransfer transfer) {
        return new InventoryDtos.TransferRow(transfer.getId(), transfer.getTransferNumber(),
                transfer.getItem().getId(), transfer.getItem().getCode(),
                transfer.getItem().getName(), transfer.getFromStore().getId(),
                transfer.getFromStore().getName(), transfer.getToStore().getId(),
                transfer.getToStore().getName(), transfer.getQuantity(), transfer.getStatus(),
                transfer.getReason(), transfer.getRequestedAt(), transfer.getDispatchedAt(),
                transfer.getReceivedAt());
    }

    // ---------------------------------------------------------------- lookups

    private ItemCategory category(UUID id) {
        return categories.findById(id)
                .orElseThrow(() -> AppException.notFound("Item category"));
    }

    private Store store(UUID id) {
        return stores.findById(id).orElseThrow(() -> AppException.notFound("Store"));
    }

    private Item item(UUID id) {
        return items.findById(id).orElseThrow(() -> AppException.notFound("Item"));
    }

    private Purchase requirePurchase(UUID id) {
        return purchases.findById(id).orElseThrow(() -> AppException.notFound("Purchase"));
    }

    private PurchaseItem purchaseItem(UUID id, Purchase purchase) {
        PurchaseItem line = purchaseItems.findById(id)
                .orElseThrow(() -> AppException.notFound("Purchase line"));
        if (!line.getPurchase().getId().equals(purchase.getId())) {
            throw AppException.rule("That line belongs to a different purchase.");
        }
        return line;
    }

    private StockIssue issue(UUID id) {
        return issues.findById(id).orElseThrow(() -> AppException.notFound("Issue note"));
    }

    private StockTransfer transfer(UUID id) {
        return transfers.findById(id)
                .orElseThrow(() -> AppException.notFound("Transfer"));
    }
}
