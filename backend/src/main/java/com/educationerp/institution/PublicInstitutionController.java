package com.educationerp.institution;

import com.educationerp.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public, unauthenticated branding endpoint. Only non-sensitive fields are returned so the
 * login page can render before anyone signs in.
 */
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public", description = "Unauthenticated branding and availability information")
@SecurityRequirements
@RequiredArgsConstructor
public class PublicInstitutionController {

    private final InstitutionService institutionService;

    @GetMapping("/institution")
    @Operation(summary = "Public institution branding",
            description = "Safe branding payload used by the login page and portals. Returns null data when setup is incomplete.")
    public ApiResponse<InstitutionBranding> institution() {
        return ApiResponse.ok(institutionService.branding());
    }

    @GetMapping("/institution/modules")
    @Operation(summary = "Enabled modules for the current installation")
    public ApiResponse<Map<String, Boolean>> modules() {
        Institution institution = institutionService.findInstitution().orElse(null);
        if (institution == null || !institution.isSetupCompleted()) {
            return ApiResponse.ok(Map.of());
        }
        Map<String, Boolean> result = new java.util.LinkedHashMap<>();
        for (ModuleKey key : ModuleKey.values()) {
            result.put(key.name(), institutionService.isModuleEnabled(key));
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/availability")
    @Operation(summary = "Whether first-run setup is still required")
    public ApiResponse<Map<String, Boolean>> availability() {
        boolean ready = institutionService.isSetupCompleted();
        return ApiResponse.ok(Map.of("setupRequired", !ready, "ready", ready));
    }
}
