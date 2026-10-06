package com.educationerp.asset.web;

import com.educationerp.asset.Asset;
import com.educationerp.asset.AssetAssignment;
import com.educationerp.asset.AssetDtos;
import com.educationerp.asset.AssetMaintenance;
import com.educationerp.asset.AssetService;
import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.api.PageResponse;
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
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assets;

    public AssetController(AssetService assets) {
        this.assets = assets;
    }

    // ---------------------------------------------------------------- categories

    @GetMapping("/categories")
    public ApiResponse<List<AssetDtos.CategoryRow>> categories() {
        return ApiResponse.ok(assets.listCategories());
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssetDtos.CategoryRow> createCategory(
            @Valid @RequestBody AssetDtos.CategoryRequest request) {
        return ApiResponse.ok(assets.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<AssetDtos.CategoryRow> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody AssetDtos.CategoryRequest request) {
        return ApiResponse.ok(assets.updateCategory(id, request));
    }

    // -------------------------------------------------------------------- assets

    @GetMapping
    public ApiResponse<PageResponse<AssetDtos.AssetRow>> search(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Asset.Status status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String term,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiResponse.ok(assets.searchAssets(categoryId, status, departmentId, term, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssetDtos.AssetRow> create(@Valid @RequestBody AssetDtos.AssetRequest request) {
        return ApiResponse.ok(assets.createAsset(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AssetDtos.AssetRow> update(@PathVariable UUID id,
                                                  @Valid @RequestBody AssetDtos.AssetRequest request) {
        return ApiResponse.ok(assets.updateAsset(id, request));
    }

    /** The asset with its full custody history and its servicing record. */
    @GetMapping("/{id}")
    public ApiResponse<AssetDtos.AssetDetail> detail(@PathVariable UUID id) {
        return ApiResponse.ok(assets.detail(id));
    }

    // ------------------------------------------------------------------ custody

    @PostMapping("/{id}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssetDtos.AssignmentRow> assign(
            @PathVariable UUID id,
            @Valid @RequestBody AssetDtos.AssignmentRequest request) {
        return ApiResponse.ok(assets.assign(id, request));
    }

    @PatchMapping("/{id}/return")
    public ApiResponse<AssetDtos.AssignmentRow> returnAsset(
            @PathVariable UUID id,
            @Valid @RequestBody AssetDtos.ReturnRequest request) {
        return ApiResponse.ok(assets.returnAsset(id, request));
    }

    @GetMapping("/{id}/assignments")
    public ApiResponse<List<AssetDtos.AssignmentRow>> history(@PathVariable UUID id) {
        return ApiResponse.ok(assets.history(id));
    }

    /** What one person is holding right now. */
    @GetMapping("/held")
    public ApiResponse<PageResponse<AssetDtos.AssetRow>> heldBy(
            @RequestParam AssetAssignment.HolderType holderType,
            @RequestParam(required = false) UUID holderUserId,
            @RequestParam(required = false) UUID holderEmployeeId,
            @RequestParam(required = false) UUID holderStudentId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(assets.heldBy(new AssetDtos.AssignmentRequest(holderType,
                holderUserId, holderEmployeeId, holderStudentId, "", null), pageable));
    }

    // ------------------------------------------------- lost, found and disposed

    /** Report an asset missing, with a reason worth reading a year from now. */
    @PatchMapping("/{id}/lost")
    public ApiResponse<AssetDtos.AssetRow> markLost(
            @PathVariable UUID id,
            @Valid @RequestBody AssetDtos.MarkLostRequest request) {
        return ApiResponse.ok(assets.markLost(id, request));
    }

    /** An asset that turned up again: back on the shelf, keeping the story of the disappearance. */
    @PatchMapping("/{id}/found")
    public ApiResponse<AssetDtos.AssetRow> markFound(
            @PathVariable UUID id,
            @Valid @RequestBody AssetDtos.FoundRequest request) {
        return ApiResponse.ok(assets.markFound(id, request));
    }

    /** Write an asset off the register, without deleting the row that accounts for it. */
    @PatchMapping("/{id}/dispose")
    public ApiResponse<AssetDtos.AssetRow> dispose(
            @PathVariable UUID id,
            @Valid @RequestBody AssetDtos.DisposeRequest request) {
        return ApiResponse.ok(assets.dispose(id, request));
    }

    // -------------------------------------------------------------- maintenance

    @PostMapping("/{id}/maintenance")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssetDtos.MaintenanceRow> schedule(
            @PathVariable UUID id,
            @Valid @RequestBody AssetDtos.MaintenanceRequest request) {
        return ApiResponse.ok(assets.schedule(id, request));
    }

    @GetMapping("/maintenance")
    public ApiResponse<PageResponse<AssetDtos.MaintenanceRow>> jobs(
            @RequestParam(required = false) AssetMaintenance.Status status,
            @PageableDefault(size = 20, sort = "scheduledFor",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(assets.listJobs(status, pageable));
    }

    @PatchMapping("/maintenance/{jobId}/start")
    public ApiResponse<AssetDtos.MaintenanceRow> start(
            @PathVariable UUID jobId,
            @RequestBody(required = false) AssetDtos.StartMaintenanceRequest request) {
        return ApiResponse.ok(assets.start(jobId, request));
    }

    @PatchMapping("/maintenance/{jobId}/complete")
    public ApiResponse<AssetDtos.MaintenanceRow> complete(
            @PathVariable UUID jobId,
            @Valid @RequestBody AssetDtos.CompleteMaintenanceRequest request) {
        return ApiResponse.ok(assets.complete(jobId, request));
    }

    @PatchMapping("/maintenance/{jobId}/cancel")
    public ApiResponse<AssetDtos.MaintenanceRow> cancelJob(@PathVariable UUID jobId) {
        return ApiResponse.ok(assets.cancelJob(jobId));
    }

    // --------------------------------------------------------------- reporting

    @GetMapping("/depreciation")
    public ApiResponse<List<AssetDtos.DepreciationRow>> depreciation() {
        return ApiResponse.ok(assets.depreciation());
    }

    @GetMapping("/overview")
    public ApiResponse<AssetDtos.AssetOverview> overview() {
        return ApiResponse.ok(assets.overview());
    }
}
