package com.educationerp.auth;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.auth.security.RateLimitService;
import com.educationerp.auth.user.AuthenticatedUser;
import com.educationerp.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Authentication endpoints. Login, refresh and recovery are public; everything else
 * requires a valid access token.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, session management and password recovery")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthorizationChecker authorizationChecker;
    private final RateLimitService rateLimitService;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Sign in with a login ID or email")
    public ApiResponse<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                                     HttpServletRequest http) {
        rateLimitService.checkLogin(http);
        return ApiResponse.ok(authService.login(request, clientIp(http), http.getHeader("User-Agent")));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Exchange a refresh token for a new token pair",
            description = "The presented refresh token is rotated and cannot be reused.")
    public ApiResponse<AuthDtos.TokenResponse> refresh(@Valid @RequestBody AuthDtos.RefreshRequest request,
                                                       HttpServletRequest http) {
        return ApiResponse.ok(authService.refresh(request, clientIp(http), http.getHeader("User-Agent")));
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out",
            description = "Revokes the supplied refresh token, or every session when allDevices is true.")
    public ApiResponse<Void> logout(@RequestBody(required = false) AuthDtos.LogoutRequest request,
                                    @RequestParam(defaultValue = "false") boolean allDevices) {
        AuthenticatedUser user = authorizationChecker.requireUser();
        authService.logout(user.userId(), request == null ? null : request.refreshToken(), allDevices);
        return ApiResponse.ok();
    }

    @GetMapping("/sessions")
    @Operation(summary = "List active sessions for the signed-in user")
    public ApiResponse<List<AuthDtos.SessionResponse>> sessions(
            @RequestParam(required = false) String refreshToken) {
        AuthenticatedUser user = authorizationChecker.requireUser();
        return ApiResponse.ok(authService.sessions(user.userId(), refreshToken));
    }

    @PostMapping("/sessions/{id}/revoke")
    @Operation(summary = "Revoke one of the signed-in user's sessions")
    public ApiResponse<Void> revoke(@jakarta.validation.constraints.NotNull UUID id) {
        AuthenticatedUser user = authorizationChecker.requireUser();
        authService.revokeSession(user.userId(), id);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    @Operation(summary = "Profile and permissions of the signed-in user")
    public ApiResponse<AuthDtos.UserProfile> me() {
        return ApiResponse.ok(authService.currentProfile());
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the signed-in user's password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        AuthenticatedUser user = authorizationChecker.requireUser();
        authService.changePassword(user.userId(), request);
        return ResponseEntity.ok(ApiResponse.<Void>ok(null, "Password changed. Please sign in again."));
    }

    @PostMapping("/forgot-password")
    @SecurityRequirements
    @Operation(summary = "Request a password reset token",
            description = "Always returns the same message so the endpoint cannot be used to discover accounts.")
    public ApiResponse<AuthDtos.MessageResponse> forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request,
                                                                HttpServletRequest http) {
        rateLimitService.checkPasswordReset(http);
        authService.forgotPassword(request, clientIp(http));
        return ApiResponse.ok(new AuthDtos.MessageResponse(
                "If that account exists, a password reset link has been sent to the registered email address."),
                "Request accepted.");
    }

    @PostMapping("/reset-password")
    @SecurityRequirements
    @Operation(summary = "Set a new password using a reset token")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.message("Password updated. You can now sign in.");
    }

    private String clientIp(HttpServletRequest request) {
        return RateLimitService.clientKey(request);
    }
}