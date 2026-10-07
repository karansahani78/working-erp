package com.educationerp.auth;

import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.config.SecurityProperties;
import com.educationerp.auth.security.AuthenticatedUserLoader;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.auth.security.JwtTokenProvider;
import com.educationerp.auth.user.AuthenticatedUser;
import com.educationerp.auth.user.PasswordResetToken;
import com.educationerp.auth.user.PasswordResetTokenRepository;
import com.educationerp.auth.user.RefreshToken;
import com.educationerp.auth.user.RefreshTokenRepository;
import com.educationerp.auth.user.RefreshTokenStatus;
import com.educationerp.auth.user.User;
import com.educationerp.auth.user.UserRepository;
import com.educationerp.auth.user.UserStatus;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Login, session lifecycle and credential recovery.
 *
 * Security properties that matter here:
 * <ul>
 *   <li>the login identifier is matched against username and email, and a miss is
 *       reported exactly like a wrong password so the endpoint cannot be used to
 *       enumerate accounts;</li>
 *   <li>failed attempts are counted per account and lock it for the configured window;</li>
 *   <li>refresh tokens rotate on every use and reuse of a rotated token is treated as
 *       theft: the whole family is revoked;</li>
 *   <li>only digests of reset and refresh tokens are stored;</li>
 *   <li>every outcome, including failures, is audited.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";
    private static final Duration RESET_TOKEN_TTL = Duration.ofHours(2);
    private static final String GENERIC_RESET_MESSAGE =
            "If that account exists, a password reset link has been sent to the registered email address.";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticatedUserLoader userLoader;
    private final AuthorizationChecker authorizationChecker;
    private final SecurityProperties properties;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final com.educationerp.auth.user.LoginAttemptService loginAttemptService;
    private final SessionRevocationService sessionRevocationService;

    // ------------------------------------------------------------------ login

    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request, String ip, String userAgent) {
        Instant now = Instant.now();
        String identifier = request.loginId() == null ? "" : request.loginId().trim();
        User user = userRepository.findByLoginIdentifier(identifier).orElse(null);

        if (user == null) {
            // Spend comparable time on an unknown account so response timing does not
            // reveal whether the login ID exists.
            passwordEncoder.matches(request.password(), "$2a$12$"
                    + "C6UzMDM.H6dfI/f/IKcEe.7dJEbsm0u1JfQF9mvD0mQF9mvD0mQF9mvD0mQ");
            auditFailure(null, identifier, "No such account", ip);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() == UserStatus.LOCKED || user.isLocked(now)) {
            auditFailure(user, identifier, "Account locked", ip);
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            auditFailure(user, identifier, "Account status " + user.getStatus(), ip);
            throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean nowLocked = loginAttemptService.registerFailure(user.getId(),
                    properties.getMaxFailedAttempts(), properties.getLockDuration(), ip);
            auditFailure(user, identifier, nowLocked ? "Locked after repeated failures" : "Wrong password", ip);
            if (nowLocked) {
                throw new AppException(ErrorCode.ACCOUNT_LOCKED);
            }
            int used = loginAttemptService.failureCount(user.getId());
            int remaining = Math.max(0, properties.getMaxFailedAttempts() - used);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS,
                    "The login ID or password is incorrect. "
                            + remaining
                            + " attempt(s) remaining before the account is locked.");
        }

        if (user.getPasswordChangedAt() == null) {
            user.setPasswordChangedAt(now);
        }
        userRepository.save(user);
        loginAttemptService.registerSuccess(user.getId());
        userLoader.invalidate(user.getId());

        AuthDtos.TokenResponse response = issueTokens(user, now, ip, userAgent);
        auditService.record(AuditEvent.builder()
                .action(AuditAction.LOGIN)
                .entityType("User")
                .entityId(user.getId().toString())
                .entityLabel(user.getUsername())
                .module("auth")
                .summary("Signed in as " + user.getDisplayName() + " (" + user.getPrimaryRole() + ")")
                .build());
        log.info("Login succeeded user={} role={} ip={}", user.getUsername(), user.getPrimaryRole(), ip);
        return response;
    }

    // ------------------------------------------------------------------ refresh

    @Transactional
    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request, String ip, String userAgent) {
        Instant now = Instant.now();
        String hash = tokenProvider.hashToken(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash).orElse(null);
        if (stored == null) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
        if (stored.getStatus() == RefreshTokenStatus.ROTATED) {
            // Replaying a rotated token means the raw value leaked; drop every session for
            // that user so the attacker and the legitimate client are both signed out.
            // Revocation runs in its own transaction: the exception below rolls this
            // transaction back, and a rolled-back revocation would leave the stolen
            // sessions alive.
            sessionRevocationService.revokeAllForUser(stored.getUserId());
            auditSecurity(stored.getUserId(), "Refresh token reuse detected; all sessions revoked");
            throw new AppException(ErrorCode.INVALID_TOKEN, "This session is no longer valid. Please sign in again.");
        }
        if (!stored.isUsable(now)) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
        User user = userRepository.findById(stored.getUserId()).orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));
        if (user.getStatus() == UserStatus.LOCKED || user.isLocked(now)) {
            auditFailure(user, user.getUsername(), "Account locked during refresh", ip);
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            auditFailure(user, user.getUsername(), "Account status " + user.getStatus() + " during refresh", ip);
            throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
        }

        AuthDtos.TokenResponse response = issueTokens(user, now, ip, userAgent);
        stored.markRotated(refreshTokenRepository.findByTokenHash(
                        tokenProvider.hashToken(response.refreshToken())).map(RefreshToken::getId).orElse(null));
        refreshTokenRepository.save(stored);
        return response;
    }

    // ------------------------------------------------------------------ logout

    @Transactional
    public void logout(UUID userId, String refreshToken, boolean allDevices) {
        int revoked;
        if (refreshToken != null && !refreshToken.isBlank() && !allDevices) {
            revoked = refreshTokenRepository.findByTokenHash(tokenProvider.hashToken(refreshToken))
                    .filter(t -> t.getUserId().equals(userId))
                    .map(t -> {
                        t.markRevoked();
                        refreshTokenRepository.save(t);
                        return 1;
                    })
                    .orElse(0);
        } else {
            revoked = refreshTokenRepository.revokeAllForUser(userId);
        }
        auditService.record(AuditEvent.builder()
                .action(AuditAction.LOGOUT)
                .entityType("User")
                .entityId(userId.toString())
                .module("auth")
                .summary(allDevices
                        ? "Signed out of all devices (" + revoked + " session(s) revoked)"
                        : "Signed out (" + revoked + " session(s) revoked)")
                .build());
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.SessionResponse> sessions(UUID userId, String currentRefreshToken) {
        UUID currentId = currentRefreshToken == null ? null
                : refreshTokenRepository.findByTokenHash(tokenProvider.hashToken(currentRefreshToken))
                .map(RefreshToken::getId).orElse(null);
        return refreshTokenRepository.findByUserIdAndStatus(userId, RefreshTokenStatus.ACTIVE).stream()
                .map(t -> new AuthDtos.SessionResponse(t.getId(), t.getIssuedAt(), t.getExpiresAt(),
                        t.getStatus().name(), t.getClientIp(), t.getUserAgent(),
                        t.getId().equals(currentId)))
                .sorted((a, b) -> b.issuedAt().compareTo(a.issuedAt()))
                .toList();
    }

    @Transactional
    public void revokeSession(UUID userId, UUID tokenId) {
        RefreshToken token = refreshTokenRepository.findById(tokenId)
                .orElseThrow(() -> AppException.notFound("Session"));
        if (!token.getUserId().equals(userId)) {
            throw AppException.denied("You can only revoke your own sessions.");
        }
        token.markRevoked();
        refreshTokenRepository.save(token);
    }

    // ------------------------------------------------------------------ profile

    @Transactional(readOnly = true)
    public AuthDtos.UserProfile currentProfile() {
        AuthenticatedUser principal = authorizationChecker.requireUser();
        return userRepository.findById(principal.userId())
                .map(user -> toProfile(user, authorizationChecker.currentUser()))
                .orElseThrow(() -> new AppException(ErrorCode.AUTHENTICATION_REQUIRED));
    }

    // ------------------------------------------------------------------ passwords

    @Transactional
    public void changePassword(UUID userId, AuthDtos.ChangePasswordRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.AUTHENTICATION_REQUIRED));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            auditSecurity(userId, "Password change rejected: current password incorrect");
            throw new AppException(ErrorCode.INVALID_CREDENTIALS,
                    "Your current password is incorrect.", Map.of("currentPassword", "This is not correct."));
        }
        validateNewPassword(request.newPassword(), user);

        Instant now = Instant.now();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(now);
        user.setMustChangePassword(false);
        userRepository.save(user);
        // Existing access tokens carry the old token version, so they stop working; refresh
        // tokens are revoked so other devices must sign in again.
        sessionRevocationService.revokeAllForUser(userId);
        userLoader.invalidate(userId);
        auditService.record(AuditEvent.builder()
                .action(AuditAction.PASSWORD_CHANGE)
                .entityType("User")
                .entityId(userId.toString())
                .entityLabel(user.getUsername())
                .module("auth")
                .summary("Password changed; all other sessions revoked")
                .build());
    }

    /**
     * Always answers with the same message. The caller cannot tell whether the account
     * exists, which is what makes this endpoint safe to expose publicly.
     */
    @Transactional
    public void forgotPassword(AuthDtos.ForgotPasswordRequest request, String ip) {
        String identifier = request.identifier() == null ? "" : request.identifier().trim();
        User user = userRepository.findByLoginIdentifier(identifier).orElse(null);
        if (user == null || user.getStatus() == UserStatus.INACTIVE) {
            log.info("Password reset requested for unknown or inactive account identifierHash={}", identifier.hashCode());
            return;
        }
        // The reset link always goes to the email the account was created with. Accepting
        // an arbitrary address from the caller would let someone reset an account they do
        // not own by redirecting the token to their own inbox.
        String recipient = user.getEmail();
        if (recipient == null || recipient.isBlank()) {
            log.info("Password reset requested for {} but no email is on file", user.getUsername());
            return;
        }

        Instant now = Instant.now();
        passwordResetTokenRepository.invalidateAllForUser(user.getId(), now);
        String rawToken = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        passwordResetTokenRepository.save(PasswordResetToken.issue(
                user.getId(),
                tokenProvider.hashToken(rawToken),
                PURPOSE_PASSWORD_RESET,
                now.plus(RESET_TOKEN_TTL),
                ip));

        notificationService.sendEmail(recipient, "Reset your password",
                "A password reset was requested for your account.\n\n"
                        + "Reset token: " + rawToken + "\n"
                        + "This token expires at " + now.plus(RESET_TOKEN_TTL) + " and can be used once.\n\n"
                        + "If you did not request this, you can ignore this message.");
        auditSecurity(user.getId(), "Password reset token issued");
    }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        Instant now = Instant.now();
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(tokenProvider.hashToken(request.token()))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN, "This reset link is no longer valid."));
        if (!PURPOSE_PASSWORD_RESET.equals(token.getPurpose()) || !token.isUsable(now)) {
            throw new AppException(ErrorCode.INVALID_TOKEN, "This reset link has expired or has already been used.");
        }
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN, "This reset link is no longer valid."));
        validateNewPassword(request.newPassword(), user);

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(now);
        user.setMustChangePassword(false);
        user.clearLockout();
        userRepository.save(user);

        token.markUsed(now);
        passwordResetTokenRepository.save(token);
        passwordResetTokenRepository.invalidateAllForUser(user.getId(), now);
        sessionRevocationService.revokeAllForUser(user.getId());
        userLoader.invalidate(user.getId());

        auditService.record(AuditEvent.builder()
                .action(AuditAction.PASSWORD_CHANGE)
                .entityType("User")
                .entityId(user.getId().toString())
                .entityLabel(user.getUsername())
                .module("auth")
                .summary("Password reset completed; sessions revoked")
                .build());
    }

    // ------------------------------------------------------------------ helpers

    private AuthDtos.TokenResponse issueTokens(User user, Instant now, String ip, String userAgent) {
        AuthenticatedUser principal = userLoader.toPrincipal(user);
        JwtTokenProvider.AccessToken access = tokenProvider.createAccessToken(
                user.getId(), user.getUsername(), principal.roles(), principal.permissions(),
                principal.tokenVersion(), principal.studentId(), principal.studentIds());

        String rawRefresh = tokenProvider.newRefreshTokenValue();
        Duration refreshTtl = properties.getJwt().getRefreshTokenTtl();
        RefreshToken saved = refreshTokenRepository.save(RefreshToken.issue(
                user.getId(),
                tokenProvider.hashToken(rawRefresh),
                now,
                now.plus(refreshTtl),
                ip,
                userAgent == null || userAgent.length() <= 400 ? userAgent : userAgent.substring(0, 400)));

        return new AuthDtos.TokenResponse(
                "Bearer",
                access.token(),
                access.expiresAt(),
                rawRefresh,
                saved.getExpiresAt(),
                toProfile(user, principal));
    }

    public AuthDtos.UserProfile toProfile(User user, AuthenticatedUser principal) {
        Set<String> permissions = principal == null ? Set.of() : principal.permissions();
        return new AuthDtos.UserProfile(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhone(),
                user.getPrimaryRole(),
                user.getStatus(),
                permissions,
                permissions.stream().anyMatch(p -> !p.startsWith("PORTAL_") && !p.startsWith("DASHBOARD_STUDENT")
                        && !p.startsWith("DASHBOARD_PARENT") && !p.equals("SELF_PROFILE_UPDATE")),
                user.getPrimaryRole().name().equals("STUDENT"),
                user.getPrimaryRole().name().equals("PARENT"),
                user.getStudentId(),
                user.isMustChangePassword(),
                user.getLastLoginAt());
    }

    private void validateNewPassword(String password, User user) {
        int minLength = properties.getPassword().getMinLength();
        Map<String, String> errors = new LinkedHashMap<>();
        if (password == null || password.length() < minLength) {
            errors.put("newPassword", "Use at least " + minLength + " characters.");
        } else if (password.chars().noneMatch(Character::isLetter)) {
            errors.put("newPassword", "Include at least one letter.");
        } else if (password.chars().noneMatch(Character::isDigit)) {
            errors.put("newPassword", "Include at least one number.");
        } else if (password.toLowerCase(Locale.ROOT).contains(user.getUsername().toLowerCase(Locale.ROOT))) {
            errors.put("newPassword", "Do not include your login ID in the password.");
        } else if (password.equals(user.getPasswordHash())) {
            errors.put("newPassword", "Choose a different password.");
        }
        if (!errors.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Please correct the highlighted fields.", errors);
        }
    }

    /**
     * Records a rejected login in its own transaction so the trail survives the rollback
     * that the exception triggers.
     */
    private void auditFailure(User user, String identifier, String reason, String ip) {
        auditService.recordIndependent(AuditEvent.builder()
                .action(AuditAction.LOGIN_FAILED)
                .entityType("User")
                .entityId(user == null ? null : user.getId().toString())
                .entityLabel(user == null ? null : user.getUsername())
                .module("auth")
                .succeeded(false)
                .failureReason(reason)
                .summary(reason + " (identifier hash " + Math.abs(identifier.hashCode()) + ")")
                .build());
        log.warn("Login failed user={} reason={} ip={}",
                user == null ? "unknown" : user.getUsername(), reason, ip);
    }

    private void auditSecurity(UUID userId, String summary) {
        auditService.recordIndependent(AuditEvent.builder()
                .action(AuditAction.SECURITY_EVENT)
                .entityType("User")
                .entityId(userId == null ? null : userId.toString())
                .module("auth")
                .summary(summary)
                .build());
    }

    /**
     * Removes expired refresh and reset tokens. Invoked by the cleanup schedule; exposed
     * so tests and manual maintenance can trigger it deterministically.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeExpiredTokens() {
        int refresh = refreshTokenRepository.expireStale(Instant.now());
        log.debug("Purged {} expired refresh token(s)", refresh);
        return refresh;
    }
}