package com.educationerp.auth.web;

import com.educationerp.auth.service.AdminService;
import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.api.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Administration of sign-ins, roles and optional modules.
 *
 * <p>Authorisation is enforced in {@link AdminService}; every handler here requires an
 * authenticated caller, and each service method additionally demands its own permission.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService service;

    // ------------------------------------------------------------------ users

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminDtos.UserResponse>> listUsers(
            @RequestParam(required = false) String term,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.searchUsers(term, role, status, pageable)));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<AdminDtos.UserResponse> getUser(@PathVariable UUID id) {
        return ApiResponse.ok(service.getUser(id));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<AdminDtos.UserResponse>> createUser(
            @Valid @RequestBody AdminDtos.CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.createUser(request), "User created"));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<AdminDtos.UserResponse> updateUser(
            @PathVariable UUID id, @Valid @RequestBody AdminDtos.UpdateUserRequest request) {
        return ApiResponse.ok(service.updateUser(id, request), "User updated");
    }

    @PostMapping("/users/{id}/password")
    public ApiResponse<Void> resetPassword(
            @PathVariable UUID id, @Valid @RequestBody AdminDtos.ResetPasswordRequest request) {
        service.resetPassword(id, request.password());
        return ApiResponse.ok(null, "Password reset");
    }

    // ------------------------------------------------------------------ roles

    @GetMapping("/roles")
    public ApiResponse<List<AdminDtos.RoleSummary>> listRoles() {
        return ApiResponse.ok(service.listRoles());
    }

    @GetMapping("/roles/{id}")
    public ApiResponse<AdminDtos.RoleDetail> getRole(@PathVariable UUID id) {
        return ApiResponse.ok(service.getRole(id));
    }

    @PutMapping("/roles/{id}/permissions")
    public ApiResponse<AdminDtos.RoleDetail> savePermissions(
            @PathVariable UUID id,
            @Valid @RequestBody AdminDtos.SaveRoleRequest request) {
        return ApiResponse.ok(service.savePermissions(id, request.permissions()), "Permissions updated");
    }

    // ------------------------------------------------------------------ modules

    @GetMapping("/modules")
    public ApiResponse<List<AdminDtos.ModuleState>> listModules() {
        return ApiResponse.ok(service.listModules());
    }

    @PutMapping("/modules")
    public ApiResponse<AdminDtos.ModuleState> setModule(
            @Valid @RequestBody AdminDtos.SetModuleRequest request) {
        return ApiResponse.ok(service.setModule(request.key(), Boolean.TRUE.equals(request.enabled())),
                request.enabled() ? "Module enabled" : "Module disabled");
    }
}