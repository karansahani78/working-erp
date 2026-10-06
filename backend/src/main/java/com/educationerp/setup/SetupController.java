package com.educationerp.setup;

import com.educationerp.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * First-run setup. Reachable only while no institution exists or setup is incomplete, so
 * an ordinary signed-in user cannot create or replace the institution.
 */
@RestController
@RequestMapping("/api/v1/setup")
@Tag(name = "Setup", description = "First-run installation wizard")
@SecurityRequirements
@RequiredArgsConstructor
public class SetupController {

    private final SetupService setupService;

    @GetMapping("/preview")
    @Operation(summary = "Describe the pending setup")
    public ApiResponse<SetupDtos.SetupPreview> preview() {
        return ApiResponse.ok(setupService.preview());
    }

    @PostMapping
    @Operation(summary = "Complete first-run setup",
            description = "Creates the institution, branding, academic structure, enabled modules and the first administrator in one transaction.")
    public ResponseEntity<ApiResponse<SetupDtos.SetupResponse>> complete(
            @Valid @RequestBody SetupDtos.SetupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(setupService.complete(request), "Setup completed."));
    }
}
