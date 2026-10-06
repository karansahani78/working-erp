package com.educationerp.inventory.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.api.PageResponse;
import com.educationerp.inventory.InventoryDtos;
import com.educationerp.inventory.InventoryService;
import com.educationerp.inventory.Purchase;
import com.educationerp.inventory.StockIssue;
import com.educationerp.inventory.StockTransfer;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    // ---------------------------------------------------------------- catalogue

    @GetMapping("/categories")
    public ApiResponse<List<InventoryDtos.CategoryRow>> categories() {
        return ApiResponse.ok(inventory.listCategories());
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryDtos.CategoryRow> createCategory(
            @Valid @RequestBody InventoryDtos.CategoryRequest request) {
        return ApiResponse.ok(inventory.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<InventoryDtos.CategoryRow> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody InventoryDtos.CategoryRequest request) {
        return ApiResponse.ok(inventory.updateCategory(id, request));
    }

    @GetMapping("/stores")
    public ApiResponse<PageResponse<InventoryDtos.StoreRow>> stores(
            @PageableDefault(size = 50, sort = "name") Pageable pageable) {
        return ApiResponse.ok(inventory.listStores(pageable));
    }

    @PostMapping("/stores")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryDtos.StoreRow> createStore(
            @Valid @RequestBody InventoryDtos.StoreRequest request) {
        return ApiResponse.ok(inventory.createStore(request));
    }

    @PutMapping("/stores/{id}")
    public ApiResponse<InventoryDtos.StoreRow> updateStore(
            @PathVariable UUID id,
            @Valid @RequestBody InventoryDtos.StoreRequest request) {
        return ApiResponse.ok(inventory.updateStore(id, request));
    }

    @GetMapping("/items")
    public ApiResponse<PageResponse<InventoryDtos.ItemRow>> items(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String term,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(inventory.searchItems(categoryId, active, term, pageable));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryDtos.ItemRow> createItem(
            @Valid @RequestBody InventoryDtos.ItemRequest request) {
        return ApiResponse.ok(inventory.createItem(request));
    }

    @PutMapping("/items/{id}")
    public ApiResponse<InventoryDtos.ItemRow> updateItem(
            @PathVariable UUID id,
            @Valid @RequestBody InventoryDtos.ItemRequest request) {
        return ApiResponse.ok(inventory.updateItem(id, request));
    }

    // ------------------------------------------------------------------- stock

    @GetMapping("/stores/{storeId}/stock")
    public ApiResponse<List<InventoryDtos.StockRow>> stock(@PathVariable UUID storeId) {
        return ApiResponse.ok(inventory.stockForStore(storeId));
    }

    @GetMapping("/stores/{storeId}/movements")
    public ApiResponse<PageResponse<InventoryDtos.MovementRow>> storeMovements(
            @PathVariable UUID storeId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ApiResponse.ok(inventory.ledgerForStore(storeId, pageable));
    }

    @GetMapping("/items/{itemId}/movements")
    public ApiResponse<PageResponse<InventoryDtos.MovementRow>> itemMovements(
            @PathVariable UUID itemId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ApiResponse.ok(inventory.ledgerForItem(itemId, pageable));
    }

    /** Set stock to a counted figure; the ledger records the difference, not the count. */
    @PostMapping("/adjustments")
    public ApiResponse<InventoryDtos.AdjustmentResult> adjust(
            @Valid @RequestBody InventoryDtos.AdjustmentRequest request) {
        return ApiResponse.ok(inventory.adjust(request));
    }

    // --------------------------------------------------------------- purchases

    @GetMapping("/purchases")
    public ApiResponse<PageResponse<InventoryDtos.PurchaseRow>> purchases(
            @RequestParam(required = false) Purchase.Status status,
            @PageableDefault(size = 20, sort = "orderedOn", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(inventory.listPurchases(status, pageable));
    }

    @GetMapping("/purchases/{id}")
    public ApiResponse<InventoryDtos.PurchaseRow> purchase(@PathVariable UUID id) {
        return ApiResponse.ok(inventory.purchase(id));
    }

    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryDtos.PurchaseRow> createPurchase(
            @Valid @RequestBody InventoryDtos.PurchaseRequest request) {
        return ApiResponse.ok(inventory.createPurchase(request));
    }

    @PatchMapping("/purchases/{id}/place-order")
    public ApiResponse<InventoryDtos.PurchaseRow> placeOrder(@PathVariable UUID id) {
        return ApiResponse.ok(inventory.placeOrder(id));
    }

    @PostMapping("/purchases/{id}/receive")
    public ApiResponse<InventoryDtos.PurchaseRow> receive(
            @PathVariable UUID id,
            @Valid @RequestBody InventoryDtos.ReceiveRequest request) {
        return ApiResponse.ok(inventory.receive(id, request));
    }

    @PatchMapping("/purchases/{id}/cancel")
    public ApiResponse<InventoryDtos.PurchaseRow> cancelPurchase(@PathVariable UUID id) {
        return ApiResponse.ok(inventory.cancelPurchase(id));
    }

    // ------------------------------------------------------------------ issues

    @GetMapping("/issues")
    public ApiResponse<PageResponse<InventoryDtos.IssueRow>> issues(
            @RequestParam(required = false) StockIssue.Status status,
            @PageableDefault(size = 20, sort = "issuedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(inventory.listIssues(status, pageable));
    }

    @PostMapping("/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryDtos.IssueRow> issue(
            @Valid @RequestBody InventoryDtos.IssueRequest request) {
        return ApiResponse.ok(inventory.issue(request));
    }

    @PatchMapping("/issues/{issueId}/return")
    public ApiResponse<InventoryDtos.IssueRow> giveBack(
            @PathVariable UUID issueId,
            @Valid @RequestBody InventoryDtos.ReturnRequest request) {
        return ApiResponse.ok(inventory.giveBack(new InventoryDtos.ReturnRequest(issueId,
                request.quantityReturned(), request.reason())));
    }

    // --------------------------------------------------------------- transfers

    @GetMapping("/transfers")
    public ApiResponse<PageResponse<InventoryDtos.TransferRow>> transfers(
            @RequestParam(required = false) StockTransfer.Status status,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(inventory.listTransfers(status, pageable));
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryDtos.TransferRow> requestTransfer(
            @Valid @RequestBody InventoryDtos.TransferRequest request) {
        return ApiResponse.ok(inventory.requestTransfer(request));
    }

    @PatchMapping("/transfers/{id}/dispatch")
    public ApiResponse<InventoryDtos.TransferRow> dispatchTransfer(@PathVariable UUID id) {
        return ApiResponse.ok(inventory.dispatchTransfer(id));
    }

    @PatchMapping("/transfers/{id}/complete")
    public ApiResponse<InventoryDtos.TransferRow> completeTransfer(@PathVariable UUID id) {
        return ApiResponse.ok(inventory.completeTransfer(id));
    }

    @PatchMapping("/transfers/{id}/cancel")
    public ApiResponse<InventoryDtos.TransferRow> cancelTransfer(@PathVariable UUID id) {
        return ApiResponse.ok(inventory.cancelTransfer(id));
    }

    // --------------------------------------------------------------- reporting

    @GetMapping("/low-stock")
    public ApiResponse<List<InventoryDtos.LowStockRow>> lowStock() {
        return ApiResponse.ok(inventory.lowStock());
    }

    @GetMapping("/overview")
    public ApiResponse<InventoryDtos.InventoryOverview> overview() {
        return ApiResponse.ok(inventory.overview());
    }
}
